package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario check CLI (letrain-check)")
class ScenarioCheckCliTest {

    @Test
    @DisplayName("a valid scenario returns 0 (with or without the --check prefix)")
    void run_ok_returnsZero() throws Exception {
        Path file = Files.createTempFile("ok", ".ltr");
        Files.writeString(file,
                "# LeTrain scenario v1\nseed 1\non build {\ngo 0,0; face e; write 1;\n}\n");
        try {
            assertEquals(0, ScenarioCheckCli.run(new String[] {file.toString()}));
            assertEquals(0, ScenarioCheckCli.run(new String[] {"--check", file.toString()}));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("a legacy scenario (CRLF, comma-less waypoints) is normalized with a warning")
    void run_legacyScenario_normalizesWithVisibleWarning() throws Exception {
        Path file = Files.createTempFile("legacy", ".ltr");
        Files.writeString(file, "# LeTrain scenario v1\r\nseed 1\r\nprogram {\r\n"
                + "create itinerary \"x\" {\r\n  add station 2 reverse unload\r\n}\r\n}\r\n");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            assertEquals(0, ScenarioCheckCli.run(new String[] {file.toString()}),
                    "the validator must load what the game loads");
        } finally {
            System.setOut(original);
            Files.deleteIfExists(file);
        }
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains(": warning: " + LegacyScriptNormalizer.LEGACY_WARNING), output);
        assertTrue(output.contains(": ok"), output);
    }

    @Test
    @DisplayName("normalizing does not mask real syntax errors (still returns 1)")
    void run_legacyScenarioWithRealError_returnsOne() throws Exception {
        Path file = Files.createTempFile("legacy-bad", ".ltr");
        Files.writeString(file,
                "# LeTrain scenario v1\nseed 1\nprogram {\n"
                        + "create itinerary \"x\" {\n  add station 2 reverse unload\n}\n"
                        + "frobnicate;\n}\n");
        try {
            assertEquals(1, ScenarioCheckCli.run(new String[] {file.toString()}));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("a scenario with a syntax error returns 1")
    void run_bad_returnsOne() throws Exception {
        Path file = Files.createTempFile("bad", ".ltr");
        Files.writeString(file, "# LeTrain scenario v1\nseed 1\non build {\nfrobnicate;\n}\n");
        try {
            assertEquals(1, ScenarioCheckCli.run(new String[] {file.toString()}));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("a missing file returns 2")
    void run_missingFile_returnsTwo() {
        assertEquals(2, ScenarioCheckCli.run(new String[] {"/no/such/file.ltr"}));
    }

    @Test
    @DisplayName("no argument returns 2")
    void run_noArgs_returnsTwo() {
        assertEquals(2, ScenarioCheckCli.run(new String[] {}));
        assertEquals(2, ScenarioCheckCli.run(new String[] {"--check"}));
        assertEquals(2, ScenarioCheckCli.run(null));
    }
}
