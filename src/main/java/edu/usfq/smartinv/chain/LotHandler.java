package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.inventory.InventoryView;
import edu.usfq.smartinv.inventory.Lot;
import edu.usfq.smartinv.inventory.Product;
import edu.usfq.smartinv.inventory.Reservation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** ¿Los lotes involucrados existen y están sanos (vigentes y sin cuarentena)? */
public final class LotHandler extends BaseHandler {

    private final InventoryView inventory;

    public LotHandler(InventoryView inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Verdict handle(OperationRequest request) {
        LocalDate today = inventory.today();

        if (request.needs(Need.VALID_EXPIRY)) {
            Optional<Product> product = inventory.product(request.sku());
            if (product.isPresent() && product.get().category().expiryRequired() && request.expiry() == null) {
                return reject("los productos de " + product.get().category().label()
                        + " necesitan fecha de vencimiento");
            }
            if (request.expiry() != null && request.expiry().isBefore(today)) {
                return reject("el lote que se quiere recibir ya venció el " + request.expiry());
            }
        }
        if (request.needs(Need.EXISTING_LOT) && inventory.lot(request.lotId()).isEmpty()) {
            return reject("el lote " + request.lotId() + " no existe");
        }
        if (request.needs(Need.HEALTHY_LOT)) {
            for (Lot lot : lotsInvolved(request)) {
                if (lot.isQuarantined()) {
                    return reject("el lote " + lot.id() + " está en cuarentena: "
                            + lot.quarantineReason().orElse("sin motivo"));
                }
                if (lot.isExpired(today)) {
                    return reject("el lote " + lot.id() + " venció el " + lot.expiry().orElseThrow());
                }
            }
        }
        if (request.needs(Need.SELLABLE_STOCK)) {
            int sellable = inventory.sellableUnits(request.sku(), request.warehouse());
            if (sellable < request.quantity()) {
                int physical = inventory.unreservedUnits(request.sku(), request.warehouse());
                return reject("de las " + physical + " u libres de " + request.sku() + " en "
                        + request.warehouse() + " solo " + sellable
                        + " están en lotes vigentes y sin cuarentena; se pidieron " + request.quantity());
            }
        }
        return super.handle(request);
    }

    /** El lote nombrado en la solicitud o, si actúa sobre una reserva, los lotes de esa reserva. */
    private List<Lot> lotsInvolved(OperationRequest request) {
        List<Lot> result = new ArrayList<>();
        if (request.lotId() != null) {
            inventory.lot(request.lotId()).ifPresent(result::add);
        } else if (request.reservationId() != null) {
            Optional<Reservation> reservation = inventory.reservation(request.reservationId());
            if (reservation.isPresent()) {
                for (Reservation.Line line : reservation.get().lines()) {
                    inventory.lot(line.lotId()).ifPresent(result::add);
                }
            }
        }
        return result;
    }

    @Override
    protected String linkName() {
        return "Lote";
    }
}
