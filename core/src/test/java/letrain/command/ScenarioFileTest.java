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

    @Test
    @DisplayName("on build / on start sections are parsed and rendered")
    void sections_roundTrip() {
        String text = ScenarioFile.render(9,
                List.of("go 0,0; face e; write 2;"),
                List.of("semaphore 1 close;"));
        ScenarioFile.Scenario s = ScenarioFile.parse(text);

        assertEquals(9, s.seed());
        assertEquals(List.of("go 0,0; face e; write 2;"), s.buildCommands());
        assertEquals(List.of("semaphore 1 close;"), s.startCommands());
        assertTrue(s.program().isEmpty(), "no program section means empty program");
        assertTrue(text.contains("on build {"));
        assertTrue(text.contains("on start {"));
    }

    @Test
    @DisplayName("the program section is parsed (with nested braces) and rendered verbatim")
    void program_roundTrip() {
        String program = "sensor 1 on train enter {\n  semaphore 1 open;\n}\n"
                + "create itinerary \"x\" { add station 1; }";
        String text = ScenarioFile.render(9,
                List.of("go 0,0; face e; write 2;"),
                List.of("semaphore 1 close;"),
                program);

        ScenarioFile.Scenario s = ScenarioFile.parse(text);

        assertEquals(9, s.seed());
        assertEquals(List.of("go 0,0; face e; write 2;"), s.buildCommands());
        assertEquals(List.of("semaphore 1 close;"), s.startCommands());
        assertEquals(program, s.program(), "the program must survive verbatim (nested braces)");
        assertTrue(text.contains("program {"));
    }

    @Test
    @DisplayName("split/withProgram round-trip the recipe and the program")
    void splitWithProgram_roundTrip() {
        String program = "sensor 1 on train enter { semaphore 1 open; }";
        String full = ScenarioFile.render(9,
                List.of("go 0,0; face e; write 2;"),
                List.of("semaphore 1 close;"),
                program);

        ScenarioFile.Parts parts = ScenarioFile.split(full);

        assertEquals(9, parts.seed());
        assertFalse(parts.recipeText().contains("program {"), parts.recipeText());
        assertEquals(program, parts.program());
        assertEquals(full, ScenarioFile.withProgram(parts.recipeText(), parts.program()));
    }

    @Test
    @DisplayName("a flat scenario without sections is treated as on build (backward compatible)")
    void flat_isBuild() {
        String text = "# LeTrain scenario v1\nseed 5\ngo 0,0; face e; write 1;\n";
        ScenarioFile.Scenario s = ScenarioFile.parse(text);

        assertEquals(5, s.seed());
        assertEquals(List.of("go 0,0; face e; write 1;"), s.buildCommands());
        assertTrue(s.startCommands().isEmpty());
        assertTrue(s.program().isEmpty());
    }

    @Test
    @DisplayName("consecutive straight writes are merged into one write N")
    void optimize_mergesStraightWrites() {
        List<String> commands = List.of(
                "go 0,0; face e; write 1;",
                "go 1,0; face e; write 1;",
                "go 2,0; face e; write 1;");
        assertEquals(List.of("go 0,0; face e; write 3;"), ScenarioFile.optimize(commands));
    }

    @Test
    @DisplayName("a turn or a non-write command breaks the run")
    void optimize_breaksOnTurnAndOtherCommands() {
        assertEquals(List.of(
                "go 0,0; face e; write 2;",
                "go 2,0; face n; write 1;"),
                ScenarioFile.optimize(List.of(
                        "go 0,0; face e; write 1;",
                        "go 1,0; face e; write 1;",
                        "go 2,0; face n; write 1;")));

        assertEquals(List.of(
                "go 0,0; face e; write 1;",
                "new st;",
                "go 1,0; face e; write 1;"),
                ScenarioFile.optimize(List.of(
                        "go 0,0; face e; write 1;",
                        "new st;",
                        "go 1,0; face e; write 1;")));
    }
}
