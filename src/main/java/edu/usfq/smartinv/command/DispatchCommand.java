package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.Reservation;
import edu.usfq.smartinv.inventory.ReservationOperations;
import java.util.Objects;

/**
 * Despacha una reserva: las unidades salen físicamente de la bodega y se cierra el ciclo
 * del stock. Su inverso es una devolución, que las regresa y reactiva la reserva.
 */
public final class DispatchCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final ReservationOperations inventory;
    private final String reservationId;
    private Reservation dispatched;

    public DispatchCommand(String id, Actor actor, ReservationOperations inventory, String reservationId) {
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
        return OperationRequest.dispatch(actor, reservationId);
    }

    @Override
    public void execute() {
        dispatched = inventory.dispatch(reservationId);
    }

    @Override
    public void undo() {
        inventory.acceptReturn(done().id());
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        Reservation r = done();
        return OperationRequest.customerReturn(requester, r.id(), r.warehouse(), r.units());
    }

    @Override
    public String outcome() {
        Reservation r = done();
        return "reserva " + r.id() + " despachada: salen " + r.units() + " u de " + r.sku() + " de "
                + r.warehouse() + " [" + r.breakdown() + "]";
    }

    private Reservation done() {
        if (dispatched == null) {
            throw new IllegalStateException("el comando " + id + " todavía no se ejecutó");
        }
        return dispatched;
    }
}
