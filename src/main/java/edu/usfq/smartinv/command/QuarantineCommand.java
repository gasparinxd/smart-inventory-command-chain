package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.StockOperations;
import java.util.Objects;

/**
 * Pone un lote en cuarentena o se la levanta. Cualquiera puede bloquear un lote dudoso,
 * pero solo un supervisor puede volver a habilitarlo; por eso deshacer un bloqueo
 * también exige supervisor.
 */
public final class QuarantineCommand implements InventoryCommand {

    public enum Action { PLACE, LIFT }

    private final String id;
    private final Actor actor;
    private final StockOperations stock;
    private final String lotId;
    private final Action action;
    private String reason;

    private QuarantineCommand(String id, Actor actor, StockOperations stock, String lotId, Action action,
                              String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.stock = Objects.requireNonNull(stock, "stock");
        this.lotId = lotId;
        this.action = action;
        this.reason = reason;
    }

    public static QuarantineCommand place(String id, Actor actor, StockOperations stock, String lotId,
                                          String reason) {
        return new QuarantineCommand(id, actor, stock, lotId, Action.PLACE, reason);
    }

    public static QuarantineCommand lift(String id, Actor actor, StockOperations stock, String lotId) {
        return new QuarantineCommand(id, actor, stock, lotId, Action.LIFT, null);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return action == Action.PLACE
                ? OperationRequest.quarantine(actor, lotId, reason)
                : OperationRequest.liftQuarantine(actor, lotId);
    }

    @Override
    public void execute() {
        if (action == Action.PLACE) {
            stock.quarantine(lotId, reason);
        } else {
            reason = stock.liftQuarantine(lotId);
        }
    }

    @Override
    public void undo() {
        if (action == Action.PLACE) {
            stock.liftQuarantine(lotId);
        } else {
            stock.quarantine(lotId, reason);
        }
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return action == Action.PLACE
                ? OperationRequest.liftQuarantine(requester, lotId)
                : OperationRequest.quarantine(requester, lotId, reason);
    }

    @Override
    public String outcome() {
        return action == Action.PLACE
                ? "el lote " + lotId + " quedó en cuarentena (" + reason + ")"
                : "el lote " + lotId + " salió de cuarentena";
    }
}
