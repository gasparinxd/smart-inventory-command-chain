package edu.usfq.smartinv.actors;

import java.util.Objects;

/** Quien solicita una operación. Queda escrito en cada entrada del ledger. */
public record Actor(String id, ActorKind kind, Role role) {

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(role, "role");
        if (id.isBlank()) {
            throw new IllegalArgumentException("el actor necesita un identificador");
        }
    }

    public boolean isSupervisor() {
        return role == Role.SUPERVISOR;
    }

    public String describe() {
        return id + " (" + kind.label() + ", " + role.label() + ")";
    }
}
