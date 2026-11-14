package edu.usfq.smartinv.chain;

/** Handler del patrón Chain of Responsibility. */
public interface Handler {

    /**
     * Enlaza el eslabón siguiente.
     *
     * @return el mismo eslabón que se recibió, para poder encadenar llamadas
     */
    Handler setNext(Handler next);

    /** Revisa la solicitud y la aprueba, la rechaza o la pasa al siguiente. Nunca lanza por un rechazo. */
    Verdict handle(OperationRequest request);
}
