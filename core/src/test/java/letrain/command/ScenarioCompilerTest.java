package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario compiler / validator (headless)")
class ScenarioCompilerTest {

    @Test
    @DisplayName("a valid scenario compiles with no diagnostics")
    void validScenario_ok() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "  go 0,0; face e; write 2;\n"
                + "  new locomotive A red;\n"
                + "  train 0 couple forward 1;\n"
                + "}\n"
                + "on start {\n"
                + "  semaphore 1 close;\n"
                + "}\n";
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertTrue(result.ok(), "expected no diagnostics: " + result.diagnostics());
    }

    @Test
    @DisplayName("missing seed is reported")
    void missingSeed_reported() {
        ScenarioCompiler.Result result = ScenarioCompiler.compile("# c\ngo 0,0; face e; write 1;\n");
        assertFalse(result.ok());
        assertTrue(result.diagnostics().get(0).message().toLowerCase().contains("seed"),
                result.diagnostics().toString());
    }

    @Test
    @DisplayName("a syntax error is reported with its line number")
    void syntaxError_reportsLine() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "  go 0,0; face e; write 2;\n"
                + "  frobnicate;\n"
                + "}\n";
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertFalse(result.ok());
        assertEquals(5, result.diagnostics().get(0).line(),
                "diagnostic line: " + result.diagnostics());
    }
}
