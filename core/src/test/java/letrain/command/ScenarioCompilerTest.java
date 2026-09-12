package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario compiler / validator (syntax, headless)")
class ScenarioCompilerTest {

    @Test
    @DisplayName("a valid scenario (with a program) compiles with no diagnostics")
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
                + "}\n"
                + "program {\n"
                + "  sensor 1 on train enter { semaphore 1 open; }\n"
                + "}\n";
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertTrue(result.ok(), "expected no diagnostics: " + result.diagnostics());
    }

    @Test
    @DisplayName("a program with nested braces is accepted")
    void program_nestedBraces_ok() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "  go 0,0; face e; write 2;\n"
                + "}\n"
                + "program {\n"
                + "  sensor 1 on train enter {\n"
                + "    semaphore 1 open;\n"
                + "  }\n"
                + "}\n";
        assertTrue(ScenarioCompiler.compile(text).ok());
    }

    @Test
    @DisplayName("blank lines inside on build/on start are ignored")
    void blankLinesInSections_ok() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "\n"
                + "  go 0,0; face e; write 2;\n"
                + "\n"
                + "}\n"
                + "on start {\n"
                + "\n"
                + "  semaphore 1 close;\n"
                + "\n"
                + "}\n";
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertTrue(result.ok(), "blank lines must not fail: " + result.diagnostics());
    }

    @Test
    @DisplayName("an empty/whitespace program section is not validated")
    void emptyProgram_ok() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "  go 0,0; face e; write 2;\n"
                + "}\n"
                + "program {\n"
                + "  \n"
                + "}\n";
        assertTrue(ScenarioCompiler.compile(text).ok(),
                "empty program must not fail: " + ScenarioCompiler.compile(text).diagnostics());
    }

    @Test
    @DisplayName("diagnostics don't dump the whole expected-token set")
    void diagnostics_areShort() {
        String text = "# LeTrain scenario v1\n"
                + "seed 5\n"
                + "on build {\n"
                + "  frobnicate;\n"
                + "}\n";
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertFalse(result.ok());
        String message = result.diagnostics().get(0).message();
        assertFalse(message.contains("expecting"), message);
        assertTrue(message.length() <= 63, "message too long: " + message);
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
    @DisplayName("a syntax error in on build is reported with its line number")
    void buildSyntaxError_reportsLine() {
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

    @Test
    @DisplayName("a syntax error inside the program is reported with its absolute line number")
    void programSyntaxError_reportsAbsoluteLine() {
        String text = "# LeTrain scenario v1\n" // 1
                + "seed 5\n" // 2
                + "on build {\n" // 3
                + "  go 0,0; face e; write 2;\n" // 4
                + "}\n" // 5
                + "program {\n" // 6
                + "  sensor 1 on train enter { semaphore 1 open; }\n" // 7
                + "  frobnicate;\n" // 8
                + "}\n"; // 9
        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);
        assertFalse(result.ok());
        assertEquals(8, result.diagnostics().get(0).line(),
                "diagnostic line: " + result.diagnostics());
    }
}
