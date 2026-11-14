package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.Lot;
import edu.usfq.smartinv.inventory.StockOperations;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Entrada de mercadería: crea un lote nuevo en una bodega. */
public final class ReceiveLotCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final StockOperations stock;
    private final String sku;
    private final String warehouse;
    private final int quantity;
    private final LocalDate expiry;
    private Lot lot;

    public ReceiveLotCommand(String id, Actor actor, StockOperations stock, String sku, String warehouse,
                             int quantity, LocalDate expiry) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.stock = Objects.requireNonNull(stock, "stock");
        this.sku = sku;
        this.warehouse = warehouse;
        this.quantity = quantity;
        this.expiry = expiry;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.receive(actor, sku, warehouse, quantity, expiry);
    }

    @Override
    public void execute() {
        lot = stock.receiveLot(sku, warehouse, quantity, expiry);
    }

    @Override
    public void undo() {
        stock.cancelReceipt(received().id());
        lot = null;
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return OperationRequest.cancelReceipt(requester, received().id());
    }

    @Override
    public String outcome() {
        return "lote " + received().id() + ": " + quantity + " u de " + sku + " en " + warehouse
                + received().expiry().map(date -> ", vence " + date).orElse("");
    }

    public Optional<Lot> lot() {
        return Optional.ofNullable(lot);
    }

    private Lot received() {
        if (lot == null) {
            throw new IllegalStateException("el comando " + id + " todavía no se ejecutó");
        }
        return lot;
    }
}
