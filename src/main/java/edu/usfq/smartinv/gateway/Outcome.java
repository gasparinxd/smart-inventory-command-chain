package edu.usfq.smartinv.gateway;

/** Cómo terminó un comando al pasar por el invoker. */
public enum Outcome {
    EXECUTED("EJECUTADO"),
    REJECTED("RECHAZADO"),
    DUPLICATE("DUPLICADO"),
    UNDONE("DESHECHO");

    private final String label;

    Outcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
