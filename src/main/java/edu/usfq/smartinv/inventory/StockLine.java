package edu.usfq.smartinv.inventory;

import java.time.LocalDate;

/** Foto de solo lectura de un lote en una bodega, para mostrar el stock. */
public record StockLine(String lotId, String sku, String warehouse, int onHand, int reserved,
                        LocalDate expiry, boolean expired, String quarantineReason) {

    public int available() {
        return onHand - reserved;
    }

    public boolean quarantined() {
        return quarantineReason != null;
    }
}
