package letrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import letrain.command.ScenarioCompiler;
import letrain.command.ScenarioFile;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2a compatibility policy: the comma waypoint syntax is strict for new input, but
 * existing text on disk (scenarios, saved programs and recorded journals) written with the old
 * syntax must keep loading, normalized to the comma form with a warning.
 */
@DisplayName("Timetable legacy-syntax compatibility (ADR-022 phase 2a)")
class TimetableCompatibilityTest {

    private static final String LEGACY_PROGRAM = """
            create itinerary "12" {
              add station 1 reverse unload
              add station 2
            }
            assign itinerary "12" to train 1;
            """;

    private static final String NORMALIZED_PROGRAM = """
            create itinerary "12" {
              add station 1 reverse, unload
              add station 2
            }
            assign itinerary "12" to train 1;
            """;

    @Test
    @DisplayName("a saved program written with the old syntax is normalized when loading")
    void savedProgram_isNormalizedOnLoad() {
        Model model = new Model(1);
        model.setProgram(LEGACY_PROGRAM); // strict parser rejects it; the text is still stored

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));

        assertNotNull(restored);
        assertEquals(NORMALIZED_PROGRAM, restored.getProgram());
        assertTrue(restored.setProgram(restored.getProgram()).isEmpty(),
                "the normalized program must parse cleanly");
    }

    @Test
    @DisplayName("the bucle.json fixture (old syntax) still loads and its programs re-parse")
    void bucleFixture_stillLoads() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("bucle.json");
        assertNotNull(is, "resource not found: bucle.json");
        Model loaded = new GameSaveService().load(is).orElseThrow();

        assertNotNull(loaded.getStation(1), "station 1 must be present");
        assertNotNull(loaded.getTrainFromLocomotiveId(1), "train 1 must be present");
        assertTrue(loaded.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isPresent(),
                "the recorded itinerary must have been assigned");
        assertTrue(loaded.setProgram(loaded.getProgram()).isEmpty(),
                "the fixture program must re-parse cleanly");
    }

    @Test
    @DisplayName("a legacy scenario program is normalized on load and replays cleanly")
    void legacyScenarioProgram_isNormalizedOnLoad() {
        String text = "# LeTrain scenario v1\nseed 1\nprogram {\n" + LEGACY_PROGRAM + "}\n";

        ScenarioFile.Scenario scenario = ScenarioFile.parse(text);

        assertEquals(NORMALIZED_PROGRAM.stripTrailing(), scenario.program());
        Model model = new Model(scenario.seed());
        model.postLoadInit();
        assertTrue(model.setProgram(scenario.program()).isEmpty());
    }

    @Test
    @DisplayName("a legacy recorded journal (on build) is normalized on load")
    void legacyJournal_isNormalizedOnLoad() {
        String text = "# LeTrain scenario v1\nseed 1\non build {\n"
                + "  add station 2 reverse unload\n}\n";

        List<String> commands = ScenarioFile.commandLines(text);

        assertEquals(List.of("add station 2 reverse, unload"), commands);
    }

    @Test
    @DisplayName("the editor stays strict: legacy syntax is a diagnostic when writing")
    void editorCompile_staysStrict() {
        String text = "# LeTrain scenario v1\nseed 1\nprogram {\n  create itinerary \"x\" {\n"
                + "    add station 2 reverse unload\n  }\n}\n";

        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);

        assertFalse(result.ok(), "the strict parser must reject the old syntax");
    }
}
