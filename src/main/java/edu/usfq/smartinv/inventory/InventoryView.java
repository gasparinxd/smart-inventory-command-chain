package edu.usfq.smartinv.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de solo lectura. Los eslabones de la cadena dependen de esta interfaz,
 * no del inventario concreto: pueden mirar, pero no tocar.
 */
public interface InventoryView {

    LocalDate today();

    Optional<Product> product(String sku);

    List<Product> products();

    Optional<Warehouse> warehouse(String code);

    List<Warehouse> warehouses();

    Optional<Lot> lot(String lotId);

    Optional<Reservation> reservation(String reservationId);

    List<Reservation> reservations();

    /** Unidades físicas sin reservar del SKU en la bodega, incluso vencidas o en cuarentena. */
    int unreservedUnits(String sku, String warehouse);

    /** Unidades sin reservar del SKU en lotes vigentes y sin cuarentena. */
    int sellableUnits(String sku, String warehouse);

    /** Unidades sin reservar de un lote en una bodega. */
    int unreservedUnitsOfLot(String lotId, String warehouse);

    /** Unidades físicas que ocupa la bodega. */
    int storedUnits(String warehouse);

    /** Unidades reservadas que ocupan la zona de despacho. */
    int stagedUnits(String warehouse);

    List<StockLine> stockLines();
}
