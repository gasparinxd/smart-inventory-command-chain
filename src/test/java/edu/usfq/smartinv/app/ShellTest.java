package edu.usfq.smartinv.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.gateway.Outcome;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La consola de punta a punta: líneas de texto que entran, respuestas que salen. */
class ShellTest {

    private Bootstrap app;
    private ByteArrayOutputStream buffer;
    private Shell shell;

    @BeforeEach
    void setUp() {
        app = Bootstrap.withSampleData(Fixtures.clock());
        buffer = new ByteArrayOutputStream();
        shell = new Shell(app, new StringReader(""), new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    /** Envía una línea y devuelve lo que la consola respondió a esa línea. */
    private String type(String line) {
        buffer.reset();
        shell.interpret(line);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sampleDataIsLoadedThroughCommandsSoItIsInTheLedger() {
        assertEquals(6, app.inventory().products().size());
        assertEquals(8, app.inventory().stockLines().size());
        assertEquals(15, app.ledger().count(Outcome.EXECUTED));
    }

    @Test
    void registeringAProductIdentifiesItAndAssignsACode() {
        String answer = type("registrar Laptop HP Pavilion 15");
        assertTrue(answer.contains("[EJECUTADO] CMD-0001"), answer);
        assertTrue(answer.contains("identificado como Electrónica con el código ELEC-LAP-002"), answer);
    }

    @Test
    void codesAreAcceptedInLowerCase() {
        String answer = type("reservar refr-yog-001 quito 100");
        assertTrue(answer.contains("[EJECUTADO]"), answer);
        assertTrue(answer.contains("60 de L-0002 + 40 de L-0003"), answer);
    }

    @Test
    void rejectionsNameTheLinkThatStoppedThem() {
        assertTrue(type("reservar FARM-PAR-001 GUAYAQUIL 10").contains("[RECHAZADO por Lote]"));
        assertTrue(type("reservar ELEC-LAP-001 QUITO 500").contains("[RECHAZADO por Stock]"));
        assertTrue(type("ajustar L-0001 QUITO -2 pantalla rota").contains("[RECHAZADO por Autorización]"));
        assertTrue(type("transferir L-0007 QUITO CUENCA 30").contains("[RECHAZADO por Capacidad]"));
        assertTrue(type("reservar NADA-XXX-001 QUITO 1").contains("[RECHAZADO por SKU]"));
    }

    @Test
    void anExplicitIdMakesRetriesIdempotent() {
        assertTrue(type("#PED-7 reservar ABAR-ATU-001 GUAYAQUIL 20").contains("[EJECUTADO] PED-7"));
        assertTrue(type("#PED-7 reservar ABAR-ATU-001 GUAYAQUIL 20").contains("[DUPLICADO] PED-7"));
        assertTrue(type("#PED-7 reservar ABAR-ATU-001 GUAYAQUIL 99").contains("[RECHAZADO por Idempotencia] PED-7"));
        assertEquals(280, app.inventory().sellableUnits("ABAR-ATU-001", "GUAYAQUIL"));
    }

    @Test
    void retryResendsTheLastCommandWithTheSameId() {
        type("reservar ELEC-LAP-001 QUITO 10");
        String answer = type("reintentar");
        assertTrue(answer.contains("[DUPLICADO] CMD-0001"), answer);
        assertEquals(30, app.inventory().sellableUnits("ELEC-LAP-001", "QUITO"));
    }

    @Test
    void switchingActorChangesWhoSendsTheCommands() {
        type("reservar ELEC-LAP-001 QUITO 10");
        type("actor pulse-ai");
        assertTrue(type("liberar RSV-0001").contains("[RECHAZADO por Autorización]"));
        type("actor mateo");
        assertTrue(type("liberar RSV-0001").contains("[EJECUTADO]"));
        assertTrue(type("actor nadie").contains("No existe el actor"));
    }

    @Test
    void undoGoesThroughTheGatewayToo() {
        type("transferir L-0001 QUITO GUAYAQUIL 5");
        assertEquals(5, app.inventory().unreservedUnitsOfLot("L-0001", "GUAYAQUIL"));
        assertTrue(type("deshacer").contains("[DESHECHO]"));
        assertEquals(0, app.inventory().unreservedUnitsOfLot("L-0001", "GUAYAQUIL"));
    }

    @Test
    void advancingTheClockMakesLotsExpire() {
        type("reservar REFR-YOG-001 QUITO 100");
        assertTrue(type("avanzar 5").contains("fecha del sistema"));
        assertTrue(type("despachar RSV-0001").contains("[RECHAZADO por Lote]"));
        assertTrue(type("stock").contains("VENCIDO"));
    }

    @Test
    void badInputShowsHowToUseTheCommandInsteadOfFailing() {
        assertTrue(type("reservar ELEC-LAP-001").startsWith("Uso: reservar"));
        assertTrue(type("reservar ELEC-LAP-001 QUITO muchas").contains("no es un número entero"));
        assertTrue(type("recibir ELEC-LAP-001 QUITO 5 mañana").contains("no es una fecha"));
        assertTrue(type("bailar").contains("No conozco el comando"));
        assertEquals("", type("   "));
    }

    @Test
    void queriesPrintTheCurrentState() {
        assertTrue(type("productos").contains("ELEC-LAP-001"));
        assertTrue(type("stock").contains("CUARENTENA: sello de seguridad roto"));
        assertTrue(type("bodegas").contains("100 / 120 u"));
        assertTrue(type("reservas").contains("no hay reservas"));
        assertTrue(type("ledger").contains("Total: 15 entradas"));
        assertTrue(type("ayuda").contains("reservar <sku> <bodega> <cantidad>"));
    }

    @Test
    void exitStopsTheLoop() {
        assertFalse(shell.interpret("salir"));
        assertTrue(shell.interpret("stock"));
    }

    @Test
    void runReadsLinesUntilExit() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Shell scripted = new Shell(app, new StringReader("reservar ELEC-LAP-001 QUITO 10\nsalir\nstock\n"),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        scripted.run();

        String all = out.toString(StandardCharsets.UTF_8);
        assertTrue(all.contains("[EJECUTADO] CMD-0001"), all);
        assertTrue(all.contains("Hasta luego."), all);
        assertFalse(all.contains("FISICO"), "no debía procesar nada después de 'salir'");
    }

    @Test
    void theDemoRunsOnASeparateCopyAndLeavesTheSessionUntouched() {
        int before = app.ledger().size();
        String answer = type("demo");
        assertTrue(answer.contains("== 10. Así quedó todo"), answer);
        assertEquals(before, app.ledger().size());
    }
}
