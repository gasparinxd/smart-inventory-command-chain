package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.Reservation;
import edu.usfq.smartinv.inventory.ReservationOperations;
import java.util.Objects;
import java.util.Optional;

/** Reserve: aparta unidades para el actor. Su inverso es liberar esa misma reserva. */
public final class ReserveCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final ReservationOperations inventory;
    private final String sku;
    private final String warehouse;
    private final int quantity;
    private Reservation reservation;

    public ReserveCommand(String id, Actor actor, ReservationOperations inventory, String sku, String warehouse,
                          int quantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.sku = sku;
        this.warehouse = warehouse;
        this.quantity = quantity;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.reserve(actor, sku, warehouse, quantity);
    }

    @Override
    public void execute() {
        reservation = inventory.reserve(actor == null ? null : actor.id(), sku, warehouse, quantity);
    }

    @Override
    public void undo() {
        inventory.release(made().id());
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return OperationRequest.release(requester, made().id());
    }

    @Override
    public String outcome() {
        Reservation r = made();
        return "reserva " + r.id() + " de " + r.ownerId() + ": " + r.units() + " u de " + r.sku() + " en "
                + r.warehouse() + " [" + r.breakdown() + "]";
    }

    public Optional<Reservation> reservation() {
        return Optional.ofNullable(reservation);
    }

    private Reservation made() {
        if (reservation == null) {
            throw new IllegalStateException("el comando " + id + " todavía no se ejecutó");
        }
        return reservation;
    }
}
