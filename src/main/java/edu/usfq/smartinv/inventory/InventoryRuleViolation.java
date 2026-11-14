package edu.usfq.smartinv.inventory;

/**
 * La lanza el inventario cuando una operación rompería una de sus reglas.
 * Es la última defensa: aunque alguien llame al receptor sin pasar por la cadena,
 * el stock nunca queda en un estado imposible.
 */
public class InventoryRuleViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventoryRuleViolation(String message) {
        super(message);
    }
}
