package edu.usfq.smartinv.inventory;

/** Lo que un comando necesita del receptor para el ciclo de una reserva. */
public interface ReservationOperations {

    /** Aparta unidades vigentes del SKU; si un lote no alcanza, reparte entre varios. */
    Reservation reserve(String ownerId, String sku, String warehouse, int quantity);

    Reservation release(String reservationId);

    /** Vuelve a apartar exactamente las mismas unidades de una reserva liberada. */
    Reservation reinstate(String reservationId);

    /** Salida física: las unidades reservadas dejan la bodega. */
    Reservation dispatch(String reservationId);

    /** Devolución de un despacho: las unidades regresan y la reserva vuelve a estar activa. */
    Reservation acceptReturn(String reservationId);
}
