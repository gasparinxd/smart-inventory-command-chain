package edu.usfq.smartinv.chain;

import static edu.usfq.smartinv.Fixtures.BOT;
import static edu.usfq.smartinv.Fixtures.DIEGO;
import static edu.usfq.smartinv.Fixtures.MATEO;
import static edu.usfq.smartinv.Fixtures.SOFIA;
import static edu.usfq.smartinv.Fixtures.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usfq.smartinv.Fixtures;
import edu.usfq.smartinv.app.AdjustableClock;
import edu.usfq.smartinv.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La cadena sola, sin comandos: qué eslabón detiene cada solicitud y con qué motivo. */
class GuardrailChainTest {

    private AdjustableClock clock;
    private Inventory inventory;
    private Handler chain;
    private String laptop;
    private String yogurt;
    private String laptopLot;
    private String yogurtLot;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(Fixtures.clock());
        inventory = Fixtures.inventory(clock);
        chain = Fixtures.chain(inventory);
        laptop = inventory.registerProduct("Laptop Lenovo ThinkPad E14").sku();
        yogurt = inventory.registerProduct("Yogur griego natural 1 kg").sku();
        laptopLot = inventory.receiveLot(laptop, "QUITO", 40, null).id();
        yogurtLot = inventory.receiveLot(yogurt, "QUITO", 60, TODAY.plusDays(5)).id();
    }

    private void assertRejectedBy(String link, OperationRequest request) {
        Verdict verdict = chain.handle(request);
        assertFalse(verdict.approved(), "debía rechazarse: " + request.summary());
        assertEquals(link, verdict.rejectedBy(), verdict.reason());
        assertFalse(verdict.reason().isBlank());
    }

    private void assertApproved(OperationRequest request) {
        Verdict verdict = chain.handle(request);
        assertTrue(verdict.approved(), () -> request.summary() + " -> " + verdict.reason());
        assertNull(verdict.rejectedBy());
    }

    // ------------------------------------------------------------ estructura

    @Test
    void setNextReturnsTheNextHandlerSoLinksCanBeChained() {
        Handler first = new SkuHandler(inventory);
        Handler second = new StockHandler(inventory);
        assertSame(second, first.setNext(second));
    }

    @Test
    void aHandlerWithoutSuccessorApprovesWhatItDoesNotObjectTo() {
        Handler alone = new StockHandler(inventory);
        assertTrue(alone.handle(OperationRequest.reserve(SOFIA, laptop, "QUITO", 5)).approved());
        assertFalse(alone.handle(OperationRequest.reserve(SOFIA, laptop, "QUITO", 500)).approved());
    }

    @Test
    void linksRunInTheOrderTheyWereConnected() {
        List<String> visited = new ArrayList<>();
        Handler a = new Probe("a", visited);
        Handler b = new Probe("b", visited);
        Handler c = new Probe("c", visited);
        a.setNext(b).setNext(c);

        assertTrue(a.handle(OperationRequest.reserve(SOFIA, laptop, "QUITO", 1)).approved());
        assertEquals(List.of("a", "b", "c"), visited);
    }

    @Test
    void aRejectionStopsTheChain() {
        List<String> visited = new ArrayList<>();
        Handler probe = new Probe("después", visited);
        Handler sku = new SkuHandler(inventory);
        sku.setNext(probe);

        assertFalse(sku.handle(OperationRequest.reserve(SOFIA, "NOPE-XXX-001", "QUITO", 1)).approved());
        assertTrue(visited.isEmpty());
    }

    @Test
    void aNewLinkCanBeAddedWithoutTouchingTheExistingOnes() {
        // Eslabón nuevo: congela las operaciones sobre una bodega en inventario físico.
        Handler freeze = new BaseHandler() {
            @Override
            public Verdict handle(OperationRequest request) {
                if ("CUENCA".equals(request.warehouse())) {
                    return reject("CUENCA está en conteo físico");
                }
                return super.handle(request);
            }

            @Override
            protected String linkName() {
                return "Conteo";
            }
        };
        freeze.setNext(chain);

        assertEquals("Conteo", freeze.handle(OperationRequest.reserve(SOFIA, laptop, "CUENCA", 1)).rejectedBy());
        assertTrue(freeze.handle(OperationRequest.reserve(SOFIA, laptop, "QUITO", 1)).approved());
    }

    /** Eslabón de prueba que solo anota que pasó por él. */
    private static final class Probe extends BaseHandler {
        private final String name;
        private final List<String> visited;

        Probe(String name, List<String> visited) {
            this.name = name;
            this.visited = visited;
        }

        @Override
        public Verdict handle(OperationRequest request) {
            visited.add(name);
            return super.handle(request);
        }

        @Override
        protected String linkName() {
            return name;
        }
    }

    // ---------------------------------------------------------- autorización

    @Test
    void requestsWithoutAnActorAreRejected() {
        assertRejectedBy("Autorización", OperationRequest.reserve(null, laptop, "QUITO", 1));
    }

    @Test
    void onlySupervisorsAdjustStockOrLiftQuarantines() {
        assertRejectedBy("Autorización", OperationRequest.adjust(SOFIA, laptopLot, "QUITO", -1, "merma"));
        assertRejectedBy("Autorización", OperationRequest.liftQuarantine(SOFIA, laptopLot));
        assertApproved(OperationRequest.adjust(MATEO, laptopLot, "QUITO", -1, "merma"));
        assertApproved(OperationRequest.quarantine(SOFIA, laptopLot, "revisión"));
    }

    @Test
    void aiAgentsPassTheSameChainPlusAUnitLimit() {
        assertApproved(OperationRequest.reserve(BOT, yogurt, "QUITO", 10));
        inventory.receiveLot(laptop, "QUITO", 200, null);
        assertRejectedBy("Autorización", OperationRequest.reserve(BOT, laptop, "QUITO", Fixtures.AGENT_LIMIT + 1));
        assertApproved(OperationRequest.reserve(BOT, laptop, "QUITO", Fixtures.AGENT_LIMIT));
        // la misma cantidad pedida por una persona pasa
        assertApproved(OperationRequest.reserve(SOFIA, laptop, "QUITO", Fixtures.AGENT_LIMIT + 1));
    }

    @Test
    void onlyTheOwnerOrASupervisorActsOnAReservation() {
        String reservation = inventory.reserve(SOFIA.id(), laptop, "QUITO", 5).id();
        assertRejectedBy("Autorización", OperationRequest.release(DIEGO, reservation));
        assertRejectedBy("Autorización", OperationRequest.dispatch(BOT, reservation));
        assertRejectedBy("Autorización", OperationRequest.release(SOFIA, "RSV-9999"));
        assertApproved(OperationRequest.release(SOFIA, reservation));
        assertApproved(OperationRequest.release(MATEO, reservation));
    }

    // -------------------------------------------------------------- sku/stock

    @Test
    void unknownSkuIsRejectedBySkuLink() {
        assertRejectedBy("SKU", OperationRequest.reserve(SOFIA, "ELEC-TV-999", "QUITO", 1));
        assertRejectedBy("SKU", OperationRequest.receive(SOFIA, "ELEC-TV-999", "QUITO", 1, null));
        assertRejectedBy("SKU", OperationRequest.retireProduct(SOFIA, null));
    }

    @Test
    void quantitiesMustMakeSense() {
        assertRejectedBy("Stock", OperationRequest.reserve(SOFIA, laptop, "QUITO", 0));
        assertRejectedBy("Stock", OperationRequest.reserve(SOFIA, laptop, "QUITO", -5));
        assertRejectedBy("Stock", OperationRequest.transfer(SOFIA, laptopLot, "QUITO", "CUENCA", 0));
        assertRejectedBy("Stock", OperationRequest.adjust(MATEO, laptopLot, "QUITO", 0, "nada"));
    }

    @Test
    void whatLeavesMustPhysicallyExist() {
        assertRejectedBy("Stock", OperationRequest.reserve(SOFIA, laptop, "QUITO", 41));
        assertRejectedBy("Stock", OperationRequest.reserve(SOFIA, laptop, "CUENCA", 1));
        assertRejectedBy("Stock", OperationRequest.reserve(SOFIA, laptop, "LOJA", 1));
        assertRejectedBy("Stock", OperationRequest.transfer(SOFIA, laptopLot, "QUITO", "CUENCA", 41));
        assertRejectedBy("Stock", OperationRequest.adjust(MATEO, laptopLot, "QUITO", -41, "merma"));
        assertApproved(OperationRequest.reserve(SOFIA, laptop, "QUITO", 40));
    }

    @Test
    void reservedUnitsAreNotAvailableForOtherOperations() {
        inventory.reserve(SOFIA.id(), laptop, "QUITO", 30);
        assertRejectedBy("Stock", OperationRequest.reserve(DIEGO, laptop, "QUITO", 11));
        assertRejectedBy("Stock", OperationRequest.transfer(DIEGO, laptopLot, "QUITO", "CUENCA", 11));
        assertApproved(OperationRequest.reserve(DIEGO, laptop, "QUITO", 10));
    }

    // ------------------------------------------------------------------ lote

    @Test
    void lotMustExist() {
        assertRejectedBy("Lote", OperationRequest.transfer(SOFIA, "L-9999", "QUITO", "CUENCA", 1));
        assertRejectedBy("Lote", OperationRequest.adjust(MATEO, "L-9999", "QUITO", -1, "merma"));
        assertRejectedBy("Lote", OperationRequest.quarantine(SOFIA, "L-9999", "revisión"));
        assertRejectedBy("Lote", OperationRequest.cancelReceipt(SOFIA, "L-9999"));
    }

    @Test
    void quarantinedStockIsThereButCannotBeReservedOrMoved() {
        inventory.quarantine(yogurtLot, "cadena de frío rota");
        Verdict verdict = chain.handle(OperationRequest.reserve(SOFIA, yogurt, "QUITO", 10));
        assertEquals("Lote", verdict.rejectedBy());
        assertTrue(verdict.reason().contains("60"), verdict.reason()); // explica que físicamente sí hay
        assertRejectedBy("Lote", OperationRequest.transfer(SOFIA, yogurtLot, "QUITO", "CUENCA", 10));
    }

    @Test
    void expiredStockCannotBeReservedMovedOrDispatched() {
        String reservation = inventory.reserve(SOFIA.id(), yogurt, "QUITO", 20).id();
        clock.advanceDays(6);
        assertRejectedBy("Lote", OperationRequest.reserve(SOFIA, yogurt, "QUITO", 10));
        assertRejectedBy("Lote", OperationRequest.transfer(SOFIA, yogurtLot, "QUITO", "CUENCA", 10));
        assertRejectedBy("Lote", OperationRequest.dispatch(SOFIA, reservation));
        // pero sí se puede dar de baja como merma
        assertApproved(OperationRequest.adjust(MATEO, yogurtLot, "QUITO", -40, "vencido"));
    }

    @Test
    void receivingChecksTheExpiryDate() {
        assertRejectedBy("Lote", OperationRequest.receive(SOFIA, yogurt, "QUITO", 10, null));
        assertRejectedBy("Lote", OperationRequest.receive(SOFIA, yogurt, "QUITO", 10, TODAY.minusDays(1)));
        assertApproved(OperationRequest.receive(SOFIA, yogurt, "QUITO", 10, TODAY.plusDays(30)));
        assertApproved(OperationRequest.receive(SOFIA, laptop, "QUITO", 10, null));
    }

    // ------------------------------------------------------------- capacidad

    @Test
    void arrivalsNeedStorageRoom() {
        assertRejectedBy("Capacidad", OperationRequest.receive(SOFIA, laptop, "CUENCA", 101, null));
        assertRejectedBy("Capacidad", OperationRequest.receive(SOFIA, laptop, "LOJA", 1, null));
        inventory.receiveLot(laptop, "CUENCA", 90, null);
        assertRejectedBy("Capacidad", OperationRequest.transfer(SOFIA, laptopLot, "QUITO", "CUENCA", 11));
        assertRejectedBy("Capacidad", OperationRequest.transfer(SOFIA, laptopLot, "QUITO", "QUITO", 5));
        assertApproved(OperationRequest.transfer(SOFIA, laptopLot, "QUITO", "CUENCA", 10));
    }

    @Test
    void positiveAdjustmentsAlsoNeedRoomButNegativeOnesDoNot() {
        String cuencaLot = inventory.receiveLot(laptop, "CUENCA", 100, null).id();
        assertRejectedBy("Capacidad", OperationRequest.adjust(MATEO, cuencaLot, "CUENCA", 1, "hallazgo"));
        assertApproved(OperationRequest.adjust(MATEO, cuencaLot, "CUENCA", -1, "merma"));
    }

    @Test
    void reservationsNeedRoomInTheStagingArea() {
        inventory.receiveLot(laptop, "CUENCA", 50, null);
        inventory.reserve(SOFIA.id(), laptop, "CUENCA", 25);
        assertRejectedBy("Capacidad", OperationRequest.reserve(SOFIA, laptop, "CUENCA", 6));
        assertApproved(OperationRequest.reserve(SOFIA, laptop, "CUENCA", 5));
    }

    // ------------------------------------------------------------- orden

    @Test
    void whenSeveralThingsAreWrongTheEarliestLinkAnswers() {
        // operador + SKU inexistente + sin stock: responde SKU, que está antes que Stock
        assertRejectedBy("SKU", OperationRequest.reserve(SOFIA, "ELEC-TV-999", "LOJA", 9999));
        // agente sobre el tope + SKU inexistente: responde Autorización, que es el primero
        assertRejectedBy("Autorización", OperationRequest.reserve(BOT, "ELEC-TV-999", "QUITO", 9999));
    }

    @Test
    void operationsOnlyTriggerTheChecksTheyDeclare() {
        // registrar un producto no necesita SKU, stock, lote ni capacidad
        assertApproved(OperationRequest.registerProduct(SOFIA, "Monitor LG 27"));
        // una cuarentena no revisa stock ni capacidad, solo que el lote exista
        assertApproved(OperationRequest.quarantine(SOFIA, yogurtLot, "revisión"));
    }
}
