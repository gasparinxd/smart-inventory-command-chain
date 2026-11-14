package edu.usfq.smartinv.inventory;

/** Lo que un comando necesita del receptor para trabajar con el catálogo. */
public interface CatalogOperations {

    /** Identifica el producto por su nombre, le asigna SKU y lo guarda. */
    Product registerProduct(String name);

    /** Quita un producto que todavía no tiene lotes. */
    void retireProduct(String sku);
}
