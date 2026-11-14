package edu.usfq.smartinv.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductClassifierTest {

    private final ProductClassifier classifier = new ProductClassifier();

    @ParameterizedTest(name = "\"{0}\" es {1}")
    @CsvSource({
        "Laptop Lenovo ThinkPad E14, ELECTRONICS",
        "LAPTOPS gamer 16 GB, ELECTRONICS",
        "Teléfono inalámbrico, ELECTRONICS",
        "Yogur griego natural 1 kg, REFRIGERATED",
        "Paracetamol 500 mg caja x100, PHARMACY",
        "Atún en lata 170 g, GROCERY",
        "Detergente líquido 3 L, CLEANING",
        "Taladro percutor 650 W, HARDWARE",
        "Silla plegable, GENERAL",
    })
    void identifiesTheFamilyFromTheName(String name, Category expected) {
        assertEquals(expected, classifier.classify(name).category());
    }

    @Test
    void reportsWhichKeywordMatched() {
        ProductClassifier.Match match = classifier.classify("Caja de LAPTOPS reacondicionadas");
        assertEquals(Category.ELECTRONICS, match.category());
        assertEquals("laptop", match.keyword());
    }

    @Test
    void unknownNamesFallBackToGeneralWithoutKeyword() {
        ProductClassifier.Match match = classifier.classify("Silla plegable");
        assertEquals(Category.GENERAL, match.category());
        assertNull(match.keyword());
    }

    @Test
    void theFirstWordThatMatchesDecides() {
        // "jabón" aparece antes que "laptop"
        assertEquals(Category.CLEANING, classifier.classify("Jabón para limpiar laptop").category());
    }

    @ParameterizedTest
    @CsvSource({
        "Laptop Lenovo, LAP",
        "Atún en lata, ATU",
        "5 m de cable, CAB",
        "TV 4K, GEN",
    })
    void buildsThreeLetterMnemonic(String name, String expected) {
        assertEquals(expected, classifier.mnemonic(name));
    }

    @Test
    void keywordsCanBeReplacedWithoutChangingTheClass() {
        ProductClassifier custom = new ProductClassifier(Map.of(Category.HARDWARE, List.of("Válvula")));
        assertEquals(Category.HARDWARE, custom.classify("valvulas de bronce").category());
        assertEquals(Category.GENERAL, custom.classify("Laptop").category());
    }
}
