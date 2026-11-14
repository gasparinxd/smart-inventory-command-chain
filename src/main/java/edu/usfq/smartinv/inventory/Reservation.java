package edu.usfq.smartinv.inventory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unidades apartadas para un dueño. Una reserva puede repartirse entre varios lotes
 * cuando uno solo no alcanza. Solo el inventario cambia su estado.
 */
public final class Reservation {

    public enum Status {
        ACTIVE("activa"),
        RELEASED("liberada"),
        DISPATCHED("despachada");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Parte de la reserva que sale de un lote concreto. */
    public record Line(String lotId, int quantity) {
    }

    private final String id;
    private final String ownerId;
    private final String sku;
    private final String warehouse;
    private final List<Line> lines;
    private Status status = Status.ACTIVE;

    Reservation(String id, String ownerId, String sku, String warehouse, List<Line> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.warehouse = Objects.requireNonNull(warehouse, "warehouse");
        this.lines = List.copyOf(lines);
    }

    public String id() {
        return id;
    }

    public String ownerId() {
        return ownerId;
    }

    public String sku() {
        return sku;
    }

    public String warehouse() {
        return warehouse;
    }

    public List<Line> lines() {
        return lines;
    }

    public Status status() {
        return status;
    }

    public int units() {
        return lines.stream().mapToInt(Line::quantity).sum();
    }

    /** Texto corto del reparto, por ejemplo "60 de L-0002 + 40 de L-0003". */
    public String breakdown() {
        return lines.stream()
                .map(line -> line.quantity() + " de " + line.lotId())
                .collect(Collectors.joining(" + "));
    }

    void mark(Status newStatus) {
        this.status = newStatus;
    }
}
