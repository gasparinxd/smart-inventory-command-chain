package edu.usfq.smartinv.inventory;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Lote de un producto: comparte fecha de vencimiento y estado de calidad.
 * La cuarentena es del lote entero, sin importar en qué bodegas tenga unidades.
 * Solo el inventario (mismo paquete) puede cambiarlo.
 */
public final class Lot {

    private final String id;
    private final String sku;
    private final LocalDate expiry;
    private final String receivedAt;
    private final int receivedQuantity;
    private String quarantineReason;

    Lot(String id, String sku, LocalDate expiry, String receivedAt, int receivedQuantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.expiry = expiry;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.receivedQuantity = receivedQuantity;
    }

    public String id() {
        return id;
    }

    public String sku() {
        return sku;
    }

    public Optional<LocalDate> expiry() {
        return Optional.ofNullable(expiry);
    }

    /** Bodega por la que entró el lote. */
    public String receivedAt() {
        return receivedAt;
    }

    public int receivedQuantity() {
        return receivedQuantity;
    }

    /** Un lote sirve hasta su fecha de vencimiento inclusive. */
    public boolean isExpired(LocalDate today) {
        return expiry != null && expiry.isBefore(today);
    }

    public boolean isQuarantined() {
        return quarantineReason != null;
    }

    public Optional<String> quarantineReason() {
        return Optional.ofNullable(quarantineReason);
    }

    /** Se puede vender: ni vencido ni en cuarentena. */
    public boolean isSellable(LocalDate today) {
        return !isQuarantined() && !isExpired(today);
    }

    LocalDate expiryOrFarFuture() {
        return expiry != null ? expiry : LocalDate.MAX;
    }

    void quarantine(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InventoryRuleViolation("la cuarentena del lote " + id + " necesita un motivo");
        }
        if (isQuarantined()) {
            throw new InventoryRuleViolation("el lote " + id + " ya está en cuarentena (" + quarantineReason + ")");
        }
        quarantineReason = reason.strip();
    }

    String liftQuarantine() {
        if (!isQuarantined()) {
            throw new InventoryRuleViolation("el lote " + id + " no está en cuarentena");
        }
        String previous = quarantineReason;
        quarantineReason = null;
        return previous;
    }
}
