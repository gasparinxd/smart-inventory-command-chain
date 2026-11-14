package edu.usfq.smartinv.app;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.command.AdjustCommand;
import edu.usfq.smartinv.command.DispatchCommand;
import edu.usfq.smartinv.command.InventoryCommand;
import edu.usfq.smartinv.command.QuarantineCommand;
import edu.usfq.smartinv.command.ReceiveLotCommand;
import edu.usfq.smartinv.command.RegisterProductCommand;
import edu.usfq.smartinv.command.ReleaseCommand;
import edu.usfq.smartinv.command.ReserveCommand;
import edu.usfq.smartinv.command.TransferCommand;
import edu.usfq.smartinv.gateway.ExecutionReport;
import edu.usfq.smartinv.gateway.OperationsGateway;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.InventoryRuleViolation;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Recorrido guiado por todo lo que hace el sistema, sobre los datos de ejemplo.
 * Hace de Client del patrón Command: crea los comandos y se los entrega al invoker.
 */
public final class DemoScript {

    private final Bootstrap app;
    private final PrintStream out;
    private final Inventory inventory;
    private final OperationsGateway gateway;
    private final Actor sofia;
    private final Actor mateo;
    private final Actor agent;
    private final Actor web;

    public DemoScript(Bootstrap app, PrintStream out) {
        this.app = Objects.requireNonNull(app, "app");
        this.out = Objects.requireNonNull(out, "out");
        this.inventory = app.inventory();
        this.gateway = app.gateway();
        this.sofia = app.actor("sofia").orElseThrow();
        this.mateo = app.actor("mateo").orElseThrow();
        this.agent = app.actor("pulse-ai").orElseThrow();
        this.web = app.actor("tienda-web").orElseThrow();
    }

    public void run() {
        out.println("LOGISTPULSE Smart Inventory | demostración de Command + Chain of Responsibility");
        out.println("Actores: " + sofia.describe() + ", " + mateo.describe() + ", " + agent.describe() + ", "
                + web.describe());
        title("0. Punto de partida");
        Reports.stock(inventory, out);

        identifyProducts();
        receiveLots();
        reserveAcrossLots();
        oneRejectionPerLink();
        idempotency();
        ownership();
        undo();
        timePasses();
        receiverDefendsItself();

        title("10. Así quedó todo");
        Reports.stock(inventory, out);
        out.println();
        Reports.reservations(inventory, out);
        out.println();
        out.println("  Ledger (últimas 12 entradas):");
        Reports.ledger(app.ledger(), app.clock().getZone(), 12, out);
    }

    private void identifyProducts() {
        title("1. Command: registrar un producto lo identifica y le da su código");
        send(new RegisterProductCommand("D-101", sofia, inventory, "Laptop HP Pavilion 15"));
        send(new RegisterProductCommand("D-102", sofia, inventory, "Jarabe para la tos 120 ml"));
        send(new RegisterProductCommand("D-103", sofia, inventory, "Cable de extensión 5 m"));
        note("El mismo nombre no entra dos veces, aunque cambien las mayúsculas:");
        send(new RegisterProductCommand("D-104", sofia, inventory, "laptop hp pavilion 15"));
    }

    private void receiveLots() {
        title("2. Recepción de lotes");
        LocalDate today = inventory.today();
        send(new ReceiveLotCommand("D-201", sofia, inventory, "ELEC-LAP-002", "QUITO", 30, null));
        note("Un producto de Farmacia sin fecha de vencimiento no pasa el eslabón de lote:");
        send(new ReceiveLotCommand("D-202", sofia, inventory, "FARM-JAR-002", "QUITO", 50, null));
        send(new ReceiveLotCommand("D-203", sofia, inventory, "FARM-JAR-002", "QUITO", 50, today.plusDays(180)));
    }

    private void reserveAcrossLots() {
        title("3. Reserve: una reserva grande se reparte entre lotes (vence primero, sale primero)");
        send(new ReserveCommand("D-301", web, inventory, "REFR-YOG-001", "QUITO", 100));
    }

    private void oneRejectionPerLink() {
        title("4. Chain of Responsibility: cada eslabón rechaza con su motivo");
        note("Autorización: un operador no puede ajustar stock.");
        send(new AdjustCommand("D-401", sofia, inventory, "L-0001", "QUITO", -2, "pantalla rota"));
        note("Autorización: el agente de IA tiene tope de " + Bootstrap.AGENT_UNIT_LIMIT + " u por operación.");
        send(new ReserveCommand("D-402", agent, inventory, "ABAR-ATU-001", "GUAYAQUIL", 150));
        note("SKU: el producto no existe.");
        send(new ReserveCommand("D-403", sofia, inventory, "ELEC-TV-999", "QUITO", 5));
        note("Stock: no hay tantas unidades.");
        send(new ReserveCommand("D-404", sofia, inventory, "ELEC-LAP-001", "QUITO", 500));
        note("Lote: hay unidades, pero el único lote está en cuarentena.");
        send(new ReserveCommand("D-405", sofia, inventory, "FARM-PAR-001", "GUAYAQUIL", 50));
        note("Capacidad: la bodega de destino no tiene espacio.");
        send(new TransferCommand("D-406", sofia, inventory, "L-0007", "QUITO", "CUENCA", 30));
        note("La misma transferencia con una cantidad que sí cabe recorre los cinco eslabones:");
        send(new TransferCommand("D-407", sofia, inventory, "L-0007", "QUITO", "CUENCA", 15));
    }

