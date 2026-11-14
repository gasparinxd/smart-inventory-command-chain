package edu.usfq.smartinv.chain;

import edu.usfq.smartinv.actors.Actor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;

/**
 * La solicitud que recorre la cadena: una sola forma para todas las operaciones.
 * Es inmutable y se crea con los métodos de fábrica, que dejan vacíos los campos que
 * la operación no usa. De ella salen también el texto del ledger y la huella que
 * usa la idempotencia.
 *
 * @param quantity unidades; en un ajuste lleva signo
 * @param text     dato libre de la operación (nombre del producto o motivo)
 * @param summary  descripción legible, lista para el ledger
 */
public record OperationRequest(OperationType type, Actor actor, String sku, String lotId, String warehouse,
                               String targetWarehouse, int quantity, String reservationId, LocalDate expiry,
                               String text, String summary) {

    public OperationRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(summary, "summary");
    }

    public static OperationRequest registerProduct(Actor actor, String name) {
        return new OperationRequest(OperationType.REGISTER_PRODUCT, actor, null, null, null, null, 0, null, null,
                name, "registrar el producto \"" + name + "\"");
    }

    public static OperationRequest retireProduct(Actor actor, String sku) {
        return new OperationRequest(OperationType.RETIRE_PRODUCT, actor, sku, null, null, null, 0, null, null,
                null, "retirar el producto " + sku);
    }

    public static OperationRequest receive(Actor actor, String sku, String warehouse, int quantity,
                                           LocalDate expiry) {
        String due = expiry == null ? "sin vencimiento" : "vence " + expiry;
        return new OperationRequest(OperationType.RECEIVE, actor, sku, null, warehouse, null, quantity, null,
                expiry, null, "recibir " + quantity + " u de " + sku + " en " + warehouse + " (" + due + ")");
    }

    public static OperationRequest cancelReceipt(Actor actor, String lotId) {
        return new OperationRequest(OperationType.CANCEL_RECEIPT, actor, null, lotId, null, null, 0, null, null,
                null, "anular la recepción del lote " + lotId);
    }

    public static OperationRequest reserve(Actor actor, String sku, String warehouse, int quantity) {
        return new OperationRequest(OperationType.RESERVE, actor, sku, null, warehouse, null, quantity, null,
                null, null, "reservar " + quantity + " u de " + sku + " en " + warehouse);
    }

    public static OperationRequest release(Actor actor, String reservationId) {
        return new OperationRequest(OperationType.RELEASE, actor, null, null, null, null, 0, reservationId, null,
                null, "liberar la reserva " + reservationId);
    }

    public static OperationRequest dispatch(Actor actor, String reservationId) {
        return new OperationRequest(OperationType.DISPATCH, actor, null, null, null, null, 0, reservationId, null,
                null, "despachar la reserva " + reservationId);
    }

    public static OperationRequest customerReturn(Actor actor, String reservationId, String warehouse,
                                                  int quantity) {
        return new OperationRequest(OperationType.RETURN, actor, null, null, warehouse, null, quantity,
                reservationId, null, null,
                "devolver a " + warehouse + " las " + quantity + " u de la reserva " + reservationId);
    }

    public static OperationRequest adjust(Actor actor, String lotId, String warehouse, int delta, String reason) {
        String signed = delta > 0 ? "+" + delta : Integer.toString(delta);
        return new OperationRequest(OperationType.ADJUST, actor, null, lotId, warehouse, null, delta, null, null,
                reason, "ajustar " + signed + " u del lote " + lotId + " en " + warehouse + " (" + reason + ")");
    }

    public static OperationRequest transfer(Actor actor, String lotId, String from, String to, int quantity) {
        return new OperationRequest(OperationType.TRANSFER, actor, null, lotId, from, to, quantity, null, null,
                null, "transferir " + quantity + " u del lote " + lotId + " de " + from + " a " + to);
    }

    public static OperationRequest quarantine(Actor actor, String lotId, String reason) {
        return new OperationRequest(OperationType.QUARANTINE, actor, null, lotId, null, null, 0, null, null,
                reason, "poner en cuarentena el lote " + lotId + " (" + reason + ")");
    }

    public static OperationRequest liftQuarantine(Actor actor, String lotId) {
        return new OperationRequest(OperationType.LIFT_QUARANTINE, actor, null, lotId, null, null, 0, null, null,
                null, "levantar la cuarentena del lote " + lotId);
    }

    public boolean needs(Need need) {
        return type.needs(need);
    }

    /** Unidades que saldrían del stock libre de {@link #warehouse()}. */
    public int unitsLeaving() {
        if (!needs(Need.PHYSICAL_STOCK)) {
            return 0;
        }
        return type.signedQuantity() ? Math.max(0, -quantity) : quantity;
    }

    /** Unidades que llegarían a {@link #arrivalWarehouse()}. */
    public int unitsArriving() {
        if (!needs(Need.STORAGE_ROOM)) {
            return 0;
        }
        return type.signedQuantity() ? Math.max(0, quantity) : quantity;
    }

    public String arrivalWarehouse() {
        return targetWarehouse != null ? targetWarehouse : warehouse;
    }

    /**
     * Huella del contenido. Dos solicitudes con los mismos datos dan la misma huella;
     * si cambia cualquier dato (por ejemplo la cantidad), cambia la huella.
     */
    public String fingerprint() {
        String canonical = String.join("|",
                type.name(),
                actor == null ? "" : actor.id(),
                orEmpty(sku), orEmpty(lotId), orEmpty(warehouse), orEmpty(targetWarehouse),
                Integer.toString(quantity),
                orEmpty(reservationId),
                expiry == null ? "" : expiry.toString(),
                text == null ? "" : text.strip());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
