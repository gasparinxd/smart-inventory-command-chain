package edu.usfq.smartinv.gateway;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Registro de auditoría. Solo se le agregan líneas: no hay forma de editar ni borrar.
 * Guarda todos los intentos, también los rechazados y los duplicados.
 */
public final class Ledger {

    private final Clock clock;
    private final List<LedgerEntry> entries = new ArrayList<>();

    public Ledger(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    LedgerEntry append(String commandId, OperationRequest request, Outcome outcome, String detail,
                       String rejectedBy) {
        Actor actor = request.actor();
        LedgerEntry entry = new LedgerEntry(entries.size() + 1, clock.instant(), commandId,
                actor == null ? "(desconocido)" : actor.id(),
                actor == null ? null : actor.kind(),
                request.type(), request.summary(), outcome, detail, rejectedBy, request.fingerprint());
        entries.add(entry);
        return entry;
    }

    public List<LedgerEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public long count(Outcome outcome) {
        return entries.stream().filter(entry -> entry.outcome() == outcome).count();
    }

    public int size() {
        return entries.size();
    }
}
