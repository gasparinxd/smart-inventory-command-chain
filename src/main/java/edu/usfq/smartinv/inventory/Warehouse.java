package edu.usfq.smartinv.inventory;

import java.util.Objects;

/**
 * Bodega con dos límites: cuántas unidades caben almacenadas y cuántas pueden estar
 * comprometidas a la vez en la zona de despacho (unidades reservadas esperando salir).
 */
public record Warehouse(String code, String name, int storageCapacity, int stagingCapacity) {

    public Warehouse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (code.isBlank()) {
            throw new IllegalArgumentException("la bodega necesita un código");
        }
        if (storageCapacity <= 0 || stagingCapacity <= 0) {
            throw new IllegalArgumentException("las capacidades de " + code + " deben ser mayores que cero");
        }
    }
}
