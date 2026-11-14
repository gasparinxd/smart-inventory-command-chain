package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.actors.ActorKind;
import edu.usfq.smartinv.inventory.InventoryView;
import edu.usfq.smartinv.inventory.Reservation;
import java.util.Objects;
import java.util.Optional;

/**
 * Primer eslabón: ¿este actor puede pedir esto?
 * Aplica las mismas reglas a personas, sistemas y agentes de IA, y a los agentes
 * les añade un tope de unidades por operación.
 */
public final class AuthorizationHandler extends BaseHandler {

    private final InventoryView inventory;
    private final int agentUnitLimit;

    public AuthorizationHandler(InventoryView inventory, int agentUnitLimit) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        if (agentUnitLimit <= 0) {
            throw new IllegalArgumentException("el tope para agentes debe ser mayor que cero");
        }
        this.agentUnitLimit = agentUnitLimit;
    }

    @Override
    public Verdict handle(OperationRequest request) {
        Actor actor = request.actor();
        if (actor == null) {
            return reject("la operación no dice quién la solicita");
        }
        if (request.needs(Need.SUPERVISOR) && !actor.isSupervisor()) {
            return reject(actor.id() + " es " + actor.role().label() + "; para " + request.type().label()
                    + " hace falta un supervisor");
        }
        if (actor.kind() == ActorKind.AI_AGENT && Math.abs(request.quantity()) > agentUnitLimit) {
            return reject("un agente de IA puede mover hasta " + agentUnitLimit + " u por operación y "
                    + actor.id() + " pidió " + Math.abs(request.quantity()) + "; debe enviarla una persona");
        }
        if (request.needs(Need.OWNED_RESERVATION)) {
            Optional<Reservation> reservation = inventory.reservation(request.reservationId());
            if (reservation.isEmpty()) {
                return reject("la reserva " + request.reservationId() + " no existe");
            }
            String owner = reservation.get().ownerId();
            if (!owner.equals(actor.id()) && !actor.isSupervisor()) {
                return reject("la reserva " + request.reservationId() + " es de " + owner + "; solo " + owner
                        + " o un supervisor pueden actuar sobre ella");
            }
        }
        return super.handle(request);
    }

    @Override
    protected String linkName() {
        return "Autorización";
    }
}
