package edu.usfq.smartinv.app;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.command.AdjustCommand;
import edu.usfq.smartinv.command.DispatchCommand;
import edu.usfq.smartinv.command.InventoryCommand;
import edu.usfq.smartinv.command.QuarantineCommand;
import edu.usfq.smartinv.command.ReceiveLotCommand;
import edu.usfq.smartinv.command.RegisterProductCommand;
import edu.usfq.smartinv.command.ReleaseCommand;
import edu.usfq.smartinv.command.ReserveCommand;
import edu.usfq.smartinv.command.TransferCommand;
import edu.usfq.smartinv.inventory.Inventory;
import edu.usfq.smartinv.inventory.ProductClassifier;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Consola interactiva. Cada línea que escribe el usuario se convierte en un comando
 * (patrón Command) y se envía al invoker; la consola nunca toca el inventario directamente.
 *
 * <p>Una línea puede empezar con {@code #ID} para elegir la clave de idempotencia,
 * por ejemplo {@code #PED-7 reservar ABAR-ATU-001 GUAYAQUIL 20}.
 */
public final class Shell {

    /** El usuario escribió mal un comando; el mensaje es la forma correcta de usarlo. */
    private static final class Usage extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Usage(String message) {
            super(message);
        }
    }

    private final Bootstrap app;
    private final Inventory inventory;
    private final BufferedReader in;
    private final PrintStream out;
    private Actor actor;
    private int counter;
    private InventoryCommand lastSent;

    public Shell(Bootstrap app, Reader in, PrintStream out) {
        this.app = Objects.requireNonNull(app, "app");
        this.inventory = app.inventory();
        this.in = new BufferedReader(Objects.requireNonNull(in, "in"));
        this.out = Objects.requireNonNull(out, "out");
        this.actor = app.actor("sofia").orElseThrow();
    }

    public void run() throws IOException {
        out.println("LOGISTPULSE Smart Inventory | Command + Chain of Responsibility");
        out.println("Escribe 'ayuda' para ver los comandos, 'demo' para la demostración y 'salir' para terminar.");
        out.println("Estás trabajando como " + actor.describe() + ".");
        while (true) {
            out.print(actor.id() + "> ");
            out.flush();
            String line = in.readLine();
            if (line == null || !interpret(line)) {
                break;
            }
        }
        out.println("Hasta luego.");
    }

    /**
     * Interpreta una línea.
     *
     * @return false cuando el usuario pide salir
     */
    public boolean interpret(String rawLine) {
        String line = rawLine.replace("﻿", "").strip(); // PowerShell antepone esa marca al redirigir texto
        if (line.isEmpty()) {
            return true;
        }
        List<String> words = new ArrayList<>(Arrays.asList(line.split("\\s+")));
        String explicitId = null;
        if (words.get(0).startsWith("#") && words.get(0).length() > 1) {
            explicitId = words.remove(0).substring(1);
        }
        if (words.isEmpty()) {
            out.println("Falta el comando después del id.");
            return true;
        }
        String verb = ProductClassifier.normalize(words.remove(0));
        try {
            switch (verb) {
                case "salir", "exit" -> {
                    return false;
                }
                case "ayuda", "help", "?" -> help();
                case "actor" -> switchActor(words);
                case "actores" -> app.actors().forEach(a -> out.println("  " + a.describe()));
                case "registrar" -> send(new RegisterProductCommand(id(explicitId), actor, inventory,
                        text(words, 0, "registrar <nombre del producto>")));
                case "recibir" -> receive(explicitId, words);
                case "reservar" -> {
                    String usage = "reservar <sku> <bodega> <cantidad>";
                    send(new ReserveCommand(id(explicitId), actor, inventory, code(words, 0, usage),
                            code(words, 1, usage), number(words, 2, usage)));
                }
                case "liberar" -> send(new ReleaseCommand(id(explicitId), actor, inventory,
                        code(words, 0, "liberar <reserva>")));
                case "despachar" -> send(new DispatchCommand(id(explicitId), actor, inventory,
                        code(words, 0, "despachar <reserva>")));
                case "ajustar" -> {
                    String usage = "ajustar <lote> <bodega> <+cantidad|-cantidad> <motivo>";
                    send(new AdjustCommand(id(explicitId), actor, inventory, code(words, 0, usage),
                            code(words, 1, usage), number(words, 2, usage), text(words, 3, usage)));
                }
                case "transferir" -> {
                    String usage = "transferir <lote> <origen> <destino> <cantidad>";
                    send(new TransferCommand(id(explicitId), actor, inventory, code(words, 0, usage),
                            code(words, 1, usage), code(words, 2, usage), number(words, 3, usage)));
                }
                case "cuarentena" -> {
                    String usage = "cuarentena <lote> <motivo>";
                    send(QuarantineCommand.place(id(explicitId), actor, inventory, code(words, 0, usage),
                            text(words, 1, usage)));
                }
                case "levantar" -> send(QuarantineCommand.lift(id(explicitId), actor, inventory,
                        code(words, 0, "levantar <lote>")));
                case "deshacer" -> out.println(app.gateway().undoLast(actor).line());
                case "reintentar" -> retry();
                case "avanzar" -> advance(words);
                case "productos" -> Reports.products(inventory, out);
                case "stock" -> Reports.stock(inventory, out);
                case "bodegas" -> Reports.warehouses(inventory, out);
                case "reservas" -> Reports.reservations(inventory, out);
                case "ledger" -> Reports.ledger(app.ledger(), app.clock().getZone(),
                        !words.isEmpty() && words.get(0).equalsIgnoreCase("todo") ? Integer.MAX_VALUE : 10, out);
                case "demo" -> {
                    out.println("(La demostración corre sobre una copia nueva de los datos; tu sesión no cambia.)");
                    new DemoScript(app.freshCopy(), out).run();
                }
                default -> out.println("No conozco el comando '" + verb + "'. Escribe 'ayuda'.");
            }
        } catch (Usage usage) {
            out.println("Uso: " + usage.getMessage());
        }
        return true;
    }

    private void receive(String explicitId, List<String> words) {
        String usage = "recibir <sku> <bodega> <cantidad> [vence AAAA-MM-DD]";
        LocalDate expiry = null;
        if (words.size() > 3) {
            String raw = words.get(words.size() - 1);
            try {
                expiry = LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                throw new Usage(usage + "   ('" + raw + "' no es una fecha AAAA-MM-DD)");
            }
        }
        send(new ReceiveLotCommand(id(explicitId), actor, inventory, code(words, 0, usage), code(words, 1, usage),
                number(words, 2, usage), expiry));
    }

    private void switchActor(List<String> words) {
        if (words.isEmpty()) {
            out.println("Actor actual: " + actor.describe() + ". Disponibles:");
            app.actors().forEach(a -> out.println("  " + a.describe()));
            return;
        }
        String wanted = words.get(0).toLowerCase(Locale.ROOT);
        app.actor(wanted).ifPresentOrElse(found -> {
            actor = found;
            out.println("Ahora trabajas como " + found.describe() + ".");
        }, () -> out.println("No existe el actor '" + wanted + "'. Escribe 'actores' para ver la lista."));
    }

    private void retry() {
        if (lastSent == null) {
            out.println("Todavía no has enviado ningún comando.");
            return;
        }
        out.println("(se reenvía " + lastSent.id() + " tal cual)");
        out.println(app.gateway().submit(lastSent).line());
    }

    private void advance(List<String> words) {
        int days = number(words, 0, "avanzar <días>");
        if (days <= 0) {
            throw new Usage("avanzar <días>   (un número mayor que cero)");
        }
        app.clock().advanceDays(days);
        out.println("La fecha del sistema ahora es " + inventory.today() + ".");
    }

    private void send(InventoryCommand command) {
        lastSent = command;
        app.gateway().setCommand(command);
        out.println(app.gateway().executeCommand().line());
    }

    private String id(String explicitId) {
        return explicitId != null ? explicitId : "CMD-%04d".formatted(++counter);
    }

    /** Códigos (SKU, bodega, lote, reserva): siempre en mayúsculas. */
    private static String code(List<String> words, int index, String usage) {
        if (index >= words.size()) {
            throw new Usage(usage);
        }
        return words.get(index).toUpperCase(Locale.ROOT);
    }

    private static int number(List<String> words, int index, String usage) {
        if (index >= words.size()) {
            throw new Usage(usage);
        }
        try {
            return Integer.parseInt(words.get(index));
        } catch (NumberFormatException e) {
            throw new Usage(usage + "   ('" + words.get(index) + "' no es un número entero)");
        }
    }

    /** Texto libre: todas las palabras desde la posición indicada. */
    private static String text(List<String> words, int from, String usage) {
        if (from >= words.size()) {
            throw new Usage(usage);
        }
        return String.join(" ", words.subList(from, words.size()));
    }

    private void help() {
        out.println("""
                Operaciones (cada una es un comando que pasa por la cadena de validación):
                  registrar <nombre>                               identifica el producto y le asigna SKU
                  recibir <sku> <bodega> <cantidad> [AAAA-MM-DD]   entra un lote (la fecha es el vencimiento)
                  reservar <sku> <bodega> <cantidad>               aparta unidades a tu nombre
                  liberar <reserva>                                solo el dueño o un supervisor
                  despachar <reserva>                              las unidades salen de la bodega
                  ajustar <lote> <bodega> <+n|-n> <motivo>         conteo o merma (solo supervisor)
                  transferir <lote> <origen> <destino> <cantidad>  mueve unidades entre bodegas
                  cuarentena <lote> <motivo>                       bloquea un lote
                  levantar <lote>                                  quita la cuarentena (solo supervisor)
                Historial:
                  deshacer                 compensa el último comando aplicado con su inverso
                  reintentar               reenvía el último comando con el mismo id (idempotencia)
                  #ID <operación>          usa ID como clave de idempotencia, ej: #PED-7 reservar ...
                Consultas:
                  productos | stock | bodegas | reservas | ledger [todo]
                Sesión:
                  actor [id]               ver o cambiar quién envía (sofia, mateo, pulse-ai, tienda-web)
                  avanzar <días>           adelanta la fecha del sistema para ver vencimientos
                  demo | ayuda | salir
                Para empezar:
                  stock
                  reservar REFR-YOG-001 QUITO 100
                  reservar FARM-PAR-001 GUAYAQUIL 10
                  actor mateo
                  deshacer""");
    }
}
