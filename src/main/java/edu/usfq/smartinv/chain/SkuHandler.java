package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.inventory.InventoryView;
import java.util.Objects;

/** ¿El producto existe en el catálogo? */
public final class SkuHandler extends BaseHandler {

    private final InventoryView inventory;

    public SkuHandler(InventoryView inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Verdict handle(OperationRequest request) {
        if (request.needs(Need.KNOWN_SKU)) {
            if (request.sku() == null || request.sku().isBlank()) {
                return reject("falta indicar el SKU");
            }
            if (inventory.product(request.sku()).isEmpty()) {
                return reject("el SKU " + request.sku() + " no existe en el catálogo");
            }
        }
        return super.handle(request);
    }

    @Override
    protected String linkName() {
        return "SKU";
    }
}
