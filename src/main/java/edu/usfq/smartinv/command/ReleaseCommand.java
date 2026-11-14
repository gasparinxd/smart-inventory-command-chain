package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.Reservation;
import edu.usfq.smartinv.inventory.ReservationOperations;
import java.util.Objects;

/** Release: devuelve al stock libre las unidades de una reserva. Su inverso la vuelve a apartar. */
public final class ReleaseCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final ReservationOperations inventory;
    private final String reservationId;
    private Reservation released;

    public ReleaseCommand(String id, Actor actor, ReservationOperations inventory, String reservationId) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.reservationId = reservationId;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.release(actor, reservationId);
    }

    @Override
    public void execute() {
        released = inventory.release(reservationId);
    }

    @Override
    public void undo() {
        inventory.reinstate(done().id());
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        Reservation r = done();
        return OperationRequest.reserve(requester, r.sku(), r.warehouse(), r.units());
    }

    @Override
    public String outcome() {
        Reservation r = done();
        return "reserva " + r.id() + " liberada: " + r.units() + " u de " + r.sku() + " vuelven a estar libres en "
                + r.warehouse();
    }

    private Reservation done() {
        if (released == null) {
            throw new IllegalStateException("el comando " + id + " todavía no se ejecutó");
        }
        return released;
    }
}
