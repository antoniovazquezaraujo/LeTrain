package letrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.util.List;
import letrain.command.ScenarioCompiler;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.itinerary.impl.WaypointImpl;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.WaypointCommandMixin;
import letrain.mvp.impl.WaypointMixin;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2a: the comma waypoint syntax is <b>strict everywhere</b> (console, editor,
 * scenarios, savegames and journals); the old comma-less syntax is not migrated (beta, no
 * compatibility promise). The savegame <b>format</b> keeps tolerating older JSON: waypoints without
 * the {@code arrival}/{@code departure} fields deserialize with empty times, and {@code bucle.json}
 * still loads because its program already uses the strict syntax.
 */
@DisplayName("Timetable: strict text and old save shapes (ADR-022 phase 2a)")
class TimetableCompatibilityTest {

    private static final String COMMA_LESS_PROGRAM = """
            create itinerary "12" {
              add station 1 reverse unload
              add station 2
            }
            assign itinerary "12" to train 1;
            """;

    /** A model with two stations and a train, so an accepted itinerary would actually get built. */
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

    @Test
    @DisplayName("comma-less waypoints are rejected when loading a program from disk (no migration)")
    void savedProgramWithCommaLessWaypoints_isRejected() {
        Model model = newModelWithTrain();

        List<String> errors = model.setProgramFromDisk(COMMA_LESS_PROGRAM);

        assertFalse(errors.isEmpty(), "the strict parser must reject the old syntax");
        assertTrue(errors.get(0).contains("unload"), errors.toString());
    }

    @Test
    @DisplayName("the scenario compiler rejects comma-less waypoints with a diagnostic")
    void scenarioCompiler_rejectsCommaLessWaypoints() {
        String text = "# LeTrain scenario v1\nseed 1\nprogram {\n  create itinerary \"x\" {\n"
                + "    add station 2 reverse unload\n  }\n}\n";

        ScenarioCompiler.Result result = ScenarioCompiler.compile(text);

        assertFalse(result.ok(), "the strict parser must reject the old syntax");
        assertTrue(result.diagnostics().get(0).message().contains("unload"),
                result.diagnostics().toString());
    }

    @Test
    @DisplayName("waypoint JSON saved before timetables (no times fields) loads with empty times")
    void oldWaypointJsonWithoutTimes_loadsWithEmptyTimes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.addMixIn(Waypoint.class, WaypointMixin.class);
        mapper.addMixIn(WaypointImpl.class, WaypointMixin.class);
        mapper.addMixIn(WaypointCommand.class, WaypointCommandMixin.class);

        Waypoint waypoint = mapper.readValue(
                "{\"type\":\"STATION\",\"targetId\":7,\"entryDir\":null,\"commands\":[]}",
                Waypoint.class);

        assertEquals(7, waypoint.targetId());
        assertTrue(waypoint.arrival().isEmpty(), "missing arrival must deserialize as empty");
        assertTrue(waypoint.departure().isEmpty(), "missing departure must deserialize as empty");
    }

    @Test
    @DisplayName("the bucle.json fixture (old save JSON) still loads")
    void bucleFixture_stillLoads() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("bucle.json");
        assertNotNull(is, "resource not found: bucle.json");
        Model loaded = new GameSaveService().load(is).orElseThrow();

        assertNotNull(loaded.getStation(1), "station 1 must be present");
        assertNotNull(loaded.getTrainFromLocomotiveId(1), "train 1 must be present");
        assertTrue(loaded.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isPresent(),
                "the fixture program (already in the strict syntax) must have been assigned");
        assertTrue(loaded.setProgram(loaded.getProgram()).isEmpty(),
                "the fixture program must re-parse cleanly");
    }
}
