package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for issue #702: optional entity names (trains, stations, sensors) used to be appended
 * raw, so an unnamed entity rendered as a literal {@code null} in {@code ls}.
 */
@DisplayName("Console 'ls': unnamed entities (issue #702)")
class ListEntitiesUnnamedTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        Train train = new Train(3);
        train.setModel(model);
        Locomotive loco = new Locomotive(1, "A", "RED");
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        model.addStation(new Station(2));
        model.addSensor(new Sensor(4));
    }

    /** Runs a command and returns the message the executor pushed to the UI (or null). */
    private static String run(Model model, String script) {
        String[] captured = {null};
        String error = PlayerCommandExecutor.execute(script, model, null, null, null,
                (title, text) -> captured[0] = text, null);
        assertNull(error, error);
        return captured[0];
    }

    @Test
    @DisplayName("'ls;' never prints a literal null and marks unnamed entities")
    void ls_unnamedEntities_showPlaceholder() {
        String listing = run(model, "ls;");

        assertNotNull(listing);
        assertFalse(listing.contains("null"), listing);
        assertTrue(listing.contains(" - 1: (unnamed)"), listing);
        assertTrue(listing.contains(" - 2: (unnamed)"), listing);
        assertTrue(listing.contains(" - 4: (unnamed)"), listing);
    }

    @Test
    @DisplayName("'ls train;' uses the placeholder for an unnamed train")
    void ls_train_unnamed() {
        String listing = run(model, "ls train;");

        assertNotNull(listing);
        assertFalse(listing.contains("null"), listing);
        assertTrue(listing.contains(" - 1: (unnamed)"), listing);
        assertFalse(listing.contains("Stations:"), listing);
    }

    @Test
    @DisplayName("'info train;' listing uses the placeholder too")
    void info_typeOnlyTrain_unnamed() {
        String listing = run(model, "info train;");

        assertNotNull(listing);
        assertFalse(listing.contains("null"), listing);
        assertTrue(listing.contains(" - 1: (unnamed)"), listing);
    }

    @Test
    @DisplayName("'info <type> <id>;' uses the placeholder on the Name line")
    void info_byId_unnamed() {
        String trainInfo = run(model, "info train 1;");
        assertNotNull(trainInfo);
        assertFalse(trainInfo.contains("null"), trainInfo);
        assertTrue(trainInfo.contains("Name: (unnamed)"), trainInfo);

        String stationInfo = run(model, "info station 2;");
        assertNotNull(stationInfo);
        assertTrue(stationInfo.contains("Name: (unnamed)"), stationInfo);

        String sensorInfo = run(model, "info sensor 4;");
        assertNotNull(sensorInfo);
        assertTrue(sensorInfo.contains("Name: (unnamed)"), sensorInfo);
    }

    @Test
    @DisplayName("named entities keep showing their names")
    void ls_namedEntities_showNames() {
        model.getLocomotives().get(0).getTrain().setName("Express");
        model.getStations().get(0).setName("Central");
        model.getSensors().get(0).setName("S1");

        String listing = run(model, "ls;");

        assertNotNull(listing);
        assertFalse(listing.contains("null"), listing);
        assertTrue(listing.contains(" - 1: Express"), listing);
        assertTrue(listing.contains(" - 2: Central"), listing);
        assertTrue(listing.contains(" - 4: S1"), listing);
    }
}
