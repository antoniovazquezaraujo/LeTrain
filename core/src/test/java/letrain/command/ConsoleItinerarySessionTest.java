package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #632: the console re-parses every typed statement on its own, so itinerary definitions used
 * to be lost between statements ("Itinerary 'x' not found" even in a single line). These tests pin
 * the session lifetime of the shared registry: definitions survive across the statements of the
 * same world and are never carried over to a model swapped in by undo/redo/load.
 */
@DisplayName("Console session: create itinerary + assign itinerary (#632)")
class ConsoleItinerarySessionTest {

    /** Block-terminated definition; the console appends the missing ';' when the user does not. */
    private static final String CREATE = """
            create itinerary "c" {
                add station 1
                add station 2
            }""";

    private static final String ASSIGN = "assign itinerary \"c\" to train 1;";

    private Model model;
    private final List<String> panelMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station one = new Station(1);
        one.setName("A");
        model.addStation(one);
        Station two = new Station(2);
        two.setName("B");
        model.addStation(two);
        Sensor sensor = new Sensor(1);
        sensor.setName("S");
        model.addSensor(sensor);
        Locomotive loco = new Locomotive(1, "A", "RED");
        Train train = new Train(1);
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        // The panel channel: long or asynchronous notices report through the model's sink (D1).
        model.setUserMessageSink((title, text) -> panelMessages.add(title + ": " + text));
    }

    /** Runs one console line exactly as the UI funnels do (the executor may append ';'). */
    private String console(String line) {
        return consoleOn(model, line);
    }

    /** Runs a console line on a concrete world (used after an undo/load model swap). */
    private String consoleOn(letrain.mvp.Model world, String line) {
        return PlayerCommandExecutor.execute(line, world, null, null, null,
                (title, text) -> panelMessages.add(title + ": " + text), null, null, null, null,
                null, false);
    }

    private static void assertAssigned(letrain.mvp.Model world) {
        Train train = world.getTrainFromLocomotiveId(1);
        assertNotNull(train, "train 1 must exist");
        assertTrue(train.getAutopilot().itinerary().isPresent(),
                "the train must have been assigned the itinerary");
        assertEquals(2, train.getAutopilot().itinerary().orElseThrow().waypoints().size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // The console session registry
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("create in one statement and assign in the next one work in the same session")
    void createThenAssign_inTwoStatements_assignsTheDefinition() {
        assertNull(console(CREATE), "create must not fail");
        assertNull(console(ASSIGN), "assign must not fail");

        assertAssigned(model);
        assertTrue(panelMessages.isEmpty(), "no problem expected: " + panelMessages);
    }

    @Test
    @DisplayName("create and assign inside a single console line work too")
    void createAndAssign_inOneLine_assignsTheDefinition() {
        assertNull(console(CREATE + "; " + ASSIGN));

        assertAssigned(model);
    }

    @Test
    @DisplayName("assign with no previous definition still warns 'not found' (single-statement flow)")
    void assignWithoutDefinition_warnsAndAssignsNothing() {
        assertNull(console(ASSIGN));

        assertTrue(model.getCommandNotice().contains("Itinerary 'c' not found"),
                model.getCommandNotice());
        assertTrue(model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty(),
                "nothing must be assigned without a definition");
    }

    @Test
    @DisplayName("a rejected definition is not stored, so a later assign still warns")
    void rejectedDefinition_isNotStored() {
        String rejected = """
                create itinerary "c" {
                    add station 99
                    add station 2
                }""";
        assertNull(console(rejected));
        assertTrue(model.getCommandNotice().contains("not created"),
                "the reject must warn on the console: " + model.getCommandNotice());

        assertNull(console(ASSIGN));
        assertTrue(model.getCommandNotice().contains("Itinerary 'c' not found"),
                "a rejected plan must never be assignable: " + model.getCommandNotice());
        assertTrue(model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty());
    }

    @Test
    @DisplayName("a rejected redefinition retires the previous definition (no stale plan)")
    void rejectedRedefinition_retiresThePreviousDefinition() {
        assertNull(console(CREATE), "the first definition must be accepted");

        String rejected = """
                create itinerary "c" {
                    add station 99
                    add station 2
                }""";
        assertNull(console(rejected));
        assertTrue(model.getCommandNotice().contains("not created"),
                "the redefine must warn: " + model.getCommandNotice());

        assertNull(console(ASSIGN));
        assertTrue(model.getCommandNotice().contains("Itinerary 'c' not found"),
                "the old plan must not stay assignable: " + model.getCommandNotice());
        assertTrue(model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty(),
                "nothing may be assigned after the reject");
    }

    @Test
    @DisplayName("a redefinition with fewer than two waypoints retires the previous definition too")
    void invalidRedefinition_retiresThePreviousDefinition() {
        assertNull(console(CREATE), "the first definition must be accepted");

        assertNull(console("create itinerary \"c\" { add station 1 }"));
        assertTrue(panelMessages.stream().anyMatch(m -> m.contains("invalid")),
                "the redefine must warn (long notice -> panel): " + panelMessages);

        assertNull(console(ASSIGN));
        assertTrue(model.getCommandNotice().contains("Itinerary 'c' not found"),
                "the old plan must not stay assignable: " + model.getCommandNotice());
        assertTrue(model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty(),
                "nothing may be assigned after the reject");
    }

    @Test
    @DisplayName("the shared manager keeps the D1 channels: a long notice opens the panel")
    void longNotice_stillOpensThePanel() {
        // Two unknown destinations: the combined notice is over the command-bar limit.
        String twoProblems = """
                create itinerary "c" {
                    add station 99
                    add sensor 98
                    add station 2
                }""";
        assertNull(console(twoProblems));

        assertTrue(model.getCommandNotice().isEmpty(),
                "a long notice must not be duplicated on the command bar: "
                        + model.getCommandNotice());
        assertTrue(panelMessages.stream().anyMatch(
                m -> m.contains("station 99 not found") && m.contains("sensor 98 not found")),
                panelMessages.toString());
    }

    @Test
    @DisplayName("a trigger registered earlier still reports to the panel when it fires later")
    void deferredTriggerNotice_afterLaterStatements_stillUsesThePanel() {
        // Registration happens in one statement; a later statement rebinds the session manager's
        // channels. The asynchronous warning must still reach the panel (D1), never the console.
        assertNull(console("sensor 1 on train enter { semaphore 99 set closed; };"));
        assertNull(console("time set 8;"));

        model.getSensor(1).onEnterTrain(model.getTrainFromLocomotiveId(1));

        assertTrue(panelMessages.stream().anyMatch(m -> m.contains("Semaphore 99 not found")),
                "the fired trigger's warning must reach the panel: " + panelMessages);
        assertTrue(model.getCommandNotice().isEmpty(),
                "an asynchronous notice is not console feedback: " + model.getCommandNotice());
    }

    // ═══════════════════════════════════════════════════════════════════
    // The registry follows the live model (applyModel / undo / redo)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a swapped-in world starts a fresh registry bound to the new model")
    void modelSwap_startsFreshRegistryBoundToTheNewWorld() {
        GameSaveService saves = new GameSaveService();
        assertNull(console(CREATE), "create must not fail");
        byte[] snapshot = saves.toBytes(model); // world before the assignment
        assertNull(console(ASSIGN), "assign must not fail");
        assertAssigned(model);

        // applyModel (undo/load): the presenter keeps running on a new model instance.
        Model swapped = saves.fromBytes(snapshot);
        assertNotNull(swapped);
        assertTrue(swapped.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty(),
                "the snapshot predates the assignment");

        // The definition belonged to the discarded world: it must not leak into the new one.
        assertNull(consoleOn(swapped, ASSIGN));
        assertTrue(swapped.getCommandNotice().contains("Itinerary 'c' not found"),
                swapped.getCommandNotice());
        assertTrue(swapped.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isEmpty());

        // A new session on the swapped world builds its own registry.
        assertNull(consoleOn(swapped, CREATE));
        assertNull(consoleOn(swapped, ASSIGN));
        assertAssigned(swapped);

        // Returning to the original world rebinds the session to it: a new round works end to end.
        assertNull(console(CREATE), "the session must rebind to the original world");
        assertNull(console(ASSIGN));
        assertAssigned(model);
    }

    @Test
    @DisplayName("undo/redo replay re-creates the definition on the restored model")
    void undoRedo_replay_keepsCreateAndAssignCoherent() {
        GameSaveService saves = new GameSaveService();
        UndoRedoHistory history = new UndoRedoHistory(new UndoRedoHistory.Codec() {
            @Override
            public byte[] toBytes(letrain.mvp.Model world) {
                return saves.toBytes(world);
            }

            @Override
            public letrain.mvp.Model fromBytes(byte[] data) {
                return saves.fromBytes(data);
            }
        });
        history.begin(model);

        // A two-statement console session: both edits are recorded for undo/redo.
        assertNull(console(CREATE));
        history.record(CREATE);
        assertNull(console(ASSIGN));
        history.record(ASSIGN);
        assertEquals(2, history.size());

        // Undo both edits: restore the checkpoint base as a new model (applyModel) and replay the
        // empty slice [0, 0), exactly as the presenter does.
        UndoRedoHistory.UndoPlan undo = history.planUndo(2);
        assertNotNull(undo);
        letrain.mvp.Model restored = history.restore(undo);
        assertNotNull(restored, "undo must restore the checkpoint base");
        history.bind(restored);
        history.commit(undo.target());
        assertEquals(0, history.applied());

        // The restored world must not inherit the discarded definition.
        assertNull(consoleOn(restored, ASSIGN));
        assertTrue(restored.getCommandNotice().contains("Itinerary 'c' not found"),
                restored.getCommandNotice());

        // Redo replays create + assign as two console commands on the restored model: the second
        // one must find what the first created in the new session.
        UndoRedoHistory.UndoPlan redo = history.planRedo(2);
        assertNotNull(redo);
        for (String cmd : redo.commandsToReplay(history.entries())) {
            assertNull(consoleOn(restored, cmd), "replay failed: " + cmd);
        }
        history.commit(redo.target());

        assertAssigned(restored);
    }
}
