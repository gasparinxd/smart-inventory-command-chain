package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.inventory.InventoryView;
import edu.usfq.smartinv.inventory.Warehouse;
import java.util.Objects;
import java.util.Optional;

/** Último eslabón: ¿hay espacio donde llegan las unidades y en la zona de despacho? */
public final class CapacityHandler extends BaseHandler {

    private final InventoryView inventory;

    public CapacityHandler(InventoryView inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Verdict handle(OperationRequest request) {
        int arriving = request.unitsArriving();
        if (arriving > 0) {
            String code = request.arrivalWarehouse();
            Optional<Warehouse> destination = inventory.warehouse(code);
            if (destination.isEmpty()) {
                return reject("la bodega " + code + " no existe");
            }
            if (request.targetWarehouse() != null && request.targetWarehouse().equals(request.warehouse())) {
                return reject("el origen y el destino son la misma bodega (" + code + ")");
            }
            int stored = inventory.storedUnits(code);
            if (stored + arriving > destination.get().storageCapacity()) {
                return reject(code + " almacena " + stored + " de " + destination.get().storageCapacity()
                        + " u; no caben " + arriving + " más");
            }
        }
        if (request.needs(Need.STAGING_ROOM)) {
            Optional<Warehouse> place = inventory.warehouse(request.warehouse());
            if (place.isEmpty()) {
                return reject("la bodega " + request.warehouse() + " no existe");
            }
            int staged = inventory.stagedUnits(request.warehouse());
            if (staged + request.quantity() > place.get().stagingCapacity()) {
                return reject("la zona de despacho de " + request.warehouse() + " tiene " + staged + " de "
                        + place.get().stagingCapacity() + " u comprometidas; no caben " + request.quantity()
                        + " más");
            }
        }
        return super.handle(request);
    }

    @Override
    protected String linkName() {
        return "Capacidad";
    }
}
