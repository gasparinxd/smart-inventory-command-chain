package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.inventory.CatalogOperations;
import edu.usfq.smartinv.inventory.Product;
import java.util.Objects;
import java.util.Optional;

/** Da de alta un producto: el inventario lo identifica por su nombre y le asigna SKU. */
public final class RegisterProductCommand implements InventoryCommand {

    private final String id;
    private final Actor actor;
    private final CatalogOperations catalog;
    private final String name;
    private Product product;

    public RegisterProductCommand(String id, Actor actor, CatalogOperations catalog, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.actor = actor;
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.name = name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationRequest request() {
        return OperationRequest.registerProduct(actor, name);
    }

    @Override
    public void execute() {
        product = catalog.registerProduct(name);
    }

    @Override
    public void undo() {
        catalog.retireProduct(registered().sku());
        product = null;
    }

    @Override
    public OperationRequest compensation(Actor requester) {
        return OperationRequest.retireProduct(requester, registered().sku());
    }

    @Override
    public String outcome() {
        Product p = registered();
        return "\"" + p.name() + "\" identificado como " + p.category().label() + " con el código " + p.sku();
    }

    public Optional<Product> product() {
        return Optional.ofNullable(product);
    }

    private Product registered() {
        if (product == null) {
            throw new IllegalStateException("el comando " + id + " todavía no se ejecutó");
        }
        return product;
    }
}
