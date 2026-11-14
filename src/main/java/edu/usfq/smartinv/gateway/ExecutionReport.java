package edu.usfq.smartinv.gateway;

/**
 * Lo que el invoker le responde a quien envió el comando.
 *
 * @param rejectedBy quién lo detuvo (un eslabón, "Idempotencia", "Inventario"...) o null si no hubo rechazo
 */
public record ExecutionReport(Outcome outcome, String commandId, String message, String rejectedBy) {

    /** true solo si esta llamada cambió el inventario. */
    public boolean changedInventory() {
        return outcome == Outcome.EXECUTED || outcome == Outcome.UNDONE;
    }

    public boolean rejected() {
        return outcome == Outcome.REJECTED;
    }

    /** Línea lista para mostrar, por ejemplo "[RECHAZADO por Stock] CMD-0007: ...". */
    public String line() {
        String tag = rejectedBy == null ? outcome.label() : outcome.label() + " por " + rejectedBy;
        return "[" + tag + "] " + commandId + ": " + message;
    }
}
