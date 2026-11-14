package edu.usfq.smartinv.gateway;

import static edu.usfq.smartinv.Fixtures.BOT;
import static edu.usfq.smartinv.Fixtures.DIEGO;
import static edu.usfq.smartinv.Fixtures.MATEO;
import static edu.usfq.smartinv.Fixtures.SOFIA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.actors.ActorKind;
import edu.usfq.smartinv.chain.OperationType;
import edu.usfq.smartinv.command.AdjustCommand;
import edu.usfq.smartinv.command.QuarantineCommand;
import edu.usfq.smartinv.command.RegisterProductCommand;
import edu.usfq.smartinv.command.ReleaseCommand;
import edu.usfq.smartinv.command.ReserveCommand;
import edu.usfq.smartinv.command.TransferCommand;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.Reservation;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El invoker: idempotencia, cadena, ejecución, deshacer y ledger. */
class OperationsGatewayTest {

    private Inventory inventory;
    private Ledger ledger;
    private OperationsGateway gateway;
    private String laptop;
    private String lot;

    @BeforeEach
    void setUp() {
        Clock clock = Fixtures.clock();
        inventory = Fixtures.inventory(clock);
        ledger = new Ledger(clock);
        gateway = new OperationsGateway(Fixtures.chain(inventory), ledger);
        laptop = inventory.registerProduct("Laptop Lenovo ThinkPad E14").sku();
        lot = inventory.receiveLot(laptop, "QUITO", 100, null).id();
    }

    private ReserveCommand reserve(String id, int quantity) {
        return new ReserveCommand(id, SOFIA, inventory, laptop, "QUITO", quantity);
    }

    private int free() {
        return inventory.unreservedUnits(laptop, "QUITO");
    }

    // ------------------------------------------------------- invoker básico

    @Test
    void executeWithoutACommandIsAProgrammingError() {
        assertThrows(IllegalStateException.class, () -> gateway.executeCommand());
    }

    @Test
    void setCommandThenExecuteRunsTheCommandOnTheReceiver() {
        gateway.setCommand(reserve("C-1", 30));
        ExecutionReport report = gateway.executeCommand();

        assertEquals(Outcome.EXECUTED, report.outcome());
        assertTrue(report.changedInventory());
        assertNull(report.rejectedBy());
        assertEquals(70, free());
    }

    @Test
    void theInvokerWorksWithAnyCommandWithoutKnowingItsClass() {
        assertEquals(Outcome.EXECUTED,
                gateway.submit(new RegisterProductCommand("C-1", SOFIA, inventory, "Monitor LG 27")).outcome());
        assertEquals(Outcome.EXECUTED,
                gateway.submit(new TransferCommand("C-2", SOFIA, inventory, lot, "QUITO", "CUENCA", 10)).outcome());
        assertEquals(Outcome.EXECUTED,
                gateway.submit(new AdjustCommand("C-3", MATEO, inventory, lot, "QUITO", -1, "merma")).outcome());
        assertEquals(3, gateway.undoableCount());
    }

    // ---------------------------------------------------- cadena + receptor

    @Test
    void aCommandRejectedByTheChainNeverReachesTheReceiver() {
        ExecutionReport report = gateway.submit(reserve("C-1", 500));

        assertEquals(Outcome.REJECTED, report.outcome());
        assertEquals("Stock", report.rejectedBy());
        assertFalse(report.changedInventory());
        assertEquals(100, free());
        assertEquals(0, gateway.undoableCount());
        assertTrue(inventory.reservations().isEmpty());
    }

    @Test
    void aViolationRaisedByTheReceiverBecomesARejectionNotACrash() {
        // la cadena no revisa nombres repetidos: lo detecta el propio inventario
        ExecutionReport report = gateway.submit(
                new RegisterProductCommand("C-1", SOFIA, inventory, "laptop lenovo thinkpad e14"));

        assertEquals(Outcome.REJECTED, report.outcome());
        assertEquals("Inventario", report.rejectedBy());
        assertEquals(0, gateway.undoableCount());
    }

