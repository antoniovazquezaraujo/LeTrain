package letrain.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the editing command journal (ADR-020 roadmap, item 2): the {@link CommandJournal}
 * storage and the {@code record on/off} DSL commands that feed it from the console. A replay test
 * proves that executing the journaled commands on a fresh byte-identical copy of the same base
 * world reproduces the exact same state.
 */
@DisplayName("Command journal (record on/off + replay)")
class CommandJournalTest {

    private letrain.mvp.impl.Model model;

    @BeforeEach
    void setUp() {
        model = new letrain.mvp.impl.Model();
        model.updateGroundMap(new Point(-150, -150), 300, 300);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
    }

    // ------------------------------------------------------------------
    // Serialization helpers (same mixins as GameSaveService)
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

    private static byte[] serialize(letrain.mvp.impl.Model m) throws IOException {
        return newMapper().writeValueAsBytes(m);
    }

    private static letrain.mvp.impl.Model deserialize(byte[] data) throws IOException {
        letrain.mvp.impl.Model m = newMapper().readValue(data, letrain.mvp.impl.Model.class);
        m.postLoadInit();
        return m;
    }

    /** Headless RailTrackMaker on the model (same wiring as the golden PoC test). */
    private static letrain.mvp.impl.RailTrackMaker headlessMaker(letrain.mvp.impl.Model m) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(m);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new letrain.mvp.impl.RailTrackMaker(presenter);
    }

    /** Runs a full player script; asserts it succeeded (execute returns null on success). */
    private void runOk(letrain.mvp.impl.Model m, String script) {
        TurtleBuilder builder = new TurtleBuilder(m, headlessMaker(m));
        String error = PlayerCommandExecutor.execute(script, m, null, null, builder);
        assertEquals(null, error, "script failed: " + error + "\nscript: " + script);
    }

    // ------------------------------------------------------------------
    // CommandJournal storage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("journal records nothing until record on, then every successful command")
    void journal_recordsOnlyWhileOn() {
        assertFalse(model.getCommandJournal().isRecording());
        assertEquals(0, model.getCommandJournal().size());

        // Not recording yet: nothing captured.
        runOk(model, "go 0,0; face e; write 6;");
        assertEquals(0, model.getCommandJournal().size());

        // Turn recording on and execute editing commands.
        runOk(model, "record on;");
        assertTrue(model.getCommandJournal().isRecording());
        runOk(model, "go 0,0; face e; new st;");
        runOk(model, "go 1,0; face e; new sn;");
        assertEquals(2, model.getCommandJournal().size());

        // Turn it off: further commands are not recorded.
        runOk(model, "record off;");
        assertFalse(model.getCommandJournal().isRecording());
        runOk(model, "go 2,0; face e;");
        assertEquals(2, model.getCommandJournal().size());
    }

    @Test
    @DisplayName("record toggles without argument and clear empties the journal")
    void journal_toggleAndClear() {
        runOk(model, "record on;");
        runOk(model, "go 0,0; face e; write 2;");
        assertEquals(1, model.getCommandJournal().size());

        runOk(model, "record;"); // toggle off
        assertFalse(model.getCommandJournal().isRecording());
        runOk(model, "record;"); // toggle on again
        assertTrue(model.getCommandJournal().isRecording());

        model.getCommandJournal().clear();
        assertEquals(0, model.getCommandJournal().size());
    }

    @Test
    @DisplayName("failed commands are not journaled")
    void journal_ignoresFailedCommands() {
        runOk(model, "record on;");
        String error = PlayerCommandExecutor.execute("del station 99;", model, null, null, null);
        assertNotNull(error, "del of a missing station must fail");
        assertEquals(0, model.getCommandJournal().size());
    }

    // ------------------------------------------------------------------
    // Deterministic replay on a fresh copy
    // ------------------------------------------------------------------

    @Test
    @DisplayName("replaying the journal on a fresh copy of the base world reproduces identical state")
    void replay_journal_onFreshCopy_producesIdenticalState() throws IOException {
        // 1. Virgin base frozen as bytes; two fresh copies from it.
        byte[] baseBytes = serialize(model);
        letrain.mvp.impl.Model recorded = deserialize(baseBytes);
        letrain.mvp.impl.Model replayed = deserialize(baseBytes);

        // 2. Record a representative editing session on the first copy, command by command so the
        //    journal stores individual entries.
        String[] session = {
            "record on;",
            "go 0,0; face e;",
            "write 8;",
            "go 2,0; face e;",
            "new st;",
            "go 3,0; face e;",
            "new sn;",
            "slide sn 1 fw 2;",
            "record off;"
        };
        for (String cmd : session) {
            runOk(recorded, cmd);
        }
        List<String> journal = recorded.getCommandJournal().entries();
        assertTrue(journal.size() >= 4, "expected several journaled commands, got: " + journal);
        assertTrue(journal.stream().noneMatch(c -> c.startsWith("record")),
                "record on/off frames must not be journaled");

        // 3. Replay each journaled command (frames already stripped) on the fresh copy.
        for (String cmd : journal) {
            runOk(replayed, cmd);
        }

        // 4. Both states must be byte-identical.
        assertArrayEquals(serialize(recorded), serialize(replayed),
                "Replaying the journal must reproduce the recorded state");
    }

    @Test
    @DisplayName("journal command reports recording state and lists the entries")
    void journalCommand_listsEntriesViaMessage() {
        // Arrange: record two commands, then turn recording off.
        runOk(model, "record on;");
        runOk(model, "go 0,0; face e;");
        runOk(model, "write 2;");
        runOk(model, "record off;");

        // Act: run 'journal;' capturing the onMessage output (as the 2D/3D console would show it).
        StringBuilder out = new StringBuilder();
        TurtleBuilder builder = new TurtleBuilder(model, headlessMaker(model));
        String error = PlayerCommandExecutor.execute("journal;", model, null, null, builder,
                (title, msg) -> out.append(title).append("\n").append(msg), null);
        assertEquals(null, error, "journal command failed: " + error);

        // Assert: the report shows recording OFF and both entries in order.
        String report = out.toString();
        assertTrue(report.contains("Recording: OFF"), "report must show recording state: " + report);
        assertTrue(report.contains("Entries (2)"), "report must count 2 entries: " + report);
        assertTrue(report.indexOf("go 0,0; face e;") < report.indexOf("write 2;"),
                "entries must appear in recorded order: " + report);
    }
}
