package edu.usfq.smartinv.command;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.chain.OperationRequest;

/**
 * Command: una operación del inventario convertida en objeto.
 *
 * <p>Además de {@code execute()} y {@code undo()}, cada comando sabe describirse como
 * {@link OperationRequest}. Con esa descripción el invoker lo valida en la cadena,
 * calcula su huella para la idempotencia y lo anota en el ledger, sin conocer la
 * clase concreta del comando.
 */
public interface InventoryCommand {

    /** Clave de idempotencia: la elige quien envía el comando. */
    String id();

    /** Lo que el comando quiere hacer. */
    OperationRequest request();

    /** Delega el trabajo en el receptor. */
    void execute();

    /** Aplica la operación inversa. Solo tiene sentido después de {@code execute()}. */
    void undo();

    /** La operación inversa descrita como solicitud, para validarla antes de deshacer. */
    OperationRequest compensation(Actor requester);

    /** Resultado en una línea, disponible después de {@code execute()}. */
    String outcome();
}
