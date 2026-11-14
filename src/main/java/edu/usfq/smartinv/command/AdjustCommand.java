package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.StockOperations;
import java.util.Objects;

/**
 * Adjust: corrige el stock de un lote por conteo físico, merma o hallazgo.
 * Su inverso es el mismo ajuste con el signo contrario.
 */
public final class AdjustCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final StockOperations stock;
    private final String lotId;
    private final String warehouse;
    private final int delta;
    private final String reason;

    public AdjustCommand(String id, Actor actor, StockOperations stock, String lotId, String warehouse,
                         int delta, String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.stock = Objects.requireNonNull(stock, "stock");
        this.lotId = lotId;
        this.warehouse = warehouse;
        this.delta = delta;
        this.reason = reason == null || reason.isBlank() ? "sin motivo" : reason.strip();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.adjust(actor, lotId, warehouse, delta, reason);
    }

    @Override
    public void execute() {
        stock.adjust(lotId, warehouse, delta);
    }

    @Override
    public void undo() {
        stock.adjust(lotId, warehouse, -delta);
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return OperationRequest.adjust(requester, lotId, warehouse, -delta, "revierte " + id);
    }

    @Override
    public String outcome() {
        String verb = delta > 0 ? "se sumaron " + delta : "se restaron " + (-delta);
        return verb + " u al lote " + lotId + " en " + warehouse + " (" + reason + ")";
    }
}
