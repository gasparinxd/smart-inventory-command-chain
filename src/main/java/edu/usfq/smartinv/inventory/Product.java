package edu.usfq.smartinv.inventory;

import java.util.Objects;

/** Producto del catálogo, ya identificado: tiene familia y SKU. */
public record Product(String sku, String name, Category category) {

    public Product {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
    }
}