    private void idempotency() {
        title("5. Idempotencia: id del comando + huella de su contenido");
        send(new ReserveCommand("WEB-501", web, inventory, "ABAR-ATU-001", "GUAYAQUIL", 20));
        note("La tienda reintenta el mismo pedido (mismo id, mismos datos): no se aplica dos veces.");
        send(new ReserveCommand("WEB-501", web, inventory, "ABAR-ATU-001", "GUAYAQUIL", 20));
        note("Mismo id con otra cantidad: se rechaza, no se da por bueno.");
        send(new ReserveCommand("WEB-501", web, inventory, "ABAR-ATU-001", "GUAYAQUIL", 200));
        note("Un rechazo no gasta el id. Este pedido falla porque en QUITO no hay taladros...");
        send(new ReserveCommand("WEB-502", web, inventory, "FERR-TAL-001", "QUITO", 40));
        note("...llegan taladros desde CUENCA...");
        send(new TransferCommand("D-501", sofia, inventory, "L-0008", "CUENCA", "QUITO", 50));
        note("...y el mismo pedido, con el mismo id, ahora sí se ejecuta.");
        send(new ReserveCommand("WEB-502", web, inventory, "FERR-TAL-001", "QUITO", 40));
    }

    private void ownership() {
        title("6. Control de propiedad: cada quien libera lo suyo");
        note("El agente de IA intenta liberar la reserva RSV-0002, que es de tienda-web:");
        send(new ReleaseCommand("D-601", agent, inventory, "RSV-0002"));
        note("Un supervisor sí puede:");
        send(new ReleaseCommand("D-602", mateo, inventory, "RSV-0002"));
    }

    private void undo() {
        title("7. Deshacer: se compensa con la operación inversa, en orden LIFO");
        send(new AdjustCommand("D-701", mateo, inventory, "L-0007", "QUITO", -5, "envases rotos"));
        send(new TransferCommand("D-702", mateo, inventory, "L-0004", "QUITO", "GUAYAQUIL", 30));
        note("Primer deshacer: regresa la transferencia D-702.");
        say(gateway.undoLast(mateo));
        note("Segundo deshacer: regresa el ajuste D-701.");
        say(gateway.undoLast(mateo));
        note("Sofía bloquea un lote. Cualquier operador puede hacerlo...");
        send(QuarantineCommand.place("D-703", sofia, inventory, "L-0007", "olor extraño en el empaque"));
        note("...pero deshacerlo es levantar una cuarentena, y eso exige supervisor.");
        say(gateway.undoLast(sofia));
        note("El comando no se perdió: sigue primero en la pila de deshacer. Mateo sí puede:");
        say(gateway.undoLast(mateo));
    }

    private void timePasses() {
        title("8. Pasan 5 días: vence un lote que estaba reservado");
        app.clock().advanceDays(5);
        note("Fecha del sistema: " + inventory.today() + ". El lote L-0002 ya venció.");
        send(new DispatchCommand("D-801", web, inventory, "RSV-0001"));
        note("El supervisor libera la reserva y da de baja las unidades vencidas (merma):");
        send(new ReleaseCommand("D-802", mateo, inventory, "RSV-0001"));
        send(new AdjustCommand("D-803", mateo, inventory, "L-0002", "QUITO", -60, "producto vencido"));
        note("La tienda vuelve a reservar: ahora solo se usa el lote vigente. Y el despacho cierra el ciclo.");
        send(new ReserveCommand("D-804", web, inventory, "REFR-YOG-001", "QUITO", 30));
        send(new DispatchCommand("D-805", web, inventory, "RSV-0004"));
    }

    private void receiverDefendsItself() {
        title("9. El receptor se defiende solo (llamadas directas, sin invoker ni cadena)");
        directCall("inventory.adjust(\"L-0001\", \"QUITO\", -9999)",
                () -> inventory.adjust("L-0001", "QUITO", -9999));
        directCall("inventory.reserve(\"intruso\", \"ELEC-LAP-001\", \"QUITO\", 9999)",
                () -> inventory.reserve("intruso", "ELEC-LAP-001", "QUITO", 9999));
        directCall("inventory.transfer(\"L-0005\", \"GUAYAQUIL\", \"QUITO\", 10)",
                () -> inventory.transfer("L-0005", "GUAYAQUIL", "QUITO", 10));
        note("Nada cambió: L-0001 sigue con "
                + inventory.unreservedUnitsOfLot("L-0001", "QUITO") + " u libres en QUITO.");
    }

    private void directCall(String code, Runnable call) {
        try {
            call.run();
            out.println("  " + code + " -> se ejecutó");
        } catch (InventoryRuleViolation violation) {
            out.println("  " + code);
            out.println("      InventoryRuleViolation: " + violation.getMessage());
        }
    }

    private void send(InventoryCommand command) {
        gateway.setCommand(command);
        say(gateway.executeCommand());
    }

    private void say(ExecutionReport report) {
        out.println("  " + report.line());
    }

    private void note(String text) {
        out.println("  . " + text);
    }

    private void title(String text) {
        out.println();
        out.println("== " + text);
    }
}
