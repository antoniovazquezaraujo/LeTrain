package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D terminal: maps a composed-scenario line back to an editor tab")
class TerminalViewDiagnosticLocatorTest {

    // 1: header     2: seed     3: configuration {   4: key   5: }
    // 6: on build { 7: command  8: }                 9: program { 10: body  11: }
    private static final String FULL = "# LeTrain scenario v1\n"
            + "seed 1\n"
            + "configuration {\n"
            + "threshold.WATER=130\n"
            + "}\n"
            + "on build {\n"
            + "go 0,0; face e; write 1;\n"
            + "}\n"
            + "program {\n"
            + "sensor 1 on train enter { semaphore 1 open; }\n"
            + "}\n";

    @Test
    @DisplayName("header/seed and on build lines go to the Scenario tab")
    void scenarioLines() {
        assertEquals(new TerminalView.Jump(0, 2), TerminalView.locateDiagnostic(FULL, 2));
        assertEquals(new TerminalView.Jump(0, 4), TerminalView.locateDiagnostic(FULL, 7));
    }

    @Test
    @DisplayName("configuration lines go to the Config tab")
    void configLines() {
        assertEquals(new TerminalView.Jump(2, 1), TerminalView.locateDiagnostic(FULL, 3));
        assertEquals(new TerminalView.Jump(2, 2), TerminalView.locateDiagnostic(FULL, 4));
    }

    @Test
    @DisplayName("program lines go to the Program tab")
    void programLines() {
        assertEquals(new TerminalView.Jump(1, 2), TerminalView.locateDiagnostic(FULL, 10));
    }

    @Test
    @DisplayName("without a configuration section nothing shifts")
    void noConfigSection() {
        String full = "# LeTrain scenario v1\nseed 1\non build {\ngo 0,0; face e; write 1;\n}\n";
        assertEquals(new TerminalView.Jump(0, 4), TerminalView.locateDiagnostic(full, 4));
    }
}
