package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.inventory.InventoryView;
import java.util.Objects;

/**
 * ¿La cantidad tiene sentido y hay unidades físicas libres para lo que sale?
 * Aquí todavía no importa si el lote está vencido o en cuarentena: eso es del eslabón siguiente.
 */
public final class StockHandler extends BaseHandler {

    private final InventoryView inventory;

    public StockHandler(InventoryView inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Verdict handle(OperationRequest request) {
        if (request.needs(Need.POSITIVE_QUANTITY) && request.quantity() <= 0) {
            return reject("la cantidad debe ser mayor que cero (se pidió " + request.quantity() + ")");
        }
        if (request.needs(Need.NONZERO_QUANTITY) && request.quantity() == 0) {
            return reject("un ajuste de 0 unidades no cambia nada");
        }

        int leaving = request.unitsLeaving();
        if (leaving > 0) {
            String warehouse = request.warehouse();
            if (inventory.warehouse(warehouse).isEmpty()) {
                return reject("la bodega " + warehouse + " no existe");
            }
            boolean byLot = request.lotId() != null;
            if (byLot && inventory.lot(request.lotId()).isEmpty()) {
                return super.handle(request); // el eslabón de lote explica que no existe
            }
            int free = byLot
                    ? inventory.unreservedUnitsOfLot(request.lotId(), warehouse)
                    : inventory.unreservedUnits(request.sku(), warehouse);
            if (free < leaving) {
                String what = byLot ? "del lote " + request.lotId() : "de " + request.sku();
                return reject("en " + warehouse + " hay " + free + " u libres " + what + " y se necesitan "
                        + leaving);
            }
        }
        return super.handle(request);
    }

    @Override
    protected String linkName() {
        return "Stock";
    }
}
