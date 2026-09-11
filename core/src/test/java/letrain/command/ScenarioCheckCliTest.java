package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario check CLI (--check)")
class ScenarioCheckCliTest {

    @Test
    @DisplayName("invocation detection accepts --check/-c/check")
    void isCheckInvocation() {
        assertTrue(ScenarioCheckCli.isCheckInvocation(new String[] {"--check", "x.ltr"}));
        assertTrue(ScenarioCheckCli.isCheckInvocation(new String[] {"-c", "x.ltr"}));
        assertTrue(ScenarioCheckCli.isCheckInvocation(new String[] {"check", "x.ltr"}));
        assertFalse(ScenarioCheckCli.isCheckInvocation(new String[] {}));
        assertFalse(ScenarioCheckCli.isCheckInvocation(null));
    }

    @Test
    @DisplayName("a valid scenario returns 0")
    void run_ok_returnsZero() throws Exception {
        Path file = Files.createTempFile("ok", ".ltr");
        Files.writeString(file,
                "# LeTrain scenario v1\nseed 1\non build {\ngo 0,0; face e; write 1;\n}\n");
        try {
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
            assertEquals(1, ScenarioCheckCli.run(new String[] {"--check", file.toString()}));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("a missing file returns 2")
    void run_missingFile_returnsTwo() {
        assertEquals(2, ScenarioCheckCli.run(new String[] {"--check", "/no/such/file.ltr"}));
    }
}
