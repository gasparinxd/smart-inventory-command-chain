package edu.usfq.smartinv.actors;

/** Nivel de permiso de quien pide una operación. */
public enum Role {
    OPERATOR("operador"),
    SUPERVISOR("supervisor");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
