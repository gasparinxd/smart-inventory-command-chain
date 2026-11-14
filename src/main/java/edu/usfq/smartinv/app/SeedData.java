package edu.usfq.smartinv.app;

import edu.usfq.smartinv.actors.Actor;
import edu.usfq.smartinv.command.InventoryCommand;
import edu.usfq.smartinv.command.QuarantineCommand;
import edu.usfq.smartinv.command.ReceiveLotCommand;
import edu.usfq.smartinv.command.RegisterProductCommand;
import edu.usfq.smartinv.gateway.ExecutionReport;
import edu.usfq.smartinv.gateway.Outcome;
import edu.usfq.smartinv.inventory.Inventory;
import java.time.LocalDate;

/**
 * Datos de ejemplo. Entran como comandos por el invoker, igual que cualquier otra
 * operación, así que también quedan en el ledger.
 *
 * <pre>
 * ELEC-LAP-001  Laptop Lenovo ThinkPad E14     L-0001  QUITO      40 u
 * REFR-YOG-001  Yogur griego natural 1 kg      L-0002  QUITO      60 u  vence en 4 días
 *                                              L-0003  QUITO      80 u  vence en 20 días
 * FARM-PAR-001  Paracetamol 500 mg caja x100   L-0004  QUITO     200 u
 *                                              L-0005  GUAYAQUIL 150 u  en cuarentena
 * ABAR-ATU-001  Atún en lata 170 g             L-0006  GUAYAQUIL 300 u
 * LIMP-DET-001  Detergente líquido 3 L         L-0007  QUITO      90 u
 * FERR-TAL-001  Taladro percutor 650 W         L-0008  CUENCA    100 u  (bodega casi llena)
 * </pre>
 */
final class SeedData {

    private SeedData() {
    }

    static void load(Bootstrap app) {
        Actor supervisor = app.actor("mateo").orElseThrow();
        Inventory inventory = app.inventory();
        LocalDate today = inventory.today();
        int n = 0;

        String[] names = {
            "Laptop Lenovo ThinkPad E14",
            "Yogur griego natural 1 kg",
            "Paracetamol 500 mg caja x100",
            "Atún en lata 170 g",
            "Detergente líquido 3 L",
            "Taladro percutor 650 W",
        };
        for (String name : names) {
            mustApply(app, new RegisterProductCommand(seedId(++n), supervisor, inventory, name));
        }

        Object[][] lots = {
            {"ELEC-LAP-001", "QUITO", 40, null},
            {"REFR-YOG-001", "QUITO", 60, today.plusDays(4)},
            {"REFR-YOG-001", "QUITO", 80, today.plusDays(20)},
            {"FARM-PAR-001", "QUITO", 200, today.plusDays(300)},
            {"FARM-PAR-001", "GUAYAQUIL", 150, today.plusDays(120)},
            {"ABAR-ATU-001", "GUAYAQUIL", 300, today.plusDays(500)},
            {"LIMP-DET-001", "QUITO", 90, null},
            {"FERR-TAL-001", "CUENCA", 100, null},
        };
        for (Object[] lot : lots) {
            mustApply(app, new ReceiveLotCommand(seedId(++n), supervisor, inventory,
                    (String) lot[0], (String) lot[1], (Integer) lot[2], (LocalDate) lot[3]));
        }

        mustApply(app, QuarantineCommand.place(seedId(++n), supervisor, inventory, "L-0005",
                "sello de seguridad roto en la recepción"));
    }

    private static String seedId(int n) {
        return "SEED-%02d".formatted(n);
    }

    private static void mustApply(Bootstrap app, InventoryCommand command) {
        ExecutionReport report = app.gateway().submit(command);
        if (report.outcome() != Outcome.EXECUTED) {
            throw new IllegalStateException("no se pudieron cargar los datos de ejemplo: " + report.line());
        }
    }
}
