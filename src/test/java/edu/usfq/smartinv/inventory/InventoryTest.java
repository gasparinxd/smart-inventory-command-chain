package edu.usfq.smartinv.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.app.AdjustableClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El receptor llamado directamente, sin invoker ni cadena: sus reglas se sostienen solas.
 */
class InventoryTest {

    private AdjustableClock clock;
    private Inventory inventory;
    private String laptop;
    private String yogurt;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(Fixtures.clock());
        inventory = Fixtures.inventory(clock);
        laptop = inventory.registerProduct("Laptop Lenovo ThinkPad E14").sku();
        yogurt = inventory.registerProduct("Yogur griego natural 1 kg").sku();
    }

    @Nested
    class Catalog {

        @Test
        void skuCombinesFamilyMnemonicAndSequence() {
            assertEquals("ELEC-LAP-001", laptop);
            assertEquals("REFR-YOG-001", yogurt);
            assertEquals("ELEC-CEL-002", inventory.registerProduct("Celular Samsung A15").sku());
            assertEquals("GENE-SIL-001", inventory.registerProduct("Silla plegable").sku());
        }

        @Test
        void sameNameIsNotRegisteredTwice() {
            InventoryRuleViolation error = assertThrows(InventoryRuleViolation.class,
                    () -> inventory.registerProduct("  laptop   LENOVO thinkpad e14 "));
            assertTrue(error.getMessage().contains("ELEC-LAP-001"));
            assertEquals(2, inventory.products().size());
        }

        @Test
        void blankNameIsRejected() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.registerProduct("   "));
        }

        @Test
        void aProductWithLotsCannotBeRetired() {
            inventory.receiveLot(laptop, "QUITO", 5, null);
            assertThrows(InventoryRuleViolation.class, () -> inventory.retireProduct(laptop));
            inventory.retireProduct(yogurt);
            assertTrue(inventory.product(yogurt).isEmpty());
        }
    }

    @Nested
    class Receiving {

        @Test
        void createsALotWithItsStock() {
            Lot lot = inventory.receiveLot(laptop, "QUITO", 40, null);
            assertEquals("L-0001", lot.id());
            assertEquals(40, inventory.unreservedUnitsOfLot("L-0001", "QUITO"));
            assertEquals(40, inventory.storedUnits("QUITO"));
        }

        @Test
        void perishablesNeedAnExpiryDate() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.receiveLot(yogurt, "QUITO", 10, null));
        }

        @Test
        void alreadyExpiredLotsAreNotReceived() {
            assertThrows(InventoryRuleViolation.class,
                    () -> inventory.receiveLot(yogurt, "QUITO", 10, Fixtures.TODAY.minusDays(1)));
            inventory.receiveLot(yogurt, "QUITO", 10, Fixtures.TODAY); // vence hoy: todavía sirve
        }

        @Test
        void storageCapacityIsRespected() {
            inventory.receiveLot(laptop, "CUENCA", 90, null);
            assertThrows(InventoryRuleViolation.class, () -> inventory.receiveLot(laptop, "CUENCA", 11, null));
            inventory.receiveLot(laptop, "CUENCA", 10, null);
            assertEquals(100, inventory.storedUnits("CUENCA"));
        }

        @Test
        void unknownSkuWarehouseOrBadQuantityAreRejected() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.receiveLot("NOPE-XXX-001", "QUITO", 1, null));
            assertThrows(InventoryRuleViolation.class, () -> inventory.receiveLot(laptop, "LOJA", 1, null));
            assertThrows(InventoryRuleViolation.class, () -> inventory.receiveLot(laptop, "QUITO", 0, null));
            assertTrue(inventory.stockLines().isEmpty());
        }

        @Test
        void aReceiptCanOnlyBeCancelledWhileTheLotIsUntouched() {
            Lot untouched = inventory.receiveLot(laptop, "QUITO", 10, null);
            Lot moved = inventory.receiveLot(laptop, "QUITO", 10, null);
            inventory.transfer(moved.id(), "QUITO", "CUENCA", 4);

            inventory.cancelReceipt(untouched.id());
            assertTrue(inventory.lot(untouched.id()).isEmpty());
            assertThrows(InventoryRuleViolation.class, () -> inventory.cancelReceipt(moved.id()));
        }
    }

    @Nested
    class Reservations {

        @BeforeEach
        void stock() {
            inventory.receiveLot(yogurt, "QUITO", 60, Fixtures.TODAY.plusDays(20)); // L-0001
            inventory.receiveLot(yogurt, "QUITO", 50, Fixtures.TODAY.plusDays(5));  // L-0002 vence antes
        }

        @Test
        void aReservationIsSplitAcrossLotsEarliestExpiryFirst() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 70);
            assertEquals(List.of(new Reservation.Line("L-0002", 50), new Reservation.Line("L-0001", 20)),
                    reservation.lines());
            assertEquals(70, reservation.units());
            assertEquals(40, inventory.sellableUnits(yogurt, "QUITO"));
            assertEquals(70, inventory.stagedUnits("QUITO"));
        }

        @Test
        void quarantinedAndExpiredLotsAreSkipped() {
            inventory.quarantine("L-0002", "empaque inflado");
            Reservation first = inventory.reserve("sofia", yogurt, "QUITO", 10);
            assertEquals(List.of(new Reservation.Line("L-0001", 10)), first.lines());

            inventory.liftQuarantine("L-0002");
            clock.advanceDays(6); // L-0002 venció
            assertEquals(50, inventory.sellableUnits(yogurt, "QUITO"));
            assertEquals(100, inventory.unreservedUnits(yogurt, "QUITO"));
            Reservation second = inventory.reserve("sofia", yogurt, "QUITO", 10);
            assertEquals("L-0001", second.lines().get(0).lotId());
        }

        @Test
        void askingForMoreThanIsSellableChangesNothing() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.reserve("sofia", yogurt, "QUITO", 111));
            assertEquals(110, inventory.sellableUnits(yogurt, "QUITO"));
            assertTrue(inventory.reservations().isEmpty());
        }

        @Test
        void stagingCapacityLimitsWhatCanBeReservedAtOnce() {
            inventory.receiveLot(laptop, "CUENCA", 50, null);
            inventory.reserve("sofia", laptop, "CUENCA", 25);
            assertThrows(InventoryRuleViolation.class, () -> inventory.reserve("sofia", laptop, "CUENCA", 6));
            inventory.reserve("sofia", laptop, "CUENCA", 5);
            assertEquals(30, inventory.stagedUnits("CUENCA"));
        }

        @Test
        void releaseAndReinstateAreExactInverses() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 70);
            inventory.release(reservation.id());
            assertEquals(Reservation.Status.RELEASED, reservation.status());
            assertEquals(110, inventory.sellableUnits(yogurt, "QUITO"));

            inventory.reinstate(reservation.id());
            assertEquals(Reservation.Status.ACTIVE, reservation.status());
            assertEquals(40, inventory.sellableUnits(yogurt, "QUITO"));
        }

        @Test
        void aReservationCannotBeReleasedTwice() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 10);
            inventory.release(reservation.id());
            assertThrows(InventoryRuleViolation.class, () -> inventory.release(reservation.id()));
            assertEquals(110, inventory.unreservedUnits(yogurt, "QUITO"));
        }

        @Test
        void reinstateFailsIfTheUnitsWereTakenMeanwhile() {
            Reservation first = inventory.reserve("sofia", yogurt, "QUITO", 50); // todo L-0002
            inventory.release(first.id());
            inventory.reserve("diego", yogurt, "QUITO", 30); // toma 30 de L-0002
            assertThrows(InventoryRuleViolation.class, () -> inventory.reinstate(first.id()));
            assertEquals(Reservation.Status.RELEASED, first.status());
        }

        @Test
        void dispatchTakesTheUnitsOutAndLeavesNoEmptyPositions() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 50); // vacía L-0002
            inventory.dispatch(reservation.id());

            assertEquals(Reservation.Status.DISPATCHED, reservation.status());
            assertEquals(60, inventory.storedUnits("QUITO"));
            assertEquals(0, inventory.stagedUnits("QUITO"));
            assertTrue(inventory.stockLines().stream().noneMatch(line -> line.onHand() == 0));
        }

        @Test
        void aReservationWithAnExpiredLotCannotBeDispatched() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 50);
            clock.advanceDays(6);
            assertThrows(InventoryRuleViolation.class, () -> inventory.dispatch(reservation.id()));
            assertEquals(Reservation.Status.ACTIVE, reservation.status());
            assertEquals(110, inventory.storedUnits("QUITO"));
        }

        @Test
        void aReturnBringsTheDispatchedUnitsBack() {
            Reservation reservation = inventory.reserve("sofia", yogurt, "QUITO", 50);
            inventory.dispatch(reservation.id());
            inventory.acceptReturn(reservation.id());

            assertEquals(Reservation.Status.ACTIVE, reservation.status());
            assertEquals(110, inventory.storedUnits("QUITO"));
            assertEquals(50, inventory.stagedUnits("QUITO"));
        }

        @Test
        void aReservationNeedsAnOwner() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.reserve(" ", yogurt, "QUITO", 1));
        }
    }

    @Nested
    class AdjustmentsAndTransfers {

        private String lot;

        @BeforeEach
        void stock() {
            lot = inventory.receiveLot(laptop, "QUITO", 40, null).id();
        }

        @Test
        void adjustmentsAddAndRemoveUnits() {
            inventory.adjust(lot, "QUITO", -3);
            assertEquals(37, inventory.storedUnits("QUITO"));
            inventory.adjust(lot, "QUITO", 5);
            assertEquals(42, inventory.storedUnits("QUITO"));
        }

        @Test
        void stockNeverGoesNegative() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.adjust(lot, "QUITO", -9999));
            assertEquals(40, inventory.storedUnits("QUITO"));
        }

        @Test
        void anAdjustmentCannotEatReservedUnits() {
            inventory.reserve("sofia", laptop, "QUITO", 30);
            assertThrows(InventoryRuleViolation.class, () -> inventory.adjust(lot, "QUITO", -11));
            inventory.adjust(lot, "QUITO", -10);
            assertEquals(30, inventory.storedUnits("QUITO"));
            assertEquals(30, inventory.stagedUnits("QUITO"));
        }

        @Test
        void zeroAdjustmentIsRejected() {
            assertThrows(InventoryRuleViolation.class, () -> inventory.adjust(lot, "QUITO", 0));
        }

        @Test
        void transferMovesUnitsAndLeavesNoGhostPosition() {
            inventory.transfer(lot, "QUITO", "CUENCA", 40);
            assertEquals(0, inventory.storedUnits("QUITO"));
            assertEquals(40, inventory.storedUnits("CUENCA"));
            assertEquals(1, inventory.stockLines().size());

            inventory.transfer(lot, "CUENCA", "QUITO", 40); // ida y vuelta: todo igual que al inicio
            assertEquals(40, inventory.unreservedUnitsOfLot(lot, "QUITO"));
            assertEquals(1, inventory.stockLines().size());
        }

        @Test
        void transferChecksDestinationCapacityAndSameWarehouse() {
            inventory.receiveLot(laptop, "CUENCA", 80, null);
            assertThrows(InventoryRuleViolation.class, () -> inventory.transfer(lot, "QUITO", "CUENCA", 21));
            assertThrows(InventoryRuleViolation.class, () -> inventory.transfer(lot, "QUITO", "QUITO", 1));
            assertEquals(40, inventory.unreservedUnitsOfLot(lot, "QUITO"));
        }

        @Test
        void quarantinedLotsDoNotMoveAndCannotBeQuarantinedTwice() {
            inventory.quarantine(lot, "golpe en el transporte");
            assertTrue(inventory.lot(lot).orElseThrow().isQuarantined());
            assertThrows(InventoryRuleViolation.class, () -> inventory.transfer(lot, "QUITO", "CUENCA", 1));
            assertThrows(InventoryRuleViolation.class, () -> inventory.quarantine(lot, "otra vez"));

            assertEquals("golpe en el transporte", inventory.liftQuarantine(lot));
            assertFalse(inventory.lot(lot).orElseThrow().isQuarantined());
            assertThrows(InventoryRuleViolation.class, () -> inventory.liftQuarantine(lot));
        }

        @Test
        void aLotOnlyHoldsItsOwnSku() {
            // los lotes son globales y de un solo producto: no hay forma de mezclar SKU en un mismo lote
            String other = inventory.receiveLot(yogurt, "CUENCA", 10, Fixtures.TODAY.plusDays(9)).id();
            inventory.transfer(lot, "QUITO", "CUENCA", 5);
            assertEquals(5, inventory.unreservedUnits(laptop, "CUENCA"));
            assertEquals(10, inventory.unreservedUnits(yogurt, "CUENCA"));
            assertEquals(yogurt, inventory.lot(other).orElseThrow().sku());
        }
    }
}
