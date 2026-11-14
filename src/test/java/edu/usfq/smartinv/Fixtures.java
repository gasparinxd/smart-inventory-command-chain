package edu.usfq.smartinv;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.actors.ActorKind;
import edu.usfq.smartinv.actors.Role;
import edu.usfq.smartinv.chain.AuthorizationHandler;
import edu.usfq.smartinv.chain.CapacityHandler;
import edu.usfq.smartinv.chain.Handler;
import edu.usfq.smartinv.chain.LotHandler;
import edu.usfq.smartinv.chain.SkuHandler;
import edu.usfq.smartinv.chain.StockHandler;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.ProductClassifier;
import edu.usfq.smartinv.inventory.Warehouse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Piezas comunes de las pruebas: reloj fijo, actores y un inventario pequeño. */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    public static final Actor SOFIA = new Actor("sofia", ActorKind.HUMAN, Role.OPERATOR);
    public static final Actor DIEGO = new Actor("diego", ActorKind.HUMAN, Role.OPERATOR);
    public static final Actor MATEO = new Actor("mateo", ActorKind.HUMAN, Role.SUPERVISOR);
    public static final Actor BOT = new Actor("pulse-ai", ActorKind.AI_AGENT, Role.OPERATOR);

    public static final int AGENT_LIMIT = 100;

    private Fixtures() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /** QUITO grande (1000 / 200) y CUENCA pequeña (100 / 30). */
    public static Inventory inventory(Clock clock) {
        return new Inventory(clock, new ProductClassifier(), List.of(
                new Warehouse("QUITO", "Quito", 1000, 200),
                new Warehouse("CUENCA", "Cuenca", 100, 30)));
    }

    /** La cadena completa: Autorización -> SKU -> Stock -> Lote -> Capacidad. */
    public static Handler chain(Inventory inventory) {
        Handler first = new AuthorizationHandler(inventory, AGENT_LIMIT);
        first.setNext(new SkuHandler(inventory))
                .setNext(new StockHandler(inventory))
                .setNext(new LotHandler(inventory))
                .setNext(new CapacityHandler(inventory));
        return first;
    }
}
