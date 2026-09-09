package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import letrain.command.PlayerCommandExecutor;
import letrain.command.TurtleBuilder;
import letrain.command.UndoRedoHistory;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keyboard recorder determinism (ADR-020 item 3): the edits made through {@link RailTrackMaker}
 * (the shared choke both 2D/3D keyboards drive) are journaled as canonical self-positioned DSL
 * commands. Re-executing those commands on a fresh copy of the checkpointed base world must produce
 * a byte-identical state, exactly like the undo/redo replay will.
 */
@DisplayName("Keyboard recorder: RailTrackMaker edits replay byte-identically")
class KeyboardRecorderTest {

    // ------------------------------------------------------------------
    // Serialization helpers
    // ------------------------------------------------------------------

    private static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.addMixIn(letrain.mvp.Model.class, letrain.mvp.impl.ModelMixin.class);
        mapper.addMixIn(letrain.mvp.impl.Model.class, letrain.mvp.impl.ModelMixin.class);
        mapper.addMixIn(letrain.vehicle.rail.impl.Train.class,
                letrain.mvp.impl.TrainMixin.class);
        mapper.addMixIn(letrain.itinerary.Waypoint.class, letrain.mvp.impl.WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.WaypointImpl.class,
                letrain.mvp.impl.WaypointMixin.class);
        mapper.addMixIn(letrain.itinerary.Itinerary.class, letrain.mvp.impl.ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.ItineraryImpl.class,
                letrain.mvp.impl.ItineraryMixin.class);
        mapper.addMixIn(letrain.itinerary.AutoPilot.class, letrain.mvp.impl.AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.impl.AutoPilotImpl.class,
                letrain.mvp.impl.AutoPilotMixin.class);
        mapper.addMixIn(letrain.itinerary.WaypointCommand.class,
                letrain.mvp.impl.WaypointCommandMixin.class);
        return mapper;
    }

    private static byte[] serialize(Model model) throws Exception {
        return newMapper().writeValueAsBytes(model);
    }

    private static letrain.mvp.impl.Model deserialize(byte[] data) throws Exception {
        letrain.mvp.impl.Model model =
                newMapper().readValue(data, letrain.mvp.impl.Model.class);
        model.postLoadInit();
        return model;
    }

    // ------------------------------------------------------------------
    // Wired harness: a presenter whose model is paused (undo active)
    // ------------------------------------------------------------------

    /** A presenter bound to {@code model} whose undo history is a real, recording session. */
    private static RailTrackMaker makerFor(letrain.mvp.impl.Model model,
            UndoRedoHistory history) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(model);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        org.mockito.Mockito.when(presenter.getUndoRedoHistory()).thenReturn(history);
        return new RailTrackMaker(presenter);
    }

    private static letrain.mvp.impl.Model pausedWorld() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        // Pause-editing on + RAILS => world frozen and undo history active.
        model.setPauseEditing(true);
        model.setMode(letrain.mvp.Model.GameMode.RAILS);
        return model;
    }

    private static UndoRedoHistory newHistory(letrain.mvp.impl.Model model) {
        UndoRedoHistory history = new UndoRedoHistory(new UndoRedoHistory.Codec() {
            @Override
            public byte[] toBytes(Model m) {
                try {
                    return serialize((letrain.mvp.impl.Model) m);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Model fromBytes(byte[] data) {
                try {
                    return deserialize(data);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        history.begin(model);
        return history;
    }

    /**
     * Replays the whole journal slice on {@code model} through one shared maker — the same setup
     * the undo/redo {@code applyPlan} path uses (one presenter maker reused across the commands, so
     * adjacency/oldTrack chaining between consecutive tiles is preserved).
     */
    private static void replaySlice(letrain.mvp.impl.Model model, List<String> journal) {
        UndoRedoHistory replayHistory = newHistory(model);
        RailTrackMaker replayMaker = makerFor(model, replayHistory);
        TurtleBuilder builder = new TurtleBuilder(model, replayMaker);
        for (String cmd : journal) {
            String error = PlayerCommandExecutor.execute(cmd, model, null, null, builder);
            assertEquals(null, error, "replay failed for '" + cmd + "': " + error);
        }
    }

    /**
     * Applies an undo plan exactly as the presenter does (TerminalPresenter.applyPlan): restore the
     * checkpoint base into {@code restored}, bind the history to it, recreate the maker fresh,
     * re-seed it from the recorded resume origin when the slice starts mid-gesture, replay the
     * slice, and commit.
     */
    private static void applyUndoPlan(letrain.mvp.impl.Model restored, UndoRedoHistory history,
            UndoRedoHistory.UndoPlan plan) {
        if (plan == null) {
            return;
        }
        history.bind(restored);
        RailTrackMaker maker = makerFor(restored, history);
        letrain.map.Point origin = history.resumeFrom(plan.fromIndex());
        if (origin != null) {
            letrain.track.rail.RailTrack predecessor =
                    restored.getRailMap().getTrackAt(origin.getX(), origin.getY());
            if (predecessor != null) {
                maker.resumeChainFrom(predecessor);
            }
        }
        TurtleBuilder builder = new TurtleBuilder(restored, maker);
        for (String cmd : plan.commandsToReplay(history.entries())) {
            String error = PlayerCommandExecutor.execute(cmd, restored, null, null, builder);
            assertEquals(null, error, "slice replay failed for '" + cmd + "': " + error);
        }
        history.commit(plan.target());
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("keyboard writes/erases journal; replaying the journal is byte-deterministic")
    void keyboardTrackEdits_replay_deterministic() throws Exception {
        letrain.mvp.impl.Model live = pausedWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);

        // Simulated keyboard: build 3 tiles along x from (0,0), then 2 more from (5,0).
        maker.createTrack(null);
        maker.createTrack(null);
        maker.createTrack(null);
        live.getCursor().setPosition(new Point(5, 0));
        live.getCursor().setDir(Dir.E);
        maker.createTrack(null);
        maker.createTrack(null);
        // Then erase the tile under the cursor (Ctrl+arrow erase-forward) at (5,0).
        live.getCursor().setPosition(new Point(5, 0));
        maker.removeTrack(true);

        List<String> journal = history.entries();
        assertEquals(6, journal.size(),
                "each keyboard tile write/erase must be journaled: " + journal);
        assertTrue(journal.get(0).startsWith("go 0,0; face e; write 1;"),
                "write must be self-positioned: " + journal.get(0));
        assertTrue(journal.get(journal.size() - 1).startsWith("go 5,0; face e; del 1;"),
                "erase must be journaled as self-positioned del: " + journal.get(journal.size() - 1));

        // The undo/redo guarantee: replaying the SAME journal on two fresh copies of the same base
        // must be byte-identical (redo always reconstructs the same state deterministically).
        letrain.mvp.impl.Model repA = deserialize(baseBytes);
        letrain.mvp.impl.Model repB = deserialize(baseBytes);
        replaySlice(repA, journal);
        replaySlice(repB, journal);
        assertArrayEquals(serialize(repA), serialize(repB),
                "keyboard journal replay must be deterministic across copies");

        // Functional check: 4 tiles survive (3 east + 2 east, one erased) on the expected cells.
        assertNotNull(repA.getRailMap().getTrackAt(0, 0));
        assertNotNull(repA.getRailMap().getTrackAt(1, 0));
        assertNotNull(repA.getRailMap().getTrackAt(2, 0));
        assertEquals(null, repA.getRailMap().getTrackAt(5, 0), "erased tile must be gone");
        assertNotNull(repA.getRailMap().getTrackAt(6, 0));
    }

    @Test
    @DisplayName("keyboard sensor/station toggles journal; replaying is byte-deterministic")
    void keyboardElementToggles_replay_deterministic() throws Exception {
        letrain.mvp.impl.Model live = pausedWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);

        // Build a 4-tile line from (0,0), then place sensor + station via the maker toggle methods.
        maker.createTrack(null);
        maker.createTrack(null);
        maker.createTrack(null);
        maker.createTrack(null);

        live.getCursor().setPosition(new Point(0, 0));
        live.getCursor().setDir(Dir.E);
        maker.manageStationSensor(); // station at 0,0
        live.getCursor().setPosition(new Point(1, 0));
        live.getCursor().setDir(Dir.E);
        maker.manageSensor(); // sensor at 1,0

        List<String> journal = history.entries();
        assertEquals(6, journal.size(), "expected 4 writes + new st + new sn: " + journal);
        assertTrue(journal.get(4).contains("new st;"), journal.get(4));
        assertTrue(journal.get(5).contains("new sn;"), journal.get(5));

        // Determinism of the journal replay (the undo/redo invariant).
        letrain.mvp.impl.Model repA = deserialize(baseBytes);
        letrain.mvp.impl.Model repB = deserialize(baseBytes);
        replaySlice(repA, journal);
        replaySlice(repB, journal);
        assertArrayEquals(serialize(repA), serialize(repB),
                "keyboard element journal replay must be deterministic across copies");

        // Functional check: 4 tiles + station at (0,0) + sensor at (1,0).
        assertEquals(1, repA.getStations().size(), "expected one station");
        assertEquals(1, repA.getSensors().size(), "expected one sensor");
        assertNotNull(repA.getStations().get(0));
        assertEquals(0, repA.getStations().get(0).getTrack().getPosition().getX());
        assertEquals(0, repA.getStations().get(0).getTrack().getPosition().getY());
    }

    @Test
    @DisplayName("keyboard edits are not recorded while not paused")
    void keyboardEdits_notRecordedWhenNotPaused() throws Exception {
        letrain.mvp.impl.Model live = pausedWorld();
        live.setPauseEditing(false); // world running -> no undo session
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);

        maker.createTrack(null);
        maker.createTrack(null);
        assertEquals(0, history.size(), "no edits may be journaled outside pause-editing");
        assertNotNull(maker, "maker must still function normally outside pause");
    }

    @Test
    @DisplayName("console turtle sequences are not double-journaled by the maker")
    void consoleSequences_notDoubleJournaled() {
        letrain.mvp.impl.Model live = pausedWorld();
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);
        TurtleBuilder builder = new TurtleBuilder(live, maker);

        // A console "write 3" goes through TurtleBuilder: the maker must suppress per-tile
        // recording so only the caller's whole-line funnel records once.
        String error = PlayerCommandExecutor.execute("go 0,0; face e; write 3;", live, null, null,
                builder);
        assertEquals(null, error, "console write must run, error=" + error);
        assertEquals(0, history.size(),
                "maker must not journal console turtle sequences (caller records the line)");
    }

    @Test
    @DisplayName("undo over a periodic-checkpoint boundary of a continuous painted line keeps it connected")
    void paintingUndo_acrossCheckpointBoundary_byteIdenticalToFreshReplay() throws Exception {
        letrain.mvp.impl.Model live = pausedWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);

        // One continuous hand-painted line of 13 tiles east from (0,0): crosses the every-10
        // periodic checkpoint. Undo of one tile must leave a solid, connected 12-tile line.
        for (int i = 0; i < 13; i++) {
            maker.createTrack(null);
        }
        assertEquals(13, history.size());

        // Reference expectation: a fresh copy of the base world with only the first 12 commands.
        letrain.mvp.impl.Model ref = deserialize(baseBytes);
        replaySlice(ref, history.entries().subList(0, 12));
        byte[] expected = serialize(ref);

        // Undo of one step from 13 -> target 12, base = periodic checkpoint at index 10; the slice
        // [10, 12) starts mid-gesture, so the first replayed tile must continue the tile at (9,0).
        UndoRedoHistory.UndoPlan plan = history.planUndo(1);
        assertEquals(12, plan.target());
        assertEquals(new letrain.map.Point(9, 0), history.resumeFrom(plan.fromIndex()),
                "the slice must start chaining from the tile laid just before the checkpoint");
        letrain.mvp.impl.Model restored = deserialize(plan.baseBytes());
        applyUndoPlan(restored, history, plan);
        assertEquals(12, history.applied());

        assertArrayEquals(expected, serialize(restored),
                "undo must match a fresh copy replaying only the surviving commands");
        assertNotNull(restored.getRailMap().getTrackAt(9, 0));
        assertNotNull(restored.getRailMap().getTrackAt(10, 0));
        assertEquals(restored.getRailMap().getTrackAt(10, 0),
                restored.getRailMap().getTrackAt(9, 0).getConnected(Dir.E),
                "the surviving line must stay connected across the checkpoint boundary");
    }

    @Test
    @DisplayName("undo of a 45-degree curve starting exactly on a checkpoint boundary keeps the curve")
    void paintingTurn_acrossCheckpointBoundary_undoKeepsTheCurve() throws Exception {
        letrain.mvp.impl.Model live = pausedWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory(live);
        RailTrackMaker maker = makerFor(live, history);

        // 10 east tiles (commands 0..9, periodic checkpoint lands after them), then a right turn to
        // face SE and 5 more tiles: command 10 is the curve at (10,0) exiting SE. A slice starting
        // at index 10 must re-lay that curve with the W entry it chained from, not a diagonal root.
        for (int i = 0; i < 10; i++) {
            maker.createTrack(null);
        }
        maker.cursorTurnRight();
        for (int i = 0; i < 5; i++) {
            maker.createTrack(null);
        }
        assertEquals(15, history.size());
        assertEquals(new letrain.map.Point(9, 0), history.resumeFrom(10),
                "the curve command must record that it chains from the tile at (9,0)");

        // Reference expectation: first 14 commands on a fresh copy of the base world.
        letrain.mvp.impl.Model ref = deserialize(baseBytes);
        replaySlice(ref, history.entries().subList(0, 14));
        byte[] expected = serialize(ref);

        UndoRedoHistory.UndoPlan plan = history.planUndo(1);
        assertEquals(14, plan.target());
        letrain.mvp.impl.Model restored = deserialize(plan.baseBytes());
        applyUndoPlan(restored, history, plan);
        assertEquals(14, history.applied());

        assertArrayEquals(expected, serialize(restored),
                "undo must match a fresh copy replaying only the surviving commands");
        letrain.track.Track curve = restored.getRailMap().getTrackAt(10, 0);
        assertNotNull(curve, "the curve tile at (10,0) must survive the undo");
        assertEquals(restored.getRailMap().getTrackAt(9, 0), curve.getConnected(Dir.W),
                "the curve must stay connected to the line entering from the west");
    }
}
