package edu.usfq.smartinv.chain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Operaciones del inventario y qué debe comprobarse antes de cada una.
 * Las cuatro de la presentación (Reserve, Release, Adjust, Transfer) y las que las
 * rodean: alta de productos, recepción, despacho y cuarentena, más sus inversas.
 */
public enum OperationType {
    REGISTER_PRODUCT("registrar producto"),
    RETIRE_PRODUCT("retirar producto", Need.KNOWN_SKU),
    RECEIVE("recibir lote", Need.KNOWN_SKU, Need.POSITIVE_QUANTITY, Need.VALID_EXPIRY, Need.STORAGE_ROOM),
    CANCEL_RECEIPT("anular recepción", Need.EXISTING_LOT),
    RESERVE("reservar", Need.KNOWN_SKU, Need.POSITIVE_QUANTITY, Need.PHYSICAL_STOCK, Need.SELLABLE_STOCK,
            Need.STAGING_ROOM),
    RELEASE("liberar reserva", Need.OWNED_RESERVATION),
    DISPATCH("despachar", Need.OWNED_RESERVATION, Need.HEALTHY_LOT),
    RETURN("registrar devolución", Need.OWNED_RESERVATION, Need.STORAGE_ROOM, Need.STAGING_ROOM),
    ADJUST("ajustar stock", Need.SUPERVISOR, Need.NONZERO_QUANTITY, Need.EXISTING_LOT, Need.PHYSICAL_STOCK,
            Need.STORAGE_ROOM),
    TRANSFER("transferir", Need.POSITIVE_QUANTITY, Need.EXISTING_LOT, Need.PHYSICAL_STOCK, Need.HEALTHY_LOT,
            Need.STORAGE_ROOM),
    QUARANTINE("poner en cuarentena", Need.EXISTING_LOT),
    LIFT_QUARANTINE("levantar cuarentena", Need.SUPERVISOR, Need.EXISTING_LOT);

    private final String label;
    private final Set<Need> needs;

    OperationType(String label, Need... needs) {
        this.label = label;
        this.needs = needs.length == 0 ? EnumSet.noneOf(Need.class) : EnumSet.copyOf(List.of(needs));
    }

    public String label() {
        return label;
    }

    public boolean needs(Need need) {
        return needs.contains(need);
    }

    /** En un ajuste la cantidad lleva signo: negativa resta, positiva suma. */
    public boolean signedQuantity() {
        return needs.contains(Need.NONZERO_QUANTITY);
    }
}
