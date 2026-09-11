package letrain.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the paused-editing undo/redo engine (ADR-020 roadmap, item 3): {@link UndoRedoHistory}
 * must plan undo/redo steps that, when executed by restoring a periodic checkpoint and re-running
 * the recorded commands, reproduce byte-identical states exactly as the golden replay PoC.
 */
@DisplayName("Undo/redo engine (checkpoint + re-execute journal)")
class UndoRedoHistoryTest {

    // ------------------------------------------------------------------
    // Serialization helpers (same mixins as GameSaveService / golden tests)
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

    private static Model deserialize(byte[] data) throws Exception {
        Model model = newMapper().readValue(data, Model.class);
        model.postLoadInit();
        return model;
    }

    // ------------------------------------------------------------------
    // Headless wiring (same as PocJournalReplayTest / CommandJournalTest)
    // ------------------------------------------------------------------

    private static RailTrackMaker headlessMaker(letrain.mvp.Model model) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(model);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new RailTrackMaker(presenter);
    }

    private static String runScript(letrain.mvp.Model model, String script) {
        TurtleBuilder builder = new TurtleBuilder(model, headlessMaker(model));
        return PlayerCommandExecutor.execute(script, model, null, null, builder);
    }

    private static Model newBaseWorld() {
        Model base = new Model();
        base.updateGroundMap(new Point(-30, -30), 60, 60);
        base.getCursor().setPosition(new Point(0, 0));
        base.getCursor().setDir(Dir.E);
        return base;
    }

    /** Builds an UndoRedoHistory wired to the same codec the live UIs will use. */
    private static UndoRedoHistory newHistory() {
        return new UndoRedoHistory(new UndoRedoHistory.Codec() {
            @Override
            public byte[] toBytes(letrain.mvp.Model model) {
                try {
                    return serialize((Model) model);
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
    }

    /** Executes an undo plan the way a presenter would: restore base, apply, replay the slice. */
    private static letrain.mvp.Model applyPlan(UndoRedoHistory history,
            UndoRedoHistory.UndoPlan plan) {
        if (plan == null) {
            return null;
        }
        letrain.mvp.Model target = plan.baseBytes() == null ? history.live()
                : deserializeFrom(plan.baseBytes());
        for (String cmd : plan.commandsToReplay(history.entries())) {
            assertEquals(null, runScript(target, cmd), "replay failed: " + cmd);
        }
        history.bind(target);
        history.commit(plan.target());
        return target;
    }

    private static Model deserializeFrom(byte[] bytes) {
        try {
            return deserialize(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // Engine behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("undo then redo reproduces the exact original state")
    void undo_redo_roundTrip_reproducesOriginalState() throws Exception {
        Model live = newBaseWorld();
        UndoRedoHistory history = newHistory();
        history.begin(live);

        assertEquals(0, history.canUndo());
        assertNull(history.planUndo(1), "nothing to undo at start");

        // Record a small editing session, command by command.
        String[] session = {
            "go 0,0; face e; write 3;",
            "go 1,0; face e; new st;",
            "go 2,0; face e; new sn;"
        };
        for (String cmd : session) {
            assertEquals(null, runScript(live, cmd), "session command failed: " + cmd);
            history.record(cmd);
        }
        byte[] finalState = serialize(live);
        assertEquals(3, history.size());
        assertEquals(3, history.applied());
        assertEquals(3, history.canUndo());
        assertEquals(0, history.canRedo());

        // Undo the last edit: world must equal the state after the first two commands.
        letrain.mvp.Model restored = applyPlan(history, history.planUndo(1));
        assertNotNull(restored, "undo of one step must produce a model");
        assertEquals(2, history.applied());

        // Redo back: the engine replays the dropped command on the current (restored) model.
        letrain.mvp.Model redone = applyPlan(history, history.planRedo(1));
        assertNotNull(redone, "redo of one step must produce a model");
        assertEquals(3, history.applied());
        assertArrayEquals(finalState, serialize((Model) redone),
                "undo+redo must reproduce the byte-identical original state");
    }

    @Test
    @DisplayName("undoing two steps is deterministic and matches an independent replay")
    void undo_twoSteps_matchesFreshReplay() throws Exception {
        Model live = newBaseWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory();
        history.begin(live);

        String[] session = {
            "go 0,0; face e; write 2;",
            "go 4,0; face e; write 2;",
            "go 0,0; face e; new st;"
        };
        for (String cmd : session) {
            assertEquals(null, runScript(live, cmd));
            history.record(cmd);
        }

        // Independent reference: copy of the SAME base world with only session[0] applied.
        Model ref = deserialize(baseBytes);
        assertEquals(null, runScript(ref, session[0]));
        byte[] expected = serialize(ref);

        letrain.mvp.Model restored = applyPlan(history, history.planUndo(1));
        restored = applyPlan(history, history.planUndo(1));
        assertEquals(1, history.applied());
        assertArrayEquals(expected, serialize((Model) restored),
                "undo must match a fresh copy replaying only the surviving commands");
    }

    @Test
    @DisplayName("recording after an undo discards the redo tail")
    void record_afterUndo_discardsRedoTail() throws Exception {
        Model live = newBaseWorld();
        UndoRedoHistory history = newHistory();
        history.begin(live);

        runScript(live, "go 0,0; face e; write 2;");
        history.record("go 0,0; face e; write 2;");
        runScript(live, "go 0,0; face e; write 3;");
        history.record("go 0,0; face e; write 3;");
        assertEquals(2, history.size());

        letrain.mvp.Model restored = applyPlan(history, history.planUndo(1));
        assertNotNull(restored);
        assertEquals(1, history.applied());
        assertEquals(1, history.canRedo());

        // New edit on the restored world: linear editor semantics drop the redo branch.
        assertEquals(null, runScript(restored, "go 2,0; face e; write 1;"));
        history.record("go 2,0; face e; write 1;");

        assertEquals(2, history.size(), "redo tail must be discarded after a new edit");
        assertEquals(2, history.applied());
        assertEquals(0, history.canRedo());
        assertArrayEquals(serialize((Model) restored), serialize((Model) history.live()),
                "history must keep tracking the rebound model");
    }

    @Test
    @DisplayName("checkpoints let undo jump back more than one command deterministically")
    void undo_multiStep_overCheckpointBoundary_isDeterministic() throws Exception {
        // Drive the periodic checkpoint by forcing the private cadence via a session of >10 edits.
        Model live = newBaseWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory();
        history.begin(live);

        // 14 single-tile writes at well-separated spots -> crosses the every-10 checkpoint.
        for (int i = 0; i < 14; i++) {
            String cmd = "go " + (i * 2) + "," + (i * 2) + "; face e; write 1;";
            assertEquals(null, runScript(live, cmd));
            history.record(cmd);
        }
        assertEquals(14, history.size());
        assertEquals(14, history.applied());
        assertTrue(history.entries().size() >= 2, "expect a periodic checkpoint mid-session");

        // Independent reference: copy of the SAME base world with only the first 12 commands.
        Model ref = deserialize(baseBytes);
        for (int i = 0; i < 12; i++) {
            String cmd = "go " + (i * 2) + "," + (i * 2) + "; face e; write 1;";
            assertEquals(null, runScript(ref, cmd));
        }
        byte[] expected = serialize(ref);

        // Undo 2 from the end -> 12 commands applied. The engine restores from the periodic
        // checkpoint (index 10) and re-executes commands 10..12.
        letrain.mvp.Model restored = applyPlan(history, history.planUndo(2));
        assertNotNull(restored);
        assertEquals(12, history.applied());
        assertArrayEquals(expected, serialize((Model) restored),
                "multi-step undo over a checkpoint boundary must be byte-identical");
    }

    @Test
    @DisplayName("coalesced limit tweaks stay coherent across a checkpoint boundary")
    void coalesce_limit_acrossCheckpoint_isDeterministic() throws Exception {
        Model live = newBaseWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory();
        history.begin(live);

        // Two build commands create the track and the signal (indices 0-1).
        String[] build = {
            "go 0,0; face e; write 1;",
            "go 0,0; face e; new sg;"
        };
        for (String cmd : build) {
            assertEquals(null, runScript(live, cmd));
            history.record(cmd);
        }
        int signalId = live.getSpeedSignals().get(0).getId();

        // Seven filler edits (indices 2-8), so the next command lands on the checkpoint at 10.
        for (int i = 1; i <= 7; i++) {
            String cmd = "go " + (i * 2) + ",0; face e; write 1;";
            assertEquals(null, runScript(live, cmd));
            history.record(cmd);
        }
        // First limit tweak at index 9 -> applied 10, checkpoint at 10 is taken.
        String limit1 = "go 0,0; face e; signal " + signalId + " set limit 5;";
        assertEquals(null, runScript(live, limit1));
        history.record(limit1);
        assertEquals(10, history.applied());

        // Two more tweaks coalesce into index 9, invalidating the stale checkpoint at 10.
        for (String limit : new String[] {
            "go 0,0; face e; signal " + signalId + " set limit 6;",
            "go 0,0; face e; signal " + signalId + " set limit 7;"}) {
            assertEquals(null, runScript(live, limit));
            history.recordCoalescing(limit);
        }
        assertEquals(10, history.size(), "limit tweaks must collapse into one command");

        // A different edit past the boundary (index 10), applied 11.
        String after = "go 20,0; face e; write 1;";
        assertEquals(null, runScript(live, after));
        history.record(after);
        assertEquals(11, history.applied());

        // Reference: base + the 9 first commands + the FINAL limit value.
        Model ref = deserialize(baseBytes);
        for (String cmd : build) {
            assertEquals(null, runScript(ref, cmd));
        }
        for (int i = 1; i <= 7; i++) {
            assertEquals(null, runScript(ref, "go " + (i * 2) + ",0; face e; write 1;"));
        }
        assertEquals(null,
                runScript(ref, "go 0,0; face e; signal " + signalId + " set limit 7;"));
        byte[] expected = serialize(ref);

        // Undo to the boundary must reproduce the coalesced final limit, not the stale snapshot.
        letrain.mvp.Model restored = applyPlan(history, history.planUndo(1));
        assertNotNull(restored);
        assertEquals(10, history.applied());
        assertArrayEquals(expected, serialize((Model) restored));
    }

    @Test
    @DisplayName("plan undoing past the start clamps to zero and returns the base world")
    void undo_beyondStart_clampsToBase() throws Exception {
        Model live = newBaseWorld();
        byte[] baseBytes = serialize(live);
        UndoRedoHistory history = newHistory();
        history.begin(live);

        runScript(live, "go 0,0; face e; write 1;");
        history.record("go 0,0; face e; write 1;");
        assertEquals(1, history.canUndo());

        UndoRedoHistory.UndoPlan plan = history.planUndo(5);
        assertNotNull(plan);
        assertEquals(0, plan.target());
        assertEquals(0, plan.fromIndex());
        assertEquals(0, plan.toIndex());

        letrain.mvp.Model restored = applyPlan(history, plan);
        byte[] got = serialize((Model) restored);
        assertArrayEquals(serialize(deserialize(baseBytes)), got,
                "undo past the start must restore exactly the base world");
    }
}
