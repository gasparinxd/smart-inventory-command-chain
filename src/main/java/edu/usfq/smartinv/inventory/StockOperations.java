package edu.usfq.smartinv.inventory;

import java.time.LocalDate;

/** Lo que un comando necesita del receptor para mover stock físico. */
public interface StockOperations {

    Lot receiveLot(String sku, String warehouse, int quantity, LocalDate expiry);

    /** Solo si el lote sigue tal como entró. */
    void cancelReceipt(String lotId);

    /** Suma (delta positivo) o resta (delta negativo) unidades por conteo, merma o hallazgo. */
    void adjust(String lotId, String warehouse, int delta);

    void transfer(String lotId, String fromWarehouse, String toWarehouse, int quantity);

    void quarantine(String lotId, String reason);

    /** @return el motivo que tenía la cuarentena, para poder restaurarla */
    String liftQuarantine(String lotId);
}
