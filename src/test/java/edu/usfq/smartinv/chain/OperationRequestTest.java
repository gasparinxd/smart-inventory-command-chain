package edu.usfq.smartinv.chain;

import static edu.usfq.smartinv.Fixtures.MATEO;
import static edu.usfq.smartinv.Fixtures.SOFIA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperationRequestTest {

    @Test
    void sameContentGivesTheSameFingerprint() {
        assertEquals(OperationRequest.reserve(SOFIA, "ELEC-LAP-001", "QUITO", 50).fingerprint(),
                OperationRequest.reserve(SOFIA, "ELEC-LAP-001", "QUITO", 50).fingerprint());
    }

    @Test
    void anyChangeInTheContentChangesTheFingerprint() {
        String base = OperationRequest.reserve(SOFIA, "ELEC-LAP-001", "QUITO", 50).fingerprint();
        assertNotEquals(base, OperationRequest.reserve(SOFIA, "ELEC-LAP-001", "QUITO", 150).fingerprint());
        assertNotEquals(base, OperationRequest.reserve(SOFIA, "ELEC-LAP-001", "CUENCA", 50).fingerprint());
        assertNotEquals(base, OperationRequest.reserve(SOFIA, "ELEC-LAP-002", "QUITO", 50).fingerprint());
        assertNotEquals(base, OperationRequest.reserve(MATEO, "ELEC-LAP-001", "QUITO", 50).fingerprint());
    }

    @Test
    void theDirectionOfATransferIsPartOfTheFingerprint() {
        assertNotEquals(OperationRequest.transfer(SOFIA, "L-0001", "QUITO", "CUENCA", 5).fingerprint(),
                OperationRequest.transfer(SOFIA, "L-0001", "CUENCA", "QUITO", 5).fingerprint());
    }

    @Test
    void aNegativeAdjustmentTakesUnitsOutAndAPositiveOneBringsThemIn() {
        OperationRequest loss = OperationRequest.adjust(MATEO, "L-0001", "QUITO", -7, "merma");
        assertEquals(7, loss.unitsLeaving());
        assertEquals(0, loss.unitsArriving());

        OperationRequest found = OperationRequest.adjust(MATEO, "L-0001", "QUITO", 7, "hallazgo");
        assertEquals(0, found.unitsLeaving());
        assertEquals(7, found.unitsArriving());
        assertEquals("QUITO", found.arrivalWarehouse());
    }

    @Test
    void aTransferLeavesOneWarehouseAndArrivesAtAnother() {
        OperationRequest transfer = OperationRequest.transfer(SOFIA, "L-0001", "QUITO", "CUENCA", 12);
        assertEquals(12, transfer.unitsLeaving());
        assertEquals(12, transfer.unitsArriving());
        assertEquals("QUITO", transfer.warehouse());
        assertEquals("CUENCA", transfer.arrivalWarehouse());
    }

    @Test
    void eachOperationDeclaresItsOwnChecks() {
        assertTrue(OperationType.ADJUST.needs(Need.SUPERVISOR));
        assertFalse(OperationType.RESERVE.needs(Need.SUPERVISOR));
        assertTrue(OperationType.RESERVE.needs(Need.STAGING_ROOM));
        assertTrue(OperationType.DISPATCH.needs(Need.HEALTHY_LOT));
        assertFalse(OperationType.ADJUST.needs(Need.HEALTHY_LOT)); // una merma puede ser de un lote vencido
        for (Need need : Need.values()) {
            assertFalse(OperationType.REGISTER_PRODUCT.needs(need));
        }
    }

    @Test
    void theSummaryIsReadable() {
        assertEquals("ajustar -7 u del lote L-0001 en QUITO (merma)",
                OperationRequest.adjust(MATEO, "L-0001", "QUITO", -7, "merma").summary());
        assertEquals("transferir 12 u del lote L-0001 de QUITO a CUENCA",
                OperationRequest.transfer(SOFIA, "L-0001", "QUITO", "CUENCA", 12).summary());
    }
}
