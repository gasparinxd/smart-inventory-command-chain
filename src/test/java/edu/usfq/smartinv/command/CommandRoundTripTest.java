package edu.usfq.smartinv.command;

import static edu.usfq.smartinv.Fixtures.MATEO;
import static edu.usfq.smartinv.Fixtures.SOFIA;
import static edu.usfq.smartinv.Fixtures.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.chain.OperationType;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.Product;
import edu.usfq.smartinv.inventory.Reservation;
import edu.usfq.smartinv.inventory.StockLine;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cada comando, ejecutado y deshecho directamente sobre el receptor:
 * execute() cambia el inventario y undo() lo deja exactamente como estaba, sin residuos.
 */
class CommandRoundTripTest {

    /** Todo lo observable del inventario, para comparar antes y después. */
    private record Snapshot(List<Product> products, List<StockLine> stock, List<String> reservations) {
    }

    private static Inventory inventory;
    private static String yogurt;
    private static String lot;
    private static String reservation;

    @BeforeEach
    void setUp() {
        inventory = Fixtures.inventory(Fixtures.clock());
        inventory.registerProduct("Laptop Lenovo ThinkPad E14");
        yogurt = inventory.registerProduct("Yogur griego natural 1 kg").sku();
        lot = inventory.receiveLot(yogurt, "QUITO", 60, TODAY.plusDays(10)).id();
        inventory.receiveLot(yogurt, "QUITO", 40, TODAY.plusDays(3));
        reservation = inventory.reserve("sofia", yogurt, "QUITO", 50).id();
    }

    /**
     * Solo cuentan las reservas activas: deshacer un Reserve es compensarlo con un Release,
     * así que la reserva no se borra, queda en el registro como liberada.
     */
    private static Snapshot snapshot() {
        return new Snapshot(inventory.products(), inventory.stockLines(),
                inventory.reservations().stream()
                        .filter(r -> r.status() == Reservation.Status.ACTIVE)
                        .map(r -> r.id() + ":" + r.breakdown())
                        .toList());
    }

    static Stream<Supplier<InventoryCommand>> commands() {
        return Stream.of(
                () -> new RegisterProductCommand("T", SOFIA, inventory, "Monitor LG 27"),
                () -> new ReceiveLotCommand("T", SOFIA, inventory, yogurt, "CUENCA", 20, TODAY.plusDays(15)),
                () -> new ReserveCommand("T", SOFIA, inventory, yogurt, "QUITO", 45),
                () -> new ReleaseCommand("T", SOFIA, inventory, reservation),
                () -> new DispatchCommand("T", SOFIA, inventory, reservation),
                () -> new AdjustCommand("T", MATEO, inventory, lot, "QUITO", -7, "merma"),
                () -> new AdjustCommand("T", MATEO, inventory, lot, "QUITO", 7, "hallazgo"),
                () -> new TransferCommand("T", SOFIA, inventory, lot, "QUITO", "CUENCA", 25),
                () -> QuarantineCommand.place("T", SOFIA, inventory, lot, "revisión"));
    }

    @ParameterizedTest
    @MethodSource("commands")
    void executeChangesTheInventoryAndUndoRestoresItExactly(Supplier<InventoryCommand> factory) {
        InventoryCommand command = factory.get();
        Snapshot before = snapshot();

        command.execute();
        assertNotEquals(before, snapshot(), "execute() debía cambiar algo");

        command.undo();
        assertEquals(before, snapshot(), "undo() debía dejar todo como estaba");
    }

    @ParameterizedTest
    @MethodSource("commands")
    void theRequestDoesNotChangeWhenTheCommandRuns(Supplier<InventoryCommand> factory) {
        InventoryCommand command = factory.get();
        String before = command.request().fingerprint();
        command.execute();
        assertEquals(before, command.request().fingerprint());
    }

    @Test
    void liftingAQuarantineRemembersTheReasonToRestoreIt() {
        inventory.quarantine(lot, "cadena de frío rota");
        QuarantineCommand lift = QuarantineCommand.lift("T", MATEO, inventory, lot);

        lift.execute();
        lift.undo();
        assertEquals("cadena de frío rota", inventory.lot(lot).orElseThrow().quarantineReason().orElseThrow());
    }

    @Test
    void eachCommandDescribesItsInverseAsARequest() {
        ReserveCommand reserve = new ReserveCommand("T", SOFIA, inventory, yogurt, "QUITO", 10);
        reserve.execute();
        assertEquals(OperationType.RELEASE, reserve.compensation(MATEO).type());
        assertEquals(reserve.reservation().orElseThrow().id(), reserve.compensation(MATEO).reservationId());
        assertEquals(MATEO, reserve.compensation(MATEO).actor());

        TransferCommand transfer = new TransferCommand("T", SOFIA, inventory, lot, "QUITO", "CUENCA", 5);
        assertEquals("CUENCA", transfer.compensation(SOFIA).warehouse());
        assertEquals("QUITO", transfer.compensation(SOFIA).targetWarehouse());

        AdjustCommand adjust = new AdjustCommand("T", MATEO, inventory, lot, "QUITO", -7, "merma");
        assertEquals(7, adjust.compensation(MATEO).quantity());

        DispatchCommand dispatch = new DispatchCommand("T", SOFIA, inventory, reservation);
        dispatch.execute();
        assertEquals(OperationType.RETURN, dispatch.compensation(SOFIA).type());
        assertEquals(50, dispatch.compensation(SOFIA).quantity());
    }

    @Test
    void undoingAReserveCompensatesItAndKeepsTheRecord() {
        ReserveCommand reserve = new ReserveCommand("T", SOFIA, inventory, yogurt, "QUITO", 45);
        reserve.execute();
        reserve.undo();
        assertEquals(Reservation.Status.RELEASED, reserve.reservation().orElseThrow().status());
        assertEquals(2, inventory.reservations().size());
    }

    @Test
    void aCommandThatHasNotRunCannotBeUndone() {
        ReserveCommand reserve = new ReserveCommand("T", SOFIA, inventory, yogurt, "QUITO", 10);
        assertThrows(IllegalStateException.class, reserve::undo);
        assertThrows(IllegalStateException.class, reserve::outcome);
    }

    @Test
    void aReservationKeepsItsOwnerWhenASupervisorExecutesTheCommandForSomeoneElse() {
        ReserveCommand reserve = new ReserveCommand("T", SOFIA, inventory, yogurt, "QUITO", 10);
        reserve.execute();
        Reservation made = reserve.reservation().orElseThrow();
        assertEquals("sofia", made.ownerId());
    }
}
