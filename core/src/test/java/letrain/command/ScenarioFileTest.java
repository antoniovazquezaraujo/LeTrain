package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario file format (seed + command journal)")
class ScenarioFileTest {

    @Test
    @DisplayName("render/parse round-trips the seed and the commands")
    void render_parse_roundTrip() {
        List<String> commands = List.of(
                "go 0,0; face e; write 5;",
                "new st;",
                "go 7,0; face e; new sn;");
        String text = ScenarioFile.render(123, commands);

        assertEquals(123, ScenarioFile.parseSeed(text));
        assertEquals(commands, ScenarioFile.commandLines(text));
        assertTrue(text.startsWith("# LeTrain scenario v1"));
        assertTrue(text.contains("seed 123"));
    }

    @Test
    @DisplayName("comments, blanks and the seed line are ignored when collecting commands")
    void parsing_ignoresCommentsBlanksAndSeed() {
        String text = "# LeTrain scenario v1\n"
                + "seed 42\n"
                + "\n"
                + "# a comment\n"
                + "go 0,0; face e; write 3;\n"
                + "   \n"
                + "new sm;\n";
        assertEquals(42, ScenarioFile.parseSeed(text));
        assertEquals(List.of("go 0,0; face e; write 3;", "new sm;"),
                ScenarioFile.commandLines(text));
    }

    @Test
    @DisplayName("a scenario without a seed line is rejected")
    void missingSeed_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ScenarioFile.parseSeed("# LeTrain scenario v1\ngo 0,0; face e; write 1;\n"));
    }

    @Test
    @DisplayName("extension detection for .ltr")
    void extensionDetection() {
        assertTrue(ScenarioFile.isScenarioName("mi-red.ltr"));
        assertTrue(ScenarioFile.isScenarioName("MI-RED.LTR"));
        assertFalse(ScenarioFile.isScenarioName("savegame.json"));
        assertFalse(ScenarioFile.isScenarioName(null));
    }

    @Test
    @DisplayName("vehicles and coupling commands are exported in order (operator)")
    void render_includesVehiclesAndCoupling() {
        List<String> commands = List.of(
                "go 0,0; face e; write 3;",
                "go 0,0; face e; new locomotive A red;",
                "go 1,0; face e; new wagon b coal;",
                "train 0 couple forward 1;",
                "go 5,0; face e; new sn;");
        String text = ScenarioFile.render(7, commands);

        assertEquals(commands, ScenarioFile.commandLines(text));
    }
}
