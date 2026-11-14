package edu.usfq.smartinv.app;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.actors.ActorKind;
import edu.usfq.smartinv.actors.Role;
import edu.usfq.smartinv.chain.AuthorizationHandler;
import edu.usfq.smartinv.chain.CapacityHandler;
import edu.usfq.smartinv.chain.Handler;
import edu.usfq.smartinv.chain.LotHandler;
import edu.usfq.smartinv.chain.SkuHandler;
import edu.usfq.smartinv.chain.StockHandler;
import edu.usfq.smartinv.gateway.Ledger;
import edu.usfq.smartinv.gateway.OperationsGateway;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.ProductClassifier;
import edu.usfq.smartinv.inventory.Warehouse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raíz de composición: el único lugar donde se crean las piezas concretas y se conectan.
 * Hace de Client en los dos patrones: enlaza los eslabones de la cadena y le entrega
 * la cabeza al invoker. El resto del código solo ve interfaces.
 */
public final class Bootstrap {

    /** Tope de unidades por operación para los agentes de IA. */
    public static final int AGENT_UNIT_LIMIT = 100;

    private final Clock baseClock;
    private final AdjustableClock clock;
    private final Inventory inventory;
    private final Handler guardrails;
    private final Ledger ledger;
    private final OperationsGateway gateway;
    private final Map<String, Actor> actors = new LinkedHashMap<>();

    private Bootstrap(Clock baseClock) {
        this.baseClock = baseClock;
        this.clock = new AdjustableClock(baseClock);
        this.inventory = new Inventory(clock, new ProductClassifier(), List.of(
                new Warehouse("QUITO", "Centro de distribución Quito", 1500, 300),
                new Warehouse("GUAYAQUIL", "Centro de distribución Guayaquil", 1000, 200),
                new Warehouse("CUENCA", "Bodega satélite Cuenca", 120, 40)));
        this.guardrails = linkGuardrails(inventory);
        this.ledger = new Ledger(clock);
        this.gateway = new OperationsGateway(guardrails, ledger);

        addActor(new Actor("sofia", ActorKind.HUMAN, Role.OPERATOR));
        addActor(new Actor("mateo", ActorKind.HUMAN, Role.SUPERVISOR));
        addActor(new Actor("pulse-ai", ActorKind.AI_AGENT, Role.OPERATOR));
        addActor(new Actor("tienda-web", ActorKind.API, Role.OPERATOR));
    }

    /** Autorización -> SKU -> Stock -> Lote -> Capacidad. */
    private static Handler linkGuardrails(Inventory inventory) {
        Handler authorization = new AuthorizationHandler(inventory, AGENT_UNIT_LIMIT);
        Handler sku = new SkuHandler(inventory);
        Handler stock = new StockHandler(inventory);
        Handler lot = new LotHandler(inventory);
        Handler capacity = new CapacityHandler(inventory);

        authorization.setNext(sku);
        sku.setNext(stock);
        stock.setNext(lot);
        lot.setNext(capacity);
        return authorization;
    }

    /** Sistema vacío: bodegas y actores, sin productos ni lotes. */
    public static Bootstrap empty(Clock baseClock) {
        return new Bootstrap(baseClock);
    }

    /** Sistema con el catálogo y los lotes de ejemplo ya cargados. */
    public static Bootstrap withSampleData(Clock baseClock) {
        Bootstrap app = new Bootstrap(baseClock);
        SeedData.load(app);
        return app;
    }

    /** Otro sistema de ejemplo, independiente de este, con el mismo reloj de partida. */
    public Bootstrap freshCopy() {
        return withSampleData(baseClock);
    }

    public AdjustableClock clock() {
        return clock;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Handler guardrails() {
        return guardrails;
    }

    public Ledger ledger() {
        return ledger;
    }

    public OperationsGateway gateway() {
        return gateway;
    }

    public Optional<Actor> actor(String id) {
        return Optional.ofNullable(actors.get(id));
    }

    public List<Actor> actors() {
        return new ArrayList<>(actors.values());
    }

    private void addActor(Actor actor) {
        actors.put(actor.id(), actor);
    }
}
