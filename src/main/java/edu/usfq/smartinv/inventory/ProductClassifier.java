package edu.usfq.smartinv.inventory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Identifica a qué familia pertenece un producto a partir de su nombre.
 * Recorre las palabras del nombre en orden y se queda con la primera que coincide
 * con una palabra clave. No distingue tildes ni mayúsculas y acepta plurales
 * ("laptops" coincide con "laptop").
 */
public final class ProductClassifier {

    /** Resultado de identificar un nombre: la familia y la palabra que la delató. */
    public record Match(Category category, String keyword) {
    }

    private final Map<Category, List<String>> keywords;

    public ProductClassifier() {
        this(defaultKeywords());
    }

    public ProductClassifier(Map<Category, List<String>> keywords) {
        Objects.requireNonNull(keywords, "keywords");
        Map<Category, List<String>> copy = new EnumMap<>(Category.class);
        keywords.forEach((category, words) ->
                copy.put(category, words.stream().map(ProductClassifier::normalize).toList()));
        this.keywords = copy;
    }

    public Match classify(String productName) {
        for (String word : words(productName)) {
            for (Map.Entry<Category, List<String>> entry : keywords.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (word.startsWith(keyword)) {
                        return new Match(entry.getKey(), keyword);
                    }
                }
            }
        }
        return new Match(Category.GENERAL, null);
    }

    /** Tres letras que resumen el nombre dentro del SKU: "Laptop Lenovo" da "LAP". */
    public String mnemonic(String productName) {
        for (String word : words(productName)) {
            String letters = word.replaceAll("[^a-z]", "");
            if (letters.length() >= 3) {
                return letters.substring(0, 3).toUpperCase(Locale.ROOT);
            }
        }
        return "GEN";
    }

    /** Minúsculas y sin tildes, para comparar nombres. */
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    private static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        for (String piece : normalize(text).split("[^a-z0-9]+")) {
            if (!piece.isEmpty()) {
                result.add(piece);
            }
        }
        return result;
    }

    private static Map<Category, List<String>> defaultKeywords() {
        Map<Category, List<String>> table = new EnumMap<>(Category.class);
        table.put(Category.ELECTRONICS, List.of("laptop", "celular", "telefono", "tablet", "televisor",
                "monitor", "teclado", "audifono", "cargador", "impresora", "router"));
        table.put(Category.REFRIGERATED, List.of("yogur", "leche", "queso", "mantequilla", "pollo",
                "carne", "jamon", "helado", "embutido"));
        table.put(Category.PHARMACY, List.of("paracetamol", "ibuprofeno", "jarabe", "vitamina",
                "amoxicilina", "antibiotico", "suero", "aspirina", "insulina"));
        table.put(Category.GROCERY, List.of("arroz", "atun", "fideo", "azucar", "aceite", "lenteja",
                "harina", "cafe", "avena", "galleta"));
        table.put(Category.CLEANING, List.of("detergente", "cloro", "jabon", "desinfectante",
                "lavavajilla", "suavizante", "escoba"));
        table.put(Category.HARDWARE, List.of("taladro", "martillo", "tornillo", "destornillador",
                "cemento", "pintura", "clavo", "sierra"));
        return table;
    }
}
