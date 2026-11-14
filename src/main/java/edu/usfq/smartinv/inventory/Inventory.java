package edu.usfq.smartinv.inventory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Receptor del patrón Command: aquí vive la lógica real del stock.
 *
 * <p>No confía en quien lo llama. Aunque los comandos llegan ya validados por la cadena,
 * cada operación vuelve a comprobar sus reglas y lanza {@link InventoryRuleViolation}
 * antes de tocar nada, de modo que una llamada directa tampoco puede dejar cantidades
 * negativas, mezclar lotes o exceder una bodega. Las operaciones son todo o nada:
 * primero se comprueba y después se aplica.
 */
public final class Inventory implements CatalogOperations, StockOperations, ReservationOperations, InventoryView {

    private record PositionKey(String lotId, String warehouse) {
    }

    private final Clock clock;
    private final Catalog catalog;
    private final Map<String, Warehouse> warehouses = new LinkedHashMap<>();
    private final Map<String, Lot> lots = new LinkedHashMap<>();
    private final Map<PositionKey, StockPosition> positions = new LinkedHashMap<>();
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private int lotCounter;
    private int reservationCounter;

    public Inventory(Clock clock, ProductClassifier classifier, List<Warehouse> warehouses) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.catalog = new Catalog(Objects.requireNonNull(classifier, "classifier"));
        for (Warehouse warehouse : Objects.requireNonNull(warehouses, "warehouses")) {
            if (this.warehouses.putIfAbsent(warehouse.code(), warehouse) != null) {
                throw new IllegalArgumentException("bodega repetida: " + warehouse.code());
            }
        }
        if (this.warehouses.isEmpty()) {
            throw new IllegalArgumentException("el inventario necesita al menos una bodega");
        }
    }

    // ---------------------------------------------------------------- catálogo

    @Override
    public Product registerProduct(String name) {
        return catalog.register(name);
    }

    @Override
    public void retireProduct(String sku) {
        Product product = requireProduct(sku);
        boolean hasLots = lots.values().stream().anyMatch(lot -> lot.sku().equals(sku));
        if (hasLots) {
            throw new InventoryRuleViolation("no se puede retirar " + product.sku() + " porque ya tiene lotes");
        }
        catalog.retire(sku);
    }

    // ------------------------------------------------------------ stock físico

    @Override
    public Lot receiveLot(String sku, String warehouse, int quantity, LocalDate expiry) {
        Product product = requireProduct(sku);
        Warehouse target = requireWarehouse(warehouse);
        requirePositive(quantity);
        if (product.category().expiryRequired() && expiry == null) {
            throw new InventoryRuleViolation("los productos de " + product.category().label()
                    + " necesitan fecha de vencimiento");
        }
        if (expiry != null && expiry.isBefore(today())) {
            throw new InventoryRuleViolation("no se recibe un lote que ya venció el " + expiry);
        }
        requireStorageRoom(target, quantity);

        Lot lot = new Lot("L-%04d".formatted(++lotCounter), sku, expiry, warehouse, quantity);
        lots.put(lot.id(), lot);
        positionOrCreate(lot.id(), warehouse).add(quantity);
        return lot;
    }

    @Override
    public void cancelReceipt(String lotId) {
        Lot lot = requireLot(lotId);
        List<StockPosition> own = positions.values().stream().filter(p -> p.lotId().equals(lotId)).toList();
        boolean untouched = own.size() == 1
                && own.get(0).warehouse().equals(lot.receivedAt())
                && own.get(0).onHand() == lot.receivedQuantity()
                && own.get(0).reserved() == 0
                && !lot.isQuarantined();
        if (!untouched) {
            throw new InventoryRuleViolation("el lote " + lotId
                    + " ya tuvo movimientos; su recepción no se puede anular");
        }
        positions.remove(new PositionKey(lotId, lot.receivedAt()));
        lots.remove(lotId);
    }

    @Override
    public void adjust(String lotId, String warehouse, int delta) {
        requireLot(lotId);
        Warehouse place = requireWarehouse(warehouse);
        if (delta == 0) {
            throw new InventoryRuleViolation("un ajuste de 0 unidades no cambia nada");
        }
        if (delta > 0) {
            requireStorageRoom(place, delta);
            positionOrCreate(lotId, warehouse).add(delta);
            return;
        }
        StockPosition position = position(lotId, warehouse);
        if (position == null) {
            throw new InventoryRuleViolation("el lote " + lotId + " no tiene unidades en " + warehouse);
        }
        position.remove(-delta);
        dropIfEmpty(position);
    }

    @Override
    public void transfer(String lotId, String fromWarehouse, String toWarehouse, int quantity) {
        Lot lot = requireLot(lotId);
        requireWarehouse(fromWarehouse);
        Warehouse destination = requireWarehouse(toWarehouse);
        requirePositive(quantity);
        if (fromWarehouse.equals(toWarehouse)) {
            throw new InventoryRuleViolation("el origen y el destino son la misma bodega (" + fromWarehouse + ")");
        }
        requireHealthy(lot, "transferir");
        StockPosition source = position(lotId, fromWarehouse);
        int free = source == null ? 0 : source.available();
        if (free < quantity) {
            throw new InventoryRuleViolation("el lote " + lotId + " solo tiene " + free + " u libres en "
                    + fromWarehouse + "; no se pueden transferir " + quantity);
        }
        requireStorageRoom(destination, quantity);

        source.remove(quantity);
        positionOrCreate(lotId, toWarehouse).add(quantity);
        dropIfEmpty(source);
    }

    @Override
    public void quarantine(String lotId, String reason) {
        requireLot(lotId).quarantine(reason);
    }

    @Override
    public String liftQuarantine(String lotId) {
        return requireLot(lotId).liftQuarantine();
    }

    // ---------------------------------------------------------------- reservas

    @Override
    public Reservation reserve(String ownerId, String sku, String warehouse, int quantity) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new InventoryRuleViolation("una reserva necesita un dueño");
        }
        requireProduct(sku);
        Warehouse place = requireWarehouse(warehouse);
        requirePositive(quantity);

        LocalDate today = today();
        // Vence primero, sale primero. La política intercambiable (Strategy) está en el otro proyecto.
        List<StockPosition> candidates = positions.values().stream()
                .filter(p -> p.warehouse().equals(warehouse) && p.available() > 0)
                .filter(p -> lots.get(p.lotId()).sku().equals(sku) && lots.get(p.lotId()).isSellable(today))
                .sorted(Comparator.comparing((StockPosition p) -> lots.get(p.lotId()).expiryOrFarFuture())
                        .thenComparing(StockPosition::lotId))
                .toList();
        int sellable = candidates.stream().mapToInt(StockPosition::available).sum();
        if (sellable < quantity) {
            throw new InventoryRuleViolation("solo hay " + sellable + " u vendibles de " + sku + " en "
                    + warehouse + "; no se pueden reservar " + quantity);
        }
        requireStagingRoom(place, quantity);

        List<Reservation.Line> lines = new ArrayList<>();
        int pending = quantity;
        for (StockPosition candidate : candidates) {
            if (pending == 0) {
                break;
            }
            int take = Math.min(pending, candidate.available());
            candidate.reserve(take);
            lines.add(new Reservation.Line(candidate.lotId(), take));
            pending -= take;
        }
        Reservation reservation = new Reservation("RSV-%04d".formatted(++reservationCounter),
                ownerId, sku, warehouse, lines);
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    @Override
    public Reservation release(String reservationId) {
        Reservation reservation = requireReservation(reservationId, Reservation.Status.ACTIVE, "liberar");
        for (Reservation.Line line : reservation.lines()) {
            requirePosition(line.lotId(), reservation.warehouse()).unreserve(line.quantity());
        }
        reservation.mark(Reservation.Status.RELEASED);
        return reservation;
    }

    @Override
    public Reservation reinstate(String reservationId) {
        Reservation reservation = requireReservation(reservationId, Reservation.Status.RELEASED, "reactivar");
        Warehouse place = requireWarehouse(reservation.warehouse());
        for (Reservation.Line line : reservation.lines()) {
            requireHealthy(requireLot(line.lotId()), "volver a reservar");
            StockPosition position = position(line.lotId(), reservation.warehouse());
            int free = position == null ? 0 : position.available();
            if (free < line.quantity()) {
                throw new InventoryRuleViolation("el lote " + line.lotId() + " ya solo tiene " + free
                        + " u libres; no alcanza para reactivar la reserva " + reservationId);
            }
        }
        requireStagingRoom(place, reservation.units());

        for (Reservation.Line line : reservation.lines()) {
            requirePosition(line.lotId(), reservation.warehouse()).reserve(line.quantity());
        }
        reservation.mark(Reservation.Status.ACTIVE);
        return reservation;
    }

    @Override
    public Reservation dispatch(String reservationId) {
        Reservation reservation = requireReservation(reservationId, Reservation.Status.ACTIVE, "despachar");
        for (Reservation.Line line : reservation.lines()) {
            requireHealthy(requireLot(line.lotId()), "despachar");
        }
        for (Reservation.Line line : reservation.lines()) {
            StockPosition position = requirePosition(line.lotId(), reservation.warehouse());
            position.shipReserved(line.quantity());
            dropIfEmpty(position);
        }
        reservation.mark(Reservation.Status.DISPATCHED);
        return reservation;
    }

    @Override
    public Reservation acceptReturn(String reservationId) {
        Reservation reservation = requireReservation(reservationId, Reservation.Status.DISPATCHED, "devolver");
        Warehouse place = requireWarehouse(reservation.warehouse());
        for (Reservation.Line line : reservation.lines()) {
            requireLot(line.lotId());
        }
        requireStorageRoom(place, reservation.units());
        requireStagingRoom(place, reservation.units());

        for (Reservation.Line line : reservation.lines()) {
            StockPosition position = positionOrCreate(line.lotId(), reservation.warehouse());
            position.add(line.quantity());
            position.reserve(line.quantity());
        }
        reservation.mark(Reservation.Status.ACTIVE);
        return reservation;
    }

    // --------------------------------------------------------------- consultas

    @Override
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Override
    public Optional<Product> product(String sku) {
        return catalog.find(sku);
    }

    @Override
    public List<Product> products() {
        return catalog.all();
    }

    @Override
    public Optional<Warehouse> warehouse(String code) {
        return Optional.ofNullable(code == null ? null : warehouses.get(code));
    }

    @Override
    public List<Warehouse> warehouses() {
        return new ArrayList<>(warehouses.values());
    }

    @Override
    public Optional<Lot> lot(String lotId) {
        return Optional.ofNullable(lotId == null ? null : lots.get(lotId));
    }

    @Override
    public Optional<Reservation> reservation(String reservationId) {
        return Optional.ofNullable(reservationId == null ? null : reservations.get(reservationId));
    }

    @Override
    public List<Reservation> reservations() {
        return new ArrayList<>(reservations.values());
    }

    @Override
    public int unreservedUnits(String sku, String warehouse) {
        return positions.values().stream()
                .filter(p -> p.warehouse().equals(warehouse) && lots.get(p.lotId()).sku().equals(sku))
                .mapToInt(StockPosition::available)
                .sum();
    }

    @Override
    public int sellableUnits(String sku, String warehouse) {
        LocalDate today = today();
        return positions.values().stream()
                .filter(p -> p.warehouse().equals(warehouse))
                .filter(p -> lots.get(p.lotId()).sku().equals(sku) && lots.get(p.lotId()).isSellable(today))
                .mapToInt(StockPosition::available)
                .sum();
    }

    @Override
    public int unreservedUnitsOfLot(String lotId, String warehouse) {
        StockPosition position = position(lotId, warehouse);
        return position == null ? 0 : position.available();
    }

    @Override
    public int storedUnits(String warehouse) {
        return positions.values().stream()
                .filter(p -> p.warehouse().equals(warehouse))
                .mapToInt(StockPosition::onHand)
                .sum();
    }

    @Override
    public int stagedUnits(String warehouse) {
        return positions.values().stream()
                .filter(p -> p.warehouse().equals(warehouse))
                .mapToInt(StockPosition::reserved)
                .sum();
    }

    @Override
    public List<StockLine> stockLines() {
        LocalDate today = today();
        return positions.values().stream()
                .map(p -> {
                    Lot lot = lots.get(p.lotId());
                    return new StockLine(lot.id(), lot.sku(), p.warehouse(), p.onHand(), p.reserved(),
                            lot.expiry().orElse(null), lot.isExpired(today), lot.quarantineReason().orElse(null));
                })
                .sorted(Comparator.comparing(StockLine::warehouse)
                        .thenComparing(StockLine::sku)
                        .thenComparing(StockLine::lotId))
                .toList();
    }

    // ---------------------------------------------------------------- internos

    private Product requireProduct(String sku) {
        return catalog.find(sku).orElseThrow(() ->
                new InventoryRuleViolation("el SKU " + sku + " no existe en el catálogo"));
    }

    private Warehouse requireWarehouse(String code) {
        return warehouse(code).orElseThrow(() ->
                new InventoryRuleViolation("la bodega " + code + " no existe"));
    }

    private Lot requireLot(String lotId) {
        return lot(lotId).orElseThrow(() -> new InventoryRuleViolation("el lote " + lotId + " no existe"));
    }

    private Reservation requireReservation(String id, Reservation.Status expected, String action) {
        Reservation reservation = reservation(id).orElseThrow(() ->
                new InventoryRuleViolation("la reserva " + id + " no existe"));
        if (reservation.status() != expected) {
            throw new InventoryRuleViolation("la reserva " + id + " está " + reservation.status().label()
                    + "; no se puede " + action);
        }
        return reservation;
    }

    private void requireHealthy(Lot lot, String action) {
        if (lot.isQuarantined()) {
            throw new InventoryRuleViolation("el lote " + lot.id() + " está en cuarentena ("
                    + lot.quarantineReason().orElse("") + "); no se puede " + action);
        }
        if (lot.isExpired(today())) {
            throw new InventoryRuleViolation("el lote " + lot.id() + " venció el "
                    + lot.expiry().orElseThrow() + "; no se puede " + action);
        }
    }

    private void requireStorageRoom(Warehouse warehouse, int arriving) {
        int stored = storedUnits(warehouse.code());
        if (stored + arriving > warehouse.storageCapacity()) {
            throw new InventoryRuleViolation(warehouse.code() + " almacena " + stored + " de "
                    + warehouse.storageCapacity() + " u; no caben " + arriving + " más");
        }
    }

    private void requireStagingRoom(Warehouse warehouse, int arriving) {
        int staged = stagedUnits(warehouse.code());
        if (staged + arriving > warehouse.stagingCapacity()) {
            throw new InventoryRuleViolation("la zona de despacho de " + warehouse.code() + " tiene " + staged
                    + " de " + warehouse.stagingCapacity() + " u comprometidas; no caben " + arriving + " más");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InventoryRuleViolation("la cantidad debe ser mayor que cero (se indicó " + quantity + ")");
        }
    }

    private StockPosition position(String lotId, String warehouse) {
        return positions.get(new PositionKey(lotId, warehouse));
    }

    private StockPosition requirePosition(String lotId, String warehouse) {
        StockPosition position = position(lotId, warehouse);
        if (position == null) {
            throw new InventoryRuleViolation("el lote " + lotId + " no tiene unidades en " + warehouse);
        }
        return position;
    }

    private StockPosition positionOrCreate(String lotId, String warehouse) {
        return positions.computeIfAbsent(new PositionKey(lotId, warehouse),
                key -> new StockPosition(lotId, warehouse));
    }

    /** Una posición sin unidades no se guarda: no quedan lotes fantasma con cantidad 0. */
    private void dropIfEmpty(StockPosition position) {
        if (position.isEmpty()) {
            positions.remove(new PositionKey(position.lotId(), position.warehouse()));
        }
    }
}
