package letrain.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless scenario check CLI (ADR-020 item 4 tooling), used by the {@code letrain-check} executable:
 * {@code letrain-check file.ltr} (a leading {@code --check}/{@code -c} is accepted). Prints
 * compiler-style diagnostics ({@code path:line:col: error: message}) and returns an exit code, so
 * editors (e.g. vim's {@code :make}) can validate a scenario without starting the game.
 *
 * <p>
 * Exit codes: {@code 0} ok, {@code 1} diagnostics, {@code 2} usage/IO error.
 */
public final class ScenarioCheckCli {

    private ScenarioCheckCli() {}

    /** Runs the check and returns the process exit code. */
    public static int run(String[] args) {
        String path = null;
        if (args != null && args.length >= 1 && args[0] != null && !args[0].isEmpty()) {
            path = args[0];
            if ("--check".equals(path) || "-c".equals(path) || "check".equals(path)) {
                path = args.length >= 2 ? args[1] : null;
            }
        }
        if (path == null || path.isEmpty()) {
            System.err.println("usage: letrain-check <file.ltr>");
            return 2;
        }
        try {
            String text = Files.readString(Path.of(path));
            ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
            for (ScenarioCompiler.Diagnostic d : result.diagnostics()) {
                System.out.println(path + ":" + d.line() + ":" + d.col() + ": error: " + d.message());
            }
            if (result.ok()) {
                System.out.println(path + ": ok");
                return 0;
            }
            return 1;
        } catch (IOException e) {
            System.err.println(path + ":0:0: error: " + e.getMessage());
            return 2;
        }
    }
}
