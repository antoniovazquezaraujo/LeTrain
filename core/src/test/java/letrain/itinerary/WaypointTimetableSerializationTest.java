package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2a: waypoint times must survive the savegame round-trip ({@code GameSaveService})
 * deterministically, so scenario export/import and save/load keep the timetable intact.
 */
@DisplayName("Waypoint timetable serialization (ADR-022 phase 2a)")
class WaypointTimetableSerializationTest {

    private static final String PROGRAM = """
            create itinerary "Ruta" {
                add station 1 arrival 09:00, load, departure 09:20;
                add station 2 arrival 10:23, unload, departure 10:30;
            }
            assign itinerary "Ruta" to train 1;
            """;

    private Model newModelWithTrain() {
        Model model = new Model(1);
        model.postLoadInit();
        Station a = new Station(1);
        a.setName("A");
        model.addStation(a);
        Station b = new Station(2);
        b.setName("B");
        model.addStation(b);
        Locomotive loco = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(loco);
        model.addLocomotive(loco);
        return model;
    }

    @Test
    @DisplayName("times survive a GameSaveService round-trip and stay in the exported program")
    void timesSurviveGameSaveRoundTrip() {
        Model model = newModelWithTrain();
        List<String> errors = model.setProgram(PROGRAM);
        assertTrue(errors.isEmpty(), "unexpected parser errors: " + errors);

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));

        assertNotNull(restored);
        assertEquals(PROGRAM, restored.getProgram(),
                "the exported program must keep the timetable text");
        Itinerary itinerary =
                restored.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();
        assertEquals(LocalTime.of(9, 0), itinerary.waypoints().get(0).arrival().orElseThrow());
        assertEquals(LocalTime.of(9, 20), itinerary.waypoints().get(0).departure().orElseThrow());
        assertEquals(LocalTime.of(10, 23), itinerary.waypoints().get(1).arrival().orElseThrow());
        assertEquals(LocalTime.of(10, 30), itinerary.waypoints().get(1).departure().orElseThrow());
    }
}
