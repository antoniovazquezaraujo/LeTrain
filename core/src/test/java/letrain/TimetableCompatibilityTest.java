package letrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import letrain.command.ScenarioCompiler;
import letrain.command.ScenarioFile;
import letrain.itinerary.Itinerary;
import letrain.itinerary.WaypointCommand;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2a compatibility policy: the comma waypoint syntax is strict for new input, but
 * existing text on disk (scenarios, saved programs and recorded journals) written with the old
 * syntax must keep loading, normalized to the comma form with a warning. These tests exercise the
 * legacy comma-less actions end-to-end (the {@code bucle.json} fixture only checks old save JSON
 * without the new timetable fields).
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

    private static final String LEGACY_PROGRAM_CRLF = LEGACY_PROGRAM.replace("\n", "\r\n");

    /** A model with two stations and a train, so the loaded itineraries actually get built. */
    private static Model newModelWithTrain() {
        Model model = new Model(1);
        model.postLoadInit();
        Station one = new Station(1);
        one.setName("A");
        model.addStation(one);
        Station two = new Station(2);
        two.setName("B");
        model.addStation(two);
        Locomotive loco = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(loco);
        model.addLocomotive(loco);
        return model;
    }

    private static Itinerary assignedItinerary(Model model) {
        return model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();
    }

    @Test
    @DisplayName("a saved program written with the old syntax is normalized when loading")
    void savedProgram_isNormalizedOnLoad() {
        Model model = newModelWithTrain();
        model.setProgram(LEGACY_PROGRAM); // strict parser rejects it; the text is still stored

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));

        assertNotNull(restored);
        assertEquals(NORMALIZED_PROGRAM, restored.getProgram());
        assertEquals(List.of(WaypointCommand.REVERSE, WaypointCommand.UNLOAD),
                assignedItinerary(restored).waypoints().get(0).commands(),
                "the legacy multi-action waypoint must have been built after normalization");
        assertTrue(restored.setProgram(restored.getProgram()).isEmpty(),
                "the normalized program must parse cleanly");
    }

    @Test
    @DisplayName("a saved program with CRLF line endings is normalized when loading")
    void savedProgramWithCrlf_isNormalizedOnLoad() {
        Model model = newModelWithTrain();
        model.setProgram(LEGACY_PROGRAM_CRLF);

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));

        assertNotNull(restored);
        assertEquals(NORMALIZED_PROGRAM, restored.getProgram(),
                "CRLF text must normalize exactly like LF text");
        assertEquals(List.of(WaypointCommand.REVERSE, WaypointCommand.UNLOAD),
                assignedItinerary(restored).waypoints().get(0).commands());
    }

    @Test
    @DisplayName("setProgramFromDisk (used by both clients' program load) normalizes in place")
    void setProgramFromDisk_normalizesInPlace() {
        Model model = newModelWithTrain();

        List<String> errors = model.setProgramFromDisk(LEGACY_PROGRAM_CRLF);

        assertTrue(errors.isEmpty(), "unexpected parser errors: " + errors);
        assertEquals(NORMALIZED_PROGRAM, model.getProgram());
        assertEquals(List.of(WaypointCommand.REVERSE, WaypointCommand.UNLOAD),
                assignedItinerary(model).waypoints().get(0).commands());
    }

    @Test
    @DisplayName("the bucle.json fixture (old save JSON without timetable fields) still loads")
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
