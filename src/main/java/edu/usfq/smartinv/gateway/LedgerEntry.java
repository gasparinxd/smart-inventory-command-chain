package edu.usfq.smartinv.gateway;

import edu.usfq.smartinv.actors.ActorKind;
import edu.usfq.smartinv.chain.OperationType;
import java.time.Instant;

/**
 * Una línea del ledger: quién pidió qué, cuándo y cómo terminó.
 *
 * @param fingerprint huella del contenido de la solicitud
 * @param rejectedBy  quién la detuvo, o null
 */
public record LedgerEntry(int sequence, Instant at, String commandId, String actorId, ActorKind actorKind,
                          OperationType operation, String summary, Outcome outcome, String detail,
                          String rejectedBy, String fingerprint) {
}