    @Test
    void humansAndAiAgentsGoThroughTheSameGate() {
        assertEquals(Outcome.EXECUTED,
                gateway.submit(new ReserveCommand("C-1", BOT, inventory, laptop, "QUITO", 20)).outcome());
        ExecutionReport tooMuch = gateway.submit(new ReserveCommand("C-2", BOT, inventory, laptop, "QUITO", 101));
        assertEquals("Autorización", tooMuch.rejectedBy());

        LedgerEntry entry = ledger.entries().get(0);
        assertEquals("pulse-ai", entry.actorId());
        assertEquals(ActorKind.AI_AGENT, entry.actorKind());
    }

    // --------------------------------------------------------- idempotencia

    @Test
    void retryingTheSameCommandDoesNotApplyItTwice() {
        gateway.submit(reserve("PED-1", 30));
        ExecutionReport retry = gateway.submit(reserve("PED-1", 30));

        assertEquals(Outcome.DUPLICATE, retry.outcome());
        assertFalse(retry.changedInventory());
        assertEquals(70, free());
        assertEquals(1, inventory.reservations().size());
        assertEquals(1, gateway.undoableCount()); // el duplicado no entra al historial
    }

    @Test
    void reusingAnIdWithDifferentContentIsRejectedNotSilentlyAccepted() {
        gateway.submit(reserve("PED-1", 30));
        ExecutionReport reused = gateway.submit(reserve("PED-1", 60));

        assertEquals(Outcome.REJECTED, reused.outcome());
        assertEquals("Idempotencia", reused.rejectedBy());
        assertEquals(70, free());
    }

    @Test
    void aRejectedAttemptDoesNotConsumeTheId() {
        assertEquals(Outcome.REJECTED, gateway.submit(reserve("PED-1", 150)).outcome());
        inventory.receiveLot(laptop, "QUITO", 100, null); // llega más mercadería

        ExecutionReport second = gateway.submit(reserve("PED-1", 150));
        assertEquals(Outcome.EXECUTED, second.outcome());
        assertEquals(50, free());
    }

    @Test
    void theSameContentWithAnotherIdIsADifferentOrder() {
        gateway.submit(reserve("PED-1", 30));
        assertEquals(Outcome.EXECUTED, gateway.submit(reserve("PED-2", 30)).outcome());
        assertEquals(40, free());
    }

    // -------------------------------------------------------------- deshacer

    @Test
    void undoCompensatesInLifoOrder() {
        gateway.submit(new TransferCommand("C-1", SOFIA, inventory, lot, "QUITO", "CUENCA", 10));
        gateway.submit(reserve("C-2", 30));

        ExecutionReport first = gateway.undoLast(SOFIA);
        assertEquals(Outcome.UNDONE, first.outcome());
        assertEquals("C-2", first.commandId());
        assertEquals(90, free());

        ExecutionReport second = gateway.undoLast(SOFIA);
        assertEquals("C-1", second.commandId());
        assertEquals(100, free());
        assertEquals(0, inventory.storedUnits("CUENCA"));
        assertEquals(0, gateway.undoableCount());
    }

    @Test
    void undoWithNothingToUndoIsReportedAndNotLogged() {
        int before = ledger.size();
        ExecutionReport report = gateway.undoLast(SOFIA);
        assertEquals(Outcome.REJECTED, report.outcome());
        assertEquals(before, ledger.size());
    }

    @Test
    void onlyTheAuthorOrASupervisorCanUndoACommand() {
        gateway.submit(reserve("C-1", 30));

        ExecutionReport byOther = gateway.undoLast(DIEGO);
        assertEquals(Outcome.REJECTED, byOther.outcome());
        assertEquals("Autorización", byOther.rejectedBy());
        assertEquals(70, free());
        assertEquals(1, gateway.undoableCount());

        assertEquals(Outcome.UNDONE, gateway.undoLast(MATEO).outcome());
        assertEquals(100, free());
    }

