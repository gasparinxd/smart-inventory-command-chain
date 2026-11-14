package edu.usfq.smartinv.chain;

/**
 * Verificaciones que una operación puede exigir. Cada tipo de operación declara las
 * suyas en {@link OperationType} y cada eslabón de la cadena atiende solo las que le tocan.
 * Así una operación nueva no obliga a modificar los eslabones existentes.
 */
public enum Need {
    /** Solo un supervisor puede pedirla. */
    SUPERVISOR,
    /** Actúa sobre una reserva: debe existir y ser del actor (o de un supervisor). */
    OWNED_RESERVATION,
    /** El SKU indicado debe existir en el catálogo. */
    KNOWN_SKU,
    /** La cantidad debe ser mayor que cero. */
    POSITIVE_QUANTITY,
    /** La cantidad puede ser negativa o positiva, pero no cero. */
    NONZERO_QUANTITY,
    /** Deben existir unidades físicas sin reservar para lo que sale. */
    PHYSICAL_STOCK,
    /** El lote indicado debe existir. */
    EXISTING_LOT,
    /** Los lotes involucrados no pueden estar vencidos ni en cuarentena. */
    HEALTHY_LOT,
    /** Debe haber suficientes unidades en lotes vigentes y sin cuarentena. */
    SELLABLE_STOCK,
    /** La fecha de vencimiento debe ser coherente con la familia del producto. */
    VALID_EXPIRY,
    /** Debe haber espacio de almacenamiento donde llegan las unidades. */
    STORAGE_ROOM,
    /** Debe haber espacio en la zona de despacho. */
    STAGING_ROOM
}
