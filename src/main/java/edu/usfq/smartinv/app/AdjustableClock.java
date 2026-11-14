package edu.usfq.smartinv.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Reloj que se puede adelantar. Sirve para mostrar qué pasa cuando un lote vence
 * sin tener que esperar: todo el sistema lee la hora de aquí.
 */
public final class AdjustableClock extends Clock {

    private final Clock base;
    private Duration offset = Duration.ZERO;

    public AdjustableClock(Clock base) {
        this.base = Objects.requireNonNull(base, "base");
    }

    public void advanceDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("los días deben ser mayores que cero");
        }
        offset = offset.plusDays(days);
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        AdjustableClock copy = new AdjustableClock(base.withZone(zone));
        copy.offset = offset;
        return copy;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset);
    }
}
