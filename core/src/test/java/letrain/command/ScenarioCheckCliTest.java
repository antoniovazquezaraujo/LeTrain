package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @DisplayName("a scenario with a syntax error returns 1")
    void run_bad_returnsOne() throws Exception {
        Path file = Files.createTempFile("bad", ".ltr");
        Files.writeString(file,
                "# LeTrain scenario v1\nseed 1\non build {\nfrobnicate;\n}\n");
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