    @Test
    void theInverseOperationAlsoPassesTheChainAndAFailedUndoKeepsTheCommand() {
        gateway.submit(new TransferCommand("C-1", SOFIA, inventory, lot, "QUITO", "CUENCA", 10));
        inventory.quarantine(lot, "alerta de calidad"); // ya no se puede mover de regreso

        ExecutionReport failed = gateway.undoLast(SOFIA);
        assertEquals(Outcome.REJECTED, failed.outcome());
        assertEquals("Lote", failed.rejectedBy());
        assertEquals(10, inventory.storedUnits("CUENCA"));
        assertEquals("C-1", gateway.lastApplied().orElseThrow().id()); // no se perdió

        inventory.liftQuarantine(lot);
        assertEquals(Outcome.UNDONE, gateway.undoLast(SOFIA).outcome());
        assertEquals(0, inventory.storedUnits("CUENCA"));
    }

    @Test
    void undoingAQuarantineNeedsASupervisorBecauseItLiftsIt() {
        gateway.submit(QuarantineCommand.place("C-1", SOFIA, inventory, lot, "revisión"));

        ExecutionReport bySofia = gateway.undoLast(SOFIA);
        assertEquals("Autorización", bySofia.rejectedBy());
        assertTrue(inventory.lot(lot).orElseThrow().isQuarantined());

        assertEquals(Outcome.UNDONE, gateway.undoLast(MATEO).outcome());
        assertFalse(inventory.lot(lot).orElseThrow().isQuarantined());
    }

    @Test
    void undoingAReleasePutsTheSameReservationBack() {
        ReserveCommand reserve = reserve("C-1", 30);
        gateway.submit(reserve);
        Reservation reservation = reserve.reservation().orElseThrow();
        gateway.submit(new ReleaseCommand("C-2", SOFIA, inventory, reservation.id()));
        assertEquals(100, free());

        assertEquals(Outcome.UNDONE, gateway.undoLast(SOFIA).outcome());
        assertEquals(Reservation.Status.ACTIVE, reservation.status());
        assertEquals(70, free());
    }

    @Test
    void anUndoneCommandFreesItsIdSoItCanBeSentAgain() {
        gateway.submit(reserve("PED-1", 30));
        gateway.undoLast(SOFIA);

        ExecutionReport again = gateway.submit(reserve("PED-1", 30));
        assertEquals(Outcome.EXECUTED, again.outcome()); // no es un DUPLICADO falso
        assertEquals(70, free());
    }

    // ---------------------------------------------------------------- ledger

    @Test
    void everyAttemptIsWrittenToTheLedgerWithItsOutcome() {
        gateway.submit(reserve("PED-1", 30));          // ejecutado
        gateway.submit(reserve("PED-1", 30));          // duplicado
        gateway.submit(reserve("PED-1", 60));          // rechazado (idempotencia)
        gateway.submit(reserve("PED-2", 999));         // rechazado (stock)
        gateway.undoLast(SOFIA);                       // deshecho

        assertEquals(5, ledger.size());
        assertEquals(1, ledger.count(Outcome.EXECUTED));
        assertEquals(1, ledger.count(Outcome.DUPLICATE));
        assertEquals(2, ledger.count(Outcome.REJECTED));
        assertEquals(1, ledger.count(Outcome.UNDONE));
    }

    @Test
    void ledgerEntriesAreStructuredAndTimestampedByTheInjectedClock() {
        gateway.submit(reserve("PED-1", 30));
        LedgerEntry entry = ledger.entries().get(0);

        assertEquals(1, entry.sequence());
        assertEquals(Fixtures.NOW, entry.at());
        assertEquals("PED-1", entry.commandId());
        assertEquals("sofia", entry.actorId());
        assertEquals(ActorKind.HUMAN, entry.actorKind());
        assertEquals(OperationType.RESERVE, entry.operation());
        assertEquals(Outcome.EXECUTED, entry.outcome());
        assertEquals(reserve("x", 30).request().fingerprint(), entry.fingerprint());
        assertTrue(entry.summary().contains("reservar 30 u"));
    }

    @Test
    void theLedgerCannotBeEditedFromOutside() {
        gateway.submit(reserve("PED-1", 30));
        assertThrows(UnsupportedOperationException.class, () -> ledger.entries().clear());
    }

    @Test
    void aRejectionRecordsWhichLinkStoppedIt() {
        gateway.submit(reserve("PED-1", 999));
        LedgerEntry entry = ledger.entries().get(0);
        assertEquals(Outcome.REJECTED, entry.outcome());
        assertEquals("Stock", entry.rejectedBy());
    }
}
