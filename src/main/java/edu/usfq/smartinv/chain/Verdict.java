package edu.usfq.smartinv.chain;

/**
 * Respuesta de la cadena. Un rechazo no es una excepción: es un resultado normal que
 * dice qué eslabón detuvo la solicitud y por qué.
 */
public record Verdict(boolean approved, String rejectedBy, String reason) {

    private static final Verdict PASS = new Verdict(true, null, null);

    public static Verdict pass() {
        return PASS;
    }

    public static Verdict reject(String link, String reason) {
        return new Verdict(false, link, reason);
    }
}
