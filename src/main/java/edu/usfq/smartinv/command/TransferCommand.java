package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.StockOperations;
import java.util.Objects;

/** Transfer: mueve unidades de un lote entre bodegas. Su inverso es la transferencia de regreso. */
public final class TransferCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final StockOperations stock;
    private final String lotId;
    private final String from;
    private final String to;
    private final int quantity;

    public TransferCommand(String id, Actor actor, StockOperations stock, String lotId, String from, String to,
                           int quantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.stock = Objects.requireNonNull(stock, "stock");
        this.lotId = lotId;
        this.from = from;
        this.to = to;
        this.quantity = quantity;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.transfer(actor, lotId, from, to, quantity);
    }

    @Override
    public void execute() {
        stock.transfer(lotId, from, to, quantity);
    }

    @Override
    public void undo() {
        stock.transfer(lotId, to, from, quantity);
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return OperationRequest.transfer(requester, lotId, to, from, quantity);
    }

    @Override
    public String outcome() {
        return quantity + " u del lote " + lotId + " pasaron de " + from + " a " + to;
    }
}
