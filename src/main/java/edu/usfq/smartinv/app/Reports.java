package edu.usfq.smartinv.app;

import edu.usfq.smartinv.gateway.Ledger;
import edu.usfq.smartinv.gateway.LedgerEntry;
import edu.usfq.smartinv.gateway.Outcome;
import edu.usfq.smartinv.inventory.InventoryView;
import edu.usfq.smartinv.inventory.Product;
import edu.usfq.smartinv.inventory.Reservation;
import edu.usfq.smartinv.inventory.StockLine;
import edu.usfq.smartinv.inventory.Warehouse;
import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Tablas de texto que comparten la consola y la demostración. */
final class Reports {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Reports() {
    }

    static void products(InventoryView inventory, PrintStream out) {
        out.printf("  %-14s %-13s %s%n", "SKU", "FAMILIA", "NOMBRE");
        for (Product product : inventory.products()) {
            out.printf("  %-14s %-13s %s%n", product.sku(), product.category().label(), product.name());
        }
    }

    static void stock(InventoryView inventory, PrintStream out) {
        out.println("  Fecha del sistema: " + inventory.today());
        out.printf("  %-10s %-14s %-7s %7s %10s %7s  %-11s %s%n",
                "BODEGA", "SKU", "LOTE", "FISICO", "RESERVADO", "LIBRE", "VENCE", "ESTADO");
        List<StockLine> lines = inventory.stockLines();
        for (StockLine line : lines) {
            String state = line.quarantined() ? "CUARENTENA: " + line.quarantineReason()
                    : line.expired() ? "VENCIDO" : "ok";
            out.printf("  %-10s %-14s %-7s %7d %10d %7d  %-11s %s%n",
                    line.warehouse(), line.sku(), line.lotId(), line.onHand(), line.reserved(), line.available(),
                    line.expiry() == null ? "-" : line.expiry().toString(), state);
        }
        if (lines.isEmpty()) {
            out.println("  (sin stock)");
        }
    }

    static void warehouses(InventoryView inventory, PrintStream out) {
        out.printf("  %-10s %-34s %-18s %s%n", "CODIGO", "NOMBRE", "ALMACENAMIENTO", "ZONA DE DESPACHO");
        for (Warehouse warehouse : inventory.warehouses()) {
            out.printf("  %-10s %-34s %-18s %s%n", warehouse.code(), warehouse.name(),
                    inventory.storedUnits(warehouse.code()) + " / " + warehouse.storageCapacity() + " u",
                    inventory.stagedUnits(warehouse.code()) + " / " + warehouse.stagingCapacity() + " u");
        }
    }

    static void reservations(InventoryView inventory, PrintStream out) {
        List<Reservation> all = inventory.reservations();
        if (all.isEmpty()) {
            out.println("  (no hay reservas)");
            return;
        }
        out.printf("  %-9s %-11s %-11s %-14s %-10s %6s  %s%n",
                "RESERVA", "ESTADO", "DUEÑO", "SKU", "BODEGA", "UNID.", "LOTES");
        for (Reservation r : all) {
            out.printf("  %-9s %-11s %-11s %-14s %-10s %6d  %s%n", r.id(), r.status().label(), r.ownerId(),
                    r.sku(), r.warehouse(), r.units(), r.breakdown());
        }
    }

    static void ledger(Ledger ledger, ZoneId zone, int lastEntries, PrintStream out) {
        List<LedgerEntry> entries = ledger.entries();
        int from = Math.max(0, entries.size() - lastEntries);
        if (from > 0) {
            out.println("  ... (" + from + " entradas anteriores; usa 'ledger todo' para verlas)");
        }
        for (LedgerEntry entry : entries.subList(from, entries.size())) {
            String who = entry.actorKind() == null ? entry.actorId()
                    : entry.actorId() + " (" + entry.actorKind().label() + ")";
            String result = entry.rejectedBy() == null ? entry.outcome().label()
                    : entry.outcome().label() + " por " + entry.rejectedBy();
            out.printf("  #%-3d %s  %-9s %-28s %s  [huella %s]%n", entry.sequence(),
                    STAMP.format(entry.at().atZone(zone)), entry.commandId(), who, result, entry.fingerprint());
            out.println("        " + entry.summary());
            out.println("        -> " + entry.detail());
        }
        out.println("  Total: " + ledger.size() + " entradas | "
                + ledger.count(Outcome.EXECUTED) + " ejecutadas, "
                + ledger.count(Outcome.REJECTED) + " rechazadas, "
                + ledger.count(Outcome.DUPLICATE) + " duplicadas, "
                + ledger.count(Outcome.UNDONE) + " deshechas");
    }
}
