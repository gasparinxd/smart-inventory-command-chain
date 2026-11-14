package edu.usfq.smartinv.app;

import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.time.Clock;

/**
 * Punto de entrada.
 * <ul>
 *   <li>sin argumentos: consola interactiva</li>
 *   <li>{@code demo}: demostración automática</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        // En una terminal se usa su codificación para que las tildes se vean bien en Windows.
        Console console = System.console();
        Charset charset = console != null ? console.charset() : Charset.defaultCharset();
        PrintStream out = console != null
                ? new PrintStream(new FileOutputStream(FileDescriptor.out), true, charset)
                : System.out;

        Bootstrap app = Bootstrap.withSampleData(Clock.systemDefaultZone());
        if (args.length > 0 && args[0].equalsIgnoreCase("demo")) {
            new DemoScript(app, out).run();
            return;
        }
        new Shell(app, new InputStreamReader(System.in, charset), out).run();
    }
}
