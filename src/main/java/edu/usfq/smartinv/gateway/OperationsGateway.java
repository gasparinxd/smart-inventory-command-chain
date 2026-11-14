package edu.usfq.smartinv.gateway;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.Handler;
import edu.usfq.smartinv.chain.OperationRequest;
import edu.usfq.smartinv.chain.Verdict;
import edu.usfq.smartinv.command.InventoryCommand;
import edu.usfq.smartinv.inventory.InventoryRuleViolation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Invoker del patrón Command y única puerta de entrada al inventario.
 *
 * <p>No sabe qué hace cada comando. Para todos sigue el mismo recorrido:
 * idempotencia, cadena de validación, ejecución y ledger. Depende de las
 * abstracciones {@link Handler} e {@link InventoryCommand}, nunca de clases concretas.
 */
public final class OperationsGateway {

    /** Lo que se recuerda de un comando ya aplicado. */
    private record Applied(String fingerprint, String outcome) {
    }

    private final Handler guardrails;
    private final Ledger ledger;
    private final Map<String, Applied> applied = new HashMap<>();
    private final Deque<InventoryCommand> history = new ArrayDeque<>();
    private InventoryCommand command;

    public OperationsGateway(Handler guardrails, Ledger ledger) {
        this.guardrails = Objects.requireNonNull(guardrails, "guardrails");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public void setCommand(InventoryCommand command) {
        this.command = Objects.requireNonNull(command, "command");
    }

    public ExecutionReport executeCommand() {
        if (command == null) {
            throw new IllegalStateException("no hay comando asignado: primero setCommand()");
        }
        InventoryCommand current = command;
        OperationRequest request = current.request();
        String fingerprint = request.fingerprint();

        // 1. Idempotencia: el id ya aplicado no se repite, y no se acepta con otro contenido.
        Applied previous = applied.get(current.id());
        if (previous != null) {
            if (previous.fingerprint().equals(fingerprint)) {
                return close(current.id(), request, Outcome.DUPLICATE,
                        "ya se había aplicado y no se repite. Resultado original: " + previous.outcome(), null);
            }
            return close(current.id(), request, Outcome.REJECTED,
                    "el id " + current.id() + " ya se usó para una operación con otros datos", "Idempotencia");
        }

        // 2. Cadena de validación: igual para personas, sistemas y agentes de IA.
        Verdict verdict = guardrails.handle(request);
        if (!verdict.approved()) {
            return close(current.id(), request, Outcome.REJECTED, verdict.reason(), verdict.rejectedBy());
        }

        // 3. Ejecución. El receptor todavía puede negarse si se rompería una regla.
        try {
            current.execute();
        } catch (InventoryRuleViolation violation) {
            return close(current.id(), request, Outcome.REJECTED, violation.getMessage(), "Inventario");
        }

        // 4. Solo lo que de verdad se aplicó entra al historial y consume el id.
        history.push(current);
        applied.put(current.id(), new Applied(fingerprint, current.outcome()));
        return close(current.id(), request, Outcome.EXECUTED, current.outcome(), null);
    }

    /** Atajo para el uso normal: asigna el comando y lo ejecuta. */
    public ExecutionReport submit(InventoryCommand next) {
        setCommand(next);
        return executeCommand();
    }

    /**
     * Deshace el último comando aplicado (orden LIFO) con su operación inversa.
     * La inversa pasa por la misma cadena. Si no se puede deshacer, el comando
     * se queda en el historial y el intento queda anotado.
     */
    public ExecutionReport undoLast(Actor requester) {
        Objects.requireNonNull(requester, "requester");
        InventoryCommand last = history.peek();
        if (last == null) {
            return new ExecutionReport(Outcome.REJECTED, "-", "no hay comandos para deshacer", "Historial");
        }
        OperationRequest inverse = last.compensation(requester);
        Actor author = last.request().actor();
        boolean ownCommand = author != null && author.id().equals(requester.id());
        if (!ownCommand && !requester.isSupervisor()) {
            return close(last.id(), inverse, Outcome.REJECTED,
                    "solo " + (author == null ? "su autor" : author.id())
                            + " o un supervisor pueden deshacer " + last.id(), "Autorización");
        }
        Verdict verdict = guardrails.handle(inverse);
        if (!verdict.approved()) {
            return close(last.id(), inverse, Outcome.REJECTED,
                    "no se pudo deshacer: " + verdict.reason(), verdict.rejectedBy());
        }
        try {
            last.undo();
        } catch (InventoryRuleViolation violation) {
            return close(last.id(), inverse, Outcome.REJECTED,
                    "no se pudo deshacer: " + violation.getMessage(), "Inventario");
        }
        history.pop();
        applied.remove(last.id());
        return close(last.id(), inverse, Outcome.UNDONE, "se compensó con: " + inverse.summary(), null);
    }

    /** El comando que se desharía a continuación. */
    public Optional<InventoryCommand> lastApplied() {
        return Optional.ofNullable(history.peek());
    }

    public int undoableCount() {
        return history.size();
    }

    private ExecutionReport close(String commandId, OperationRequest request, Outcome outcome, String message,
                                  String rejectedBy) {
        ledger.append(commandId, request, outcome, message, rejectedBy);
        return new ExecutionReport(outcome, commandId, message, rejectedBy);
    }
}
