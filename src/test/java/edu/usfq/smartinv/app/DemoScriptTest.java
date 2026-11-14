package edu.usfq.smartinv.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.gateway.Outcome;
import edu.usfq.smartinv.inventory.StockLine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** La demostración completa debe correr sin errores y dejar el inventario coherente. */
class DemoScriptTest {

    private static Bootstrap app;
    private static String output;

    @BeforeAll
    static void runDemoOnce() {
        app = Bootstrap.withSampleData(Fixtures.clock());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new DemoScript(app, new PrintStream(buffer, true, StandardCharsets.UTF_8)).run();
        output = buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void everyLinkOfTheChainRejectsAtLeastOnce() {
        for (String link : new String[] {"Autorización", "SKU", "Stock", "Lote", "Capacidad"}) {
            assertTrue(output.contains("[RECHAZADO por " + link + "]"), "falta un rechazo de " + link);
        }
    }

    @Test
    void itShowsIdempotencyUndoAndTheReceiverDefendingItself() {
        assertTrue(output.contains("[DUPLICADO] WEB-501"));
        assertTrue(output.contains("[RECHAZADO por Idempotencia] WEB-501"));
        assertTrue(output.contains("[EJECUTADO] WEB-502")); // el rechazo previo no gastó el id
        assertTrue(output.contains("[DESHECHO] D-702"));
        assertTrue(output.contains("InventoryRuleViolation"));
    }

    @Test
    void theLaptopIsIdentifiedAsElectronics() {
        assertTrue(output.contains("\"Laptop HP Pavilion 15\" identificado como Electrónica con el código ELEC-LAP-002"));
    }

    @Test
    void theLedgerAddsUp() {
        long total = app.ledger().count(Outcome.EXECUTED) + app.ledger().count(Outcome.REJECTED)
                + app.ledger().count(Outcome.DUPLICATE) + app.ledger().count(Outcome.UNDONE);
        assertEquals(app.ledger().size(), total);
        assertEquals(3, app.ledger().count(Outcome.UNDONE));
        assertEquals(1, app.ledger().count(Outcome.DUPLICATE));
    }

    @Test
    void stockStaysConsistentAfterTheWholeScript() {
        for (StockLine line : app.inventory().stockLines()) {
            assertTrue(line.onHand() > 0, line + " no debería existir vacío");
            assertTrue(line.reserved() >= 0 && line.reserved() <= line.onHand(), line.toString());
        }
        assertEquals(40, app.inventory().unreservedUnitsOfLot("L-0001", "QUITO"));
        assertTrue(app.inventory().lot("L-0002").isPresent()); // el lote vencido se dio de baja, no se borró su historia
        assertEquals(0, app.inventory().unreservedUnitsOfLot("L-0002", "QUITO"));
    }
}
