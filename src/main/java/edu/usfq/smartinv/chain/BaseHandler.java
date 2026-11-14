package edu.usfq.smartinv.chain;

/**
 * BaseHandler: guarda el siguiente eslabón y sabe reenviarle la solicitud.
 * Si ya no hay siguiente, la solicitud pasó todas las revisiones.
 */
public abstract class BaseHandler implements Handler {

    private Handler next;

    @Override
    public Handler setNext(Handler next) {
        this.next = next;
        return next;
    }

    @Override
    public Verdict handle(OperationRequest request) {
        if (next != null) {
            return next.handle(request);
        }
        return Verdict.pass();
    }

    /** Nombre con el que este eslabón firma sus rechazos. */
    protected abstract String linkName();

    protected Verdict reject(String reason) {
        return Verdict.reject(linkName(), reason);
    }
}
