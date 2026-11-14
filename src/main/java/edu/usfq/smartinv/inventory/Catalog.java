package edu.usfq.smartinv.inventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Productos conocidos. Al registrar un nombre lo identifica con el clasificador y le
 * arma el SKU: FAMILIA-ABC-NNN (prefijo de la familia, tres letras del nombre y un
 * consecutivo por familia). Es una pieza interna del inventario.
 */
final class Catalog {

    private final ProductClassifier classifier;
    private final Map<String, Product> bySku = new LinkedHashMap<>();
    private final Map<Category, Integer> counters = new EnumMap<>(Category.class);

    Catalog(ProductClassifier classifier) {
        this.classifier = classifier;
    }

    Product register(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new InventoryRuleViolation("el nombre del producto no puede estar vacío");
        }
        String name = rawName.strip().replaceAll("\\s+", " ");
        String key = ProductClassifier.normalize(name);
        for (Product existing : bySku.values()) {
            if (ProductClassifier.normalize(existing.name()).equals(key)) {
                throw new InventoryRuleViolation("\"" + existing.name() + "\" ya está registrado con el código "
                        + existing.sku());
            }
        }
        Category category = classifier.classify(name).category();
        int number = counters.merge(category, 1, Integer::sum);
        String sku = "%s-%s-%03d".formatted(category.prefix(), classifier.mnemonic(name), number);
        Product product = new Product(sku, name, category);
        bySku.put(sku, product);
        return product;
    }

    void retire(String sku) {
        bySku.remove(sku);
    }

    Optional<Product> find(String sku) {
        return Optional.ofNullable(sku == null ? null : bySku.get(sku));
    }

    List<Product> all() {
        return new ArrayList<>(bySku.values());
    }
}
