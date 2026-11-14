package edu.usfq.smartinv.inventory;

/**
 * Unidades de un lote en una bodega. Invariante: 0 <= reservado <= físico.
 * Cada cambio se valida aquí, así que ningún camino puede dejar cantidades negativas.
 */
final class StockPosition {

    private final String lotId;
    private final String warehouse;
    private int onHand;
    private int reserved;

    StockPosition(String lotId, String warehouse) {
        this.lotId = lotId;
        this.warehouse = warehouse;
    }

    String lotId() {
        return lotId;
    }

    String warehouse() {
        return warehouse;
    }

    int onHand() {
        return onHand;
    }

    int reserved() {
        return reserved;
    }

    /** Unidades físicas que nadie tiene reservadas. */
    int available() {
        return onHand - reserved;
    }

    boolean isEmpty() {
        return onHand == 0;
    }

    void add(int units) {
        requirePositive(units);
        onHand += units;
    }

    void remove(int units) {
        requirePositive(units);
        if (units > available()) {
            throw new InventoryRuleViolation("el lote " + lotId + " solo tiene " + available() + " u libres en "
                    + warehouse + " (" + reserved + " reservadas); no se pueden quitar " + units);
        }
        onHand -= units;
    }

    void reserve(int units) {
        requirePositive(units);
        if (units > available()) {
            throw new InventoryRuleViolation("el lote " + lotId + " solo tiene " + available() + " u libres en "
                    + warehouse + "; no se pueden reservar " + units);
        }
        reserved += units;
    }

    void unreserve(int units) {
        requirePositive(units);
        if (units > reserved) {
            throw new InventoryRuleViolation("el lote " + lotId + " solo tiene " + reserved + " u reservadas en "
                    + warehouse + "; no se pueden liberar " + units);
        }
        reserved -= units;
    }

    /** Salida física de unidades que estaban reservadas. */
    void shipReserved(int units) {
        requirePositive(units);
        if (units > reserved) {
            throw new InventoryRuleViolation("el lote " + lotId + " solo tiene " + reserved + " u reservadas en "
                    + warehouse + "; no se pueden despachar " + units);
        }
        reserved -= units;
        onHand -= units;
    }

    private static void requirePositive(int units) {
        if (units <= 0) {
            throw new InventoryRuleViolation("la cantidad debe ser mayor que cero (se indicó " + units + ")");
        }
    }
}
