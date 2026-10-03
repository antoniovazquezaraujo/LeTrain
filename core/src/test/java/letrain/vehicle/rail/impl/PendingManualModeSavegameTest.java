package letrain.vehicle.rail.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #636: {@code pendingManualMode} is runtime intent ("switch to manual once the train
 * stops"), not persistent state. It must not survive a save/load cycle, or a train that was running
 * under autopilot when saved drops to manual on its next full stop.
 */
@DisplayName("Issue #636: pendingManualMode must not survive a save/load cycle")
class PendingManualModeSavegameTest {

    private Model newModelWithTrain() {
        Model model = new Model(1);
        model.postLoadInit();
        Station station = new Station(1);
        station.setName("A");
        model.addStation(station);
        Locomotive locomotive = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(locomotive);
        model.addLocomotive(locomotive);
        return model;
    }

    private byte[] readResource(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(is, "resource not found: " + name);
            return is.readAllBytes();
        }
    }

    @Test
    @DisplayName("the savegame JSON never contains the runtime flag")
    void savedJson_doesNotContainPendingManualMode() {
        Model model = newModelWithTrain();
        model.getTrainFromLocomotiveId(1).setPendingManualMode(true);

        byte[] saved = new GameSaveService().toBytes(model);

        String json = new String(saved, StandardCharsets.UTF_8);
        assertFalse(json.contains("pendingManualMode"),
                "runtime intent must not be persisted in the savegame JSON");
    }

    @Test
    @DisplayName("an old savegame that contains the flag loads it cleared")
    void oldSavegameWithFlag_loadsCleared() throws IOException {
        // Real player saves (e.g. tiempo.json) already shipped the flag; inject it into an
        // existing fixture to reproduce the load of a document that contains it.
        String json = new String(readResource("simple.json"), StandardCharsets.UTF_8)
                .replace("\"pendingManualMode\":false", "\"pendingManualMode\":true");
        assertTrue(json.contains("\"pendingManualMode\":true"),
                "fixture must contain the stale flag for this test to be meaningful");

        Model loaded = new GameSaveService()
                .load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow();
        Train train = loaded.getTrainFromLocomotiveId(1);
        assertNotNull(train, "train 1 must survive the load");
        assertFalse(train.isPendingManualMode(),
                "the loaded train must not inherit the stale manual request");
    }

    @Test
    @DisplayName("a train saved with the flag set does not reload pending manual mode")
    void roundTrip_flagSetBeforeSave_doesNotReachLoadedTrain() {
        Model model = newModelWithTrain();
        Train train = model.getTrainFromLocomotiveId(1);
        train.setPendingManualMode(true);

        GameSaveService saves = new GameSaveService();
        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        Train loadedTrain = loaded.getTrainFromLocomotiveId(1);
        assertNotNull(loadedTrain, "train 1 must survive the round-trip");
        assertFalse(loadedTrain.isPendingManualMode(),
                "a save/load cycle must not restore runtime manual intent");
    }
}
