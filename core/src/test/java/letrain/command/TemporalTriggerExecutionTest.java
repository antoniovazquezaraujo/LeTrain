package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.SimulationController;
import letrain.time.GameTime;
import letrain.time.TemporalTrigger;
import letrain.time.TemporalTriggerService;
import letrain.track.RailSemaphore;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 3, F3b: execution, save/load, replay and rejected actions of the temporal triggers
 * (contract D1-D7). The block actions run through the same deferred runner as the event triggers;
 * fires are computed from game ticks only, so every test is deterministic.
 */
@DisplayName("Temporal trigger execution (ADR-022 phase 3, F3b)")
class TemporalTriggerExecutionTest {

    private Model model;
    private RailSemaphore semaphore;
    private final List<String> notices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = newModel();
        semaphore = (RailSemaphore) model.getSemaphore(1);
    }

    /** A world with semaphore 1 and the visible channel wired (D1 panel notices). */
    private Model newModel() {
        Model created = new Model(1);
        created.postLoadInit();
        created.addSemaphore(new RailSemaphore(1));
        created.setUserMessageSink((title, text) -> notices.add(title + ": " + text));
        return created;
    }

    /** Mirrors SimulationController order: scheduler first, then the game clock. */
    private static void runTicks(Model target, int count) {
        for (int i = 0; i < count; i++) {
            target.getScheduler().tick();
            target.getGameClock().tick();
        }
    }

    private void runTicks(int count) {
        runTicks(model, count);
    }

    private TemporalTriggerService service() {
        return model.getTemporalTriggerService();
    }

    private boolean warned(String fragment) {
        return notices.stream().anyMatch(n -> n.contains(fragment));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Execution on the tick the clock crosses the threshold
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execution")
    class Execution {

        @Test
        @DisplayName("`at` fires at its game time and repeats every day")
        void at_firesAtTheGameTime_andDaily() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("at 10:30 {\n  semaphore 1 open;\n}");

            runTicks(599); // 10:29 and change: the threshold has not been crossed
            assertFalse(semaphore.isOpen(), "nothing fires before 10:30");

            runTicks(2); // the clock crosses 10:30 and the due fire runs
            assertTrue(semaphore.isOpen(), "the 10:30 action must run");

            semaphore.setOpen(false);
            runTicks(28800); // one full game day
            assertTrue(semaphore.isOpen(), "`at` must fire again the next day");
        }

        @Test
        @DisplayName("`every` fires on the fixed grid and re-arms the next point")
        void every_firesRepeatedlyOnTheGrid() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");

            runTicks(460); // 10:30: the armed fire lands one scheduler tick early and re-arms
            assertFalse(semaphore.isOpen(), "the 10:30 grid point has not run yet");

            runTicks(1);
            assertTrue(semaphore.isOpen(), "10:30: the first grid point runs");

            semaphore.setOpen(false);
            runTicks(599);
            assertFalse(semaphore.isOpen(), "still before 11:00");
            runTicks(2);
            assertTrue(semaphore.isOpen(), "11:00: the next grid point runs");
        }

        @Test
        @DisplayName("`train at <place>` resolves the train at the place when the block fires")
        void trainAtPlace_targetsTheTrain() {
            Train resting = addStationWithTrain(1, "Mine");
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("at 10:30 {\n  train at station 1 set speed 3;\n}");

            runTicks(601);

            assertEquals(3, ((Locomotive) resting.getDirectorLinker()).getTargetSpeed(),
                    "the action must target the train resting at the station");
        }

        @Test
        @DisplayName("paused editing freezes the clock: fires do not accumulate and run after resume")
        void pausedEditing_doesNotAccumulateFires() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("at 10:30 {\n  semaphore 1 open;\n}");
            model.setMode(GameMode.RAILS);
            model.setPauseEditing(true);
            SimulationController controller = new SimulationController(model, null, null);

            for (int i = 0; i < 5000; i++) {
                controller.tick();
            }

            assertFalse(semaphore.isOpen(), "a frozen clock must not release temporal triggers");
            assertEquals(new GameTime(1, 10, 0), model.getGameClock().now(),
                    "the clock must stay where the pause caught it");

            model.setPauseEditing(false);
            for (int i = 0; i < 601; i++) {
                controller.tick();
            }
            assertTrue(semaphore.isOpen(),
                    "after resuming, the trigger fires when the clock reaches its time");
            assertEquals(new GameTime(1, 10, 30), model.getGameClock().now(),
                    "the pause must not have advanced the clock");
        }

        @Test
        @DisplayName("a duplicate is warned and only the first registered block runs")
        void duplicate_onlyTheFirstBlockRuns() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("""
                    at 10:30 {
                        semaphore 1 open;
                    }
                    at 10:30 {
                        semaphore 1 close;
                    }
                    """);

            runTicks(601);

            assertTrue(warned("Duplicate temporal trigger 'at 10:30'"), notices.toString());
            assertTrue(semaphore.isOpen(),
                    "the second (ignored) block must never execute its close action");
        }

        @Test
        @DisplayName("a generic train action is rejected with a visible notice; the rest of the block runs")
        void genericTrainAction_warnsAndIsSkipped() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("""
                    at 10:30 {
                        train set speed 5;
                        semaphore 1 open;
                    }
                    """);

            runTicks(601);

            assertTrue(warned("not allowed in a temporal trigger"), notices.toString());
            assertTrue(semaphore.isOpen(),
                    "the allowed actions of the block must still run after a rejection");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Save/load: no catch-up
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Save/load without catch-up")
    class SaveLoad {

        private final GameSaveService saves = new GameSaveService();

        @Test
        @DisplayName("loading with a past `at` time schedules it for the next day")
        void atTimeAlreadyPassed_waitsForTomorrow() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));
            model.setProgram("at 6:30 {\n  semaphore 1 open;\n}");

            Model loaded = saves.fromBytes(saves.toBytes(model));

            TemporalTrigger at = TemporalTrigger.at(LocalTime.of(6, 30));
            assertEquals(24460, loaded.getTemporalTriggerService().ticksUntilNextFire(at),
                    "6:30 already passed: the next occurrence is tomorrow, no catch-up");

            runTicks(loaded, 24459);
            assertFalse(((RailSemaphore) loaded.getSemaphore(1)).isOpen(),
                    "nothing may fire before tomorrow 6:30");
            assertEquals(new GameTime(2, 6, 29), loaded.getGameClock().now(),
                    "the loaded clock continues from the saved instant");

            runTicks(loaded, 2);
            assertTrue(((RailSemaphore) loaded.getSemaphore(1)).isOpen(),
                    "the trigger fires at the next daily occurrence");
        }

        @Test
        @DisplayName("loading with `every` continues from the current clock, not from lost hours")
        void every_continuesFromTheCurrentClock() {
            model.getGameClock().setTime(new GameTime(1, 10, 7));
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");

            Model loaded = saves.fromBytes(saves.toBytes(model));

            assertEquals(460, loaded.getTemporalTriggerService().ticksUntilNextFire(
                    TemporalTrigger.every(30)), "the next grid point from 10:07 is 10:30");

            RailSemaphore loadedSemaphore = (RailSemaphore) loaded.getSemaphore(1);
            runTicks(loaded, 461);
            assertTrue(loadedSemaphore.isOpen(), "10:30 grid point");

            loadedSemaphore.setOpen(false);
            runTicks(loaded, 600);
            assertTrue(loadedSemaphore.isOpen(), "11:00 grid point");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Replay, snapshot restore and program replace
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Replay and program replace")
    class Replay {

        @Test
        @DisplayName("a replaced program invalidates its armed fires (generation no-ops)")
        void replacedProgram_oldFiresAreNoOps() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");
            // Same trigger shape, but a fresh generation with an empty block: only the old fire
            // could open the semaphore, and it must be a no-op.
            model.setProgram("every 30m {\n  \n}");

            runTicks(601);

            assertFalse(semaphore.isOpen(), "the fire armed for the replaced program must not run");
        }

        @Test
        @DisplayName("an empty program clears the registry and its armed fires")
        void clearedProgram_firesAreNoOps() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("every 30m {\n  semaphore 1 open;\n}");
            model.setProgram("");

            runTicks(601);

            assertTrue(service().triggers().isEmpty());
            assertFalse(semaphore.isOpen(), "a cleared program must not leave live fires behind");
        }

        @Test
        @DisplayName("restoring a snapshot re-registers the triggers and replays the same fires")
        void snapshotRestore_replaysTheSameFires() {
            model.getGameClock().setTime(new GameTime(1, 10, 0));
            model.setProgram("at 10:30 {\n  semaphore 1 open;\n}");
            GameSaveService saves = new GameSaveService();
            byte[] checkpoint = saves.toBytes(model);

            runTicks(601);
            assertTrue(semaphore.isOpen());

            Model replayed = saves.fromBytes(checkpoint);
            runTicks(replayed, 601);

            assertEquals(model.getGameClock().now(), replayed.getGameClock().now());
            assertTrue(((RailSemaphore) replayed.getSemaphore(1)).isOpen(),
                    "the restored world must fire the same action at the same game time");
        }

        @Test
        @DisplayName("journal replay of the same statements is deterministic")
        void journalReplay_isDeterministic() {
            Model first = newModel();
            Model second = newModel();
            List<String> journal =
                    List.of("at 6:00 { semaphore 1 open; }", "every 30m { semaphore 1 open; }");

            for (Model target : List.of(first, second)) {
                target.getGameClock().setTime(new GameTime(1, 10, 7));
                for (String statement : journal) {
                    assertNull(PlayerCommandExecutor.execute(statement, target), statement);
                }
                runTicks(target, 1200); // 10:07 -> 11:07: the 10:30 and 11:00 grid points fire
            }

            assertTrue(((RailSemaphore) first.getSemaphore(1)).isOpen(),
                    "the replayed `every` statement must have fired");
            assertEquals(first.getGameClock().now(), second.getGameClock().now());
            assertEquals(first.getTemporalTriggerService().triggers(),
                    second.getTemporalTriggerService().triggers());
            assertEquals(((RailSemaphore) first.getSemaphore(1)).isOpen(),
                    ((RailSemaphore) second.getSemaphore(1)).isOpen(),
                    "both replays must reach the same semaphore state");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    /** A station with a stopped train resting on it (engine on, target speed 0). */
    private Train addStationWithTrain(int stationId, String name) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(0, 0));
        track.addRoute(Dir.W, Dir.E);
        track.addRoute(Dir.E, Dir.W);
        model.getRailMap().addTrack(new Point(0, 0), track);
        Station station = new Station(stationId);
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);

        Locomotive loco = new Locomotive(1, "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(1);
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        track.enterLinkerFromDir(Dir.W, loco);
        return train;
    }
}
