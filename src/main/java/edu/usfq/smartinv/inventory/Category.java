package edu.usfq.smartinv.inventory;

/** Familias de producto. El prefijo forma parte del SKU que genera el catálogo. */
public enum Category {
    ELECTRONICS("ELEC", "Electrónica", false),
    REFRIGERATED("REFR", "Refrigerados", true),
    PHARMACY("FARM", "Farmacia", true),
    GROCERY("ABAR", "Abarrotes", false),
    CLEANING("LIMP", "Limpieza", false),
    HARDWARE("FERR", "Ferretería", false),
    GENERAL("GENE", "General", false);

    private final String prefix;
    private final String label;
    private final boolean expiryRequired;

    Category(String prefix, String label, boolean expiryRequired) {
        this.prefix = prefix;
        this.label = label;
        this.expiryRequired = expiryRequired;
    }

    public String prefix() {
        return prefix;
    }

    public String label() {
        return label;
    }

    /** Las familias perecibles no aceptan lotes sin fecha de vencimiento. */
    public boolean expiryRequired() {
        return expiryRequired;
    }
}
