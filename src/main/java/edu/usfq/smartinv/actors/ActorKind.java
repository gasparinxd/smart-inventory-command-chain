package edu.usfq.smartinv.actors;

/**
 * Qué clase de actor envía la operación. La cadena de validación es la misma para todos:
 * una persona, un agente de IA o un sistema externo pasan por los mismos eslabones.
 */
public enum ActorKind {
    HUMAN("persona"),
    AI_AGENT("agente de IA"),
    API("sistema");

    private final String label;

    ActorKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
