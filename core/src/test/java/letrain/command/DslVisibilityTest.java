package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.itinerary.TrainMission;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.track.Sensor;
import letrain.track.SpeedSignal;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * D1 (batch 1, visibility): every DSL problem warns on the visible channel (never log-only) and a
 * syntax error executes nothing. Covers console orders, triggers, itineraries/waypoints, naming,
 * turtle steps, {@code info}, speed clamps, program validation and waypoint mission notices.
 *
 * <p>
 * Contextual channel (D1): a typed command warns on the console (short command-line notice, long
 * texts open the panel); asynchronous events (programs, triggers firing, missions) always report to
 * the panel.
 */
@DisplayName("D1 batch 1: DSL problem visibility")
class DslVisibilityTest {

    private Model model;
    private Train train;
    private List<String> messages;

    /** Short notices produced by typed commands (the console command-line channel). */
    private final List<String> consoleNotices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station station = new Station(1);
        station.setName("A");
        model.addStation(station);
        Sensor sensor = new Sensor(1);
        sensor.setName("S1");
        model.addSensor(sensor);
        Locomotive loco = new Locomotive(1, "A");
        train = new Train(1);
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        messages = new ArrayList<>();
        // The panel channel: async notices (triggers, missions, programs) resolve it at fire time.
        model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));
    }

    /** Runs a console script capturing the visible message channel (the game console). */
    private String run(String script) {
        String error = PlayerCommandExecutor.execute(script, model, null, null, null,
                (title, text) -> messages.add(title + ": " + text), null, null, null, null, null,
                false);
        captureConsoleNotice();
        return error;
    }

    private String runWithTurtle(String script) {
        String error = PlayerCommandExecutor.execute(script, model, null, null,
                new TurtleBuilder(model, headlessMaker(model)),
                (title, text) -> messages.add(title + ": " + text), null, null, null, null, null,
                false);
        captureConsoleNotice();
        return error;
    }

    /** Collects the command-line notice the typed command left (empty for silent successes). */
    private void captureConsoleNotice() {
        String notice = model.getCommandNotice();
        if (notice != null && !notice.isEmpty()) {
            consoleNotices.add(notice);
        }
    }

    private static RailTrackMaker headlessMaker(Model m) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(m);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new RailTrackMaker(presenter);
    }

    private boolean warned(String fragment) {
        return consoleWarned(fragment) || panelWarned(fragment);
    }

    /** True when the short console channel carried {@code fragment}. */
    private boolean consoleWarned(String fragment) {
        return consoleNotices.stream().anyMatch(m -> m.contains(fragment));
    }

    /** True when the scrollable panel carried {@code fragment}. */
    private boolean panelWarned(String fragment) {
        return messages.stream().anyMatch(m -> m.contains(fragment));
    }

    private ForkRailTrack addFork() {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(0, 0));
        fork.setCreationDir(Dir.E);
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.S);
        fork.addRoute(Dir.S, Dir.W);
        fork.setNormalRoute();
        model.getRailMap().addTrack(fork.getPosition(), fork);
        model.addFork(fork);
        return fork;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Direct orders
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unknown entities in direct orders")
    class DirectOrders {

        @Test
        @DisplayName("train N with an unknown id warns for speed, stop and autopilot orders")
        void unknownTrain_warns() {
            run("train 99 set speed 3;");
            run("train 99 stop at sensor 1;");
            run("train 99 set autopilot true;");

            assertEquals(3, consoleNotices.size(), consoleNotices.toString());
            assertTrue(messages.isEmpty(),
                    "typed short notices must not open the panel: " + messages);
            assertTrue(consoleWarned("Train 99 not found"), consoleNotices.toString());
        }

        @Test
        @DisplayName("station/sensor invert with an unknown id warns")
        void unknownStationAndSensorInvert_warns() {
            run("station 99 invert;");
            run("sensor 99 invert;");

            assertTrue(consoleWarned("Station 99 not found"), consoleNotices.toString());
            assertTrue(consoleWarned("Plain sensor 99 not found"), consoleNotices.toString());
            assertTrue(messages.isEmpty(), messages.toString());
        }

        @Test
        @DisplayName("fork/semaphore/signal with an unknown id warns")
        void unknownForkSemaphoreSignal_warns() {
            run("fork 99 flip;");
            run("semaphore 99 open;");
            run("signal 99 invert;");

            assertTrue(consoleWarned("Fork 99 not found"), consoleNotices.toString());
            assertTrue(consoleWarned("Semaphore 99 not found"), consoleNotices.toString());
            assertTrue(consoleWarned("Signal 99 not found"), consoleNotices.toString());
        }

        @Test
        @DisplayName("a valid order stays silent (success says nothing)")
        void validOrder_isSilent() {
            String error = run("train 1 set speed 3;");

            assertNull(error, error);
            assertTrue(messages.isEmpty(), "unexpected notices: " + messages);
            assertTrue(model.getCommandNotice().isEmpty(),
                    "a silent success must clear the command line: " + model.getCommandNotice());
        }

        @Test
        @DisplayName("set speed outside 0..10 warns and applies the clamped value")
        void speedClamp_warns() {
            Locomotive loco = (Locomotive) train.getDirectorLinker();

            run("train 1 set speed 99;");
            assertEquals(10, loco.getTargetSpeed());
            assertTrue(consoleWarned("out of range 0-10"), consoleNotices.toString());

            messages.clear();
            consoleNotices.clear();
            run("train 1 set speed -4;");
            assertEquals(0, loco.getTargetSpeed());
            assertTrue(consoleWarned("out of range 0-10"), consoleNotices.toString());
        }

        @Test
        @DisplayName("a mission speed outside 0..10 warns too")
        void missionSpeedClamp_warns() {
            run("train 1 stop at end speed 99;");

            // The clamp warning and the failed end-of-track mission share the command feedback;
            // their joined set is long, so it opens the panel.
            assertTrue(warned("Mission speed 99"),
                    "console: " + consoleNotices + " panel: " + messages);
        }

        @Test
        @DisplayName("fork set <direction> that maps to no route warns instead of doing nothing")
        void forkDirectionWithNoRoute_warns() {
            addFork();

            run("fork 1 set n;");

            assertTrue(consoleWarned("no route towards n"), consoleNotices.toString());
        }

        @Test
        @DisplayName("fork set <direction> with a single-route fork warns instead of NPE (O4)")
        void singleRouteForkDirection_warnsInsteadOfNpe() {
            // A fork with one route has no original/alternative pair: getOriginalRoute() is null.
            ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
            fork.setPosition(new Point(0, 0));
            fork.setCreationDir(Dir.E);
            fork.addRoute(Dir.W, Dir.E);
            fork.setNormalRoute();
            model.getRailMap().addTrack(fork.getPosition(), fork);
            model.addFork(fork);

            String error = run("fork 1 set n;");

            assertNull(error, "the order must not crash: " + error);
            assertTrue(consoleWarned("Fork 1 has no route towards n; unchanged"),
                    consoleNotices.toString());
        }

        @Test
        @DisplayName("stop at sensor N resolving to a speed signal warns (shared id)")
        void stopAtSensorResolvingToSpeedSignal_warns() {
            model.addSensor(new SpeedSignal(7, Dir.E, 3, true));

            run("train 1 stop at sensor 7;");

            // The text is over 60 chars: it opens the panel instead of the command line.
            assertTrue(panelWarned("not a plain sensor"), messages.toString());
            assertTrue(model.getCommandNotice().isEmpty(), model.getCommandNotice());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Triggers
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unknown selectors when registering a trigger")
    class Triggers {

        @Test
        @DisplayName("sensor/fork/semaphore/station selectors that do not exist warn at registration")
        void unknownTriggerSelector_warns() {
            run("sensor 99 on train enter { semaphore 1 open; };");
            run("fork 99 on train enter { semaphore 1 open; };");
            run("semaphore 99 on train enter { semaphore 1 open; };");
            run("station 99 on train enter { semaphore 1 open; };");

            assertEquals(4, consoleNotices.size(), consoleNotices.toString());
            assertTrue(messages.isEmpty(), "registration problems are typed feedback: " + messages);
            assertTrue(consoleWarned("Sensor 99 not found; trigger ignored"),
                    consoleNotices.toString());
            assertTrue(consoleWarned("Fork 99 not found; trigger ignored"),
                    consoleNotices.toString());
            assertTrue(consoleWarned("Semaphore 99 not found; trigger ignored"),
                    consoleNotices.toString());
            assertTrue(consoleWarned("Station 99 not found; trigger ignored"),
                    consoleNotices.toString());
        }

        @Test
        @DisplayName("an existing selector registers silently")
        void existingTrigger_isSilent() {
            String error = run("sensor 1 on train enter { semaphore 1 open; };");

            assertNull(error, error);
            assertTrue(messages.isEmpty(), "unexpected notices: " + messages);
            assertTrue(consoleNotices.isEmpty(), "unexpected notices: " + consoleNotices);
        }

        @Test
        @DisplayName("'train at' with no train at the place names the selector readably (O1)")
        void trainAtWithoutTrain_warnsReadableSelector() {
            // Repro from the review: the notice used to concatenate tokens ("No train at
            // station1").
            assertNull(run("sensor 1 on train enter { train at station 1 set speed 2; };"));

            model.getSensor(1).onEnterTrain(train);

            // The trigger fired after the command: asynchronous, so it reports to the panel.
            assertTrue(panelWarned("No train at station 1; action ignored"), messages.toString());
            assertFalse(messages.stream().anyMatch(m -> m.contains("station1")),
                    "tokens must not be glued: " + messages);
            assertTrue(consoleNotices.isEmpty(),
                    "a notice fired later is not console feedback: " + consoleNotices);
        }

        @Test
        @DisplayName("semaphore open/close/invert inside a block no longer NPE and act on firing")
        void semaphoreActionsInBlock_work() {
            letrain.track.RailSemaphore semaphore =
                    new letrain.track.RailSemaphore(model.nextSemaphoreId());
            semaphore.setCreationDir(Dir.E);
            semaphore.setOpen(false);
            model.addSemaphore(semaphore);

            // §6 gap: `semaphore N open` (bare status) used to throw NPE while building the block.
            assertNull(run("sensor 1 on train enter { semaphore 1 open; };"));
            model.getSensor(1).onEnterTrain(train);
            assertTrue(semaphore.isOpen(), "the block must open the semaphore");

            assertNull(run("sensor 1 on train exit { semaphore 1 set closed; };"));
            model.getSensor(1).onExitTrain(train);
            assertFalse(semaphore.isOpen(), "the block must close the semaphore");

            Dir original = semaphore.getCreationDir();
            assertNull(run("sensor 1 on train exit { semaphore 1 invert; };"));
            model.getSensor(1).onExitTrain(train);
            assertEquals(original.inverse(), semaphore.getCreationDir());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Itineraries and waypoints
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Waypoints with unknown destinations")
    class Itineraries {

        @Test
        @DisplayName("console: an itinerary with a missing station is not created and warns")
        void consoleUnknownWaypoint_rejectsItinerary() {
            String error = run("create itinerary \"x\" { add station 99; add station 1; }");

            assertNull(error, error);
            assertTrue(consoleWarned("Itinerary 'x' not created: station 99 not found"),
                    consoleNotices.toString());
            assertTrue(messages.isEmpty(), messages.toString());
        }

        @Test
        @DisplayName("program: the reject is visible and the itinerary cannot be assigned")
        void programUnknownWaypoint_rejectsItinerary() {
            model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));

            List<String> errors = model.setProgram("""
                    create itinerary "x" {
                        add station 99
                        add station 1
                    }
                    assign itinerary "x" to train 1;
                    """);

            assertTrue(errors.isEmpty(), "no syntax errors expected: " + errors);
            assertTrue(warned("Itinerary 'x' not created: station 99 not found"),
                    messages.toString());
            assertTrue(warned("Itinerary 'x' not found"), messages.toString());
            assertTrue(train.getAutopilot().itinerary().isEmpty(),
                    "a plan with a missing stop must not be assigned");
        }

        @Test
        @DisplayName("a waypoint action with an unknown mission target also rejects the itinerary")
        void waypointActionWithUnknownTarget_rejectsItinerary() {
            model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));

            List<String> errors = model.setProgram("""
                    create itinerary "y" {
                        add station 1 stop at sensor 99 speed 2
                        add station 1
                    }
                    """);

            assertTrue(errors.isEmpty(), "no syntax errors expected: " + errors);
            assertTrue(warned("Sensor not found in 'stop at' order"), messages.toString());
            assertTrue(warned("Itinerary 'y' not created"), messages.toString());
        }

        @Test
        @DisplayName("a valid itinerary is still created and assigned")
        void validItinerary_stillWorks() {
            Station two = new Station(2);
            two.setName("B");
            model.addStation(two);

            List<String> errors = model.setProgram("""
                    create itinerary "ok" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "ok" to train 1;
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(train.getAutopilot().itinerary().isPresent());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Names
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("set name")
    class Names {

        @Test
        @DisplayName("st/sn aliases rename the station/sensor (token comparison bug)")
        void aliasesRename() {
            run("st 1 set name \"Renamed\";");
            run("sn 1 set name \"S2\";");

            assertEquals("Renamed", model.getStation(1).getName());
            assertEquals("S2", model.getSensor(1).getName());
            assertTrue(messages.isEmpty(), "unexpected notices: " + messages);
            assertTrue(consoleNotices.isEmpty(), "unexpected notices: " + consoleNotices);
        }

        @Test
        @DisplayName("renaming by the old name still works")
        void renameByOldNameWorks() {
            run("station \"A\" set name \"B\";");

            assertEquals("B", model.getStation(1).getName());
        }

        @Test
        @DisplayName("train N set name works as a direct order and inside a block")
        void trainRenameDirectAndInBlock() {
            String error = run("train 1 set name \"T1\";");
            assertNull(error, error);
            assertEquals("T1", train.getName());

            error = run("sensor 1 on train enter { train 1 set name \"Block\"; };");
            assertNull(error, error);
            model.getSensor(1).onEnterTrain(train);
            assertEquals("Block", train.getName());

            assertTrue(messages.isEmpty(), "unexpected notices: " + messages);
        }

        @Test
        @DisplayName("renaming an unknown station warns")
        void unknownStationRename_warns() {
            run("st 99 set name \"X\";");

            assertTrue(consoleWarned("Station 99 not found; name unchanged"),
                    consoleNotices.toString());
        }

        @Test
        @DisplayName("semaphores and signals cannot be renamed: the notice is visible")
        void semaphoreAndSignalRename_warn() {
            run("sm 1 set name \"X\";");
            run("sg 1 set name \"X\";");

            assertTrue(consoleWarned("Semaphores cannot be renamed"), consoleNotices.toString());
            assertTrue(consoleWarned("Signals cannot be renamed"), consoleNotices.toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Turtle, info, time
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Console side commands")
    class SideCommands {

        @Test
        @DisplayName("turtle steps with a name/mark warn instead of being ignored in silence")
        void turtleNamedStep_warns() {
            String error = runWithTurtle("write 1, m foo, 2;");

            assertNull(error, error);
            assertTrue(warned("ignored: named steps/marks are not implemented"),
                    "console: " + consoleNotices + " panel: " + messages);
        }

        @Test
        @DisplayName("uppercase turtle steps are identifiers: they warn instead of moving silently")
        void turtleUppercaseStep_warns() {
            String error = runWithTurtle("write 1, L;");

            assertNull(error, error);
            assertTrue(warned("ignored: named steps/marks are not implemented"),
                    "console: " + consoleNotices + " panel: " + messages);
        }

        @Test
        @DisplayName("info N without a type says the reference is ignored")
        void infoWithoutType_warns() {
            String error = run("info 5;");

            assertNull(error, error);
            // `info` is data output, not a problem notice: its report keeps the panel.
            assertTrue(panelWarned("'info 5' ignores the reference"), messages.toString());
        }

        @Test
        @DisplayName("time set outside the clock range warns on the panel (long text)")
        void invalidTime_warns() {
            String error = run("time set 25:99;");

            assertNull(error, error);
            assertTrue(panelWarned("Invalid time 25:99"), messages.toString());
            assertTrue(model.getCommandNotice().isEmpty(),
                    "a long notice must not duplicate on the command line: "
                            + model.getCommandNotice());
            assertEquals(8, model.getGameClock().now().hour());
            assertEquals(0, model.getGameClock().now().minute());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contextual channel: typed command vs asynchronous event
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Contextual warning channel (typed command vs async event)")
    class ContextualChannels {

        @Test
        @DisplayName("a typed command's short warning goes to the command line, not the panel")
        void typedShortNotice_goesToConsole() {
            run("train 99 set speed 3;");

            assertEquals("Train 99 not found; order ignored", model.getCommandNotice());
            assertTrue(messages.isEmpty(),
                    "a short typed notice must not open the panel: " + messages);
        }

        @Test
        @DisplayName("the short notice is cleared by a later silent success")
        void silentSuccess_clearsTheNotice() {
            run("train 99 set speed 3;");
            assertFalse(model.getCommandNotice().isEmpty());

            run("train 1 set speed 3;");

            assertTrue(model.getCommandNotice().isEmpty(),
                    "the notice must not outlive the command: " + model.getCommandNotice());
            assertTrue(messages.isEmpty(), messages.toString());
        }

        @Test
        @DisplayName("a typed command's long warning opens the panel and is not repeated")
        void typedLongNotice_opensThePanel() {
            run("train 99 set speed 3; time set 25:99;");

            assertTrue(model.getCommandNotice().isEmpty(),
                    "a long notice must not duplicate on the line: " + model.getCommandNotice());
            assertEquals(1, messages.size(),
                    "the whole set is one panel message: " + messages.toString());
            assertTrue(panelWarned("Train 99 not found"), messages.toString());
            assertTrue(panelWarned("Invalid time 25:99"), messages.toString());
        }

        @Test
        @DisplayName("two short warnings whose set does not fit the line open the panel")
        void accumulatedShortNoticeSet_opensThePanel() {
            run("train 98 set speed 3; train 99 set speed 3;");

            assertTrue(model.getCommandNotice().isEmpty(),
                    "the joined set is over 60 chars: " + model.getCommandNotice());
            assertEquals(1, messages.size(), messages.toString());
            assertTrue(panelWarned("Train 98 not found") && panelWarned("Train 99 not found"),
                    messages.toString());
        }

        @Test
        @DisplayName("a mission target missing when the typed order runs warns on the command line")
        void typedMissionProblem_goesToConsole() {
            run("train 1 stop at sensor 99 speed 2;");

            assertTrue(consoleWarned("Sensor not found in 'stop at' order"),
                    consoleNotices.toString());
            assertTrue(messages.isEmpty(), messages.toString());
        }

        @Test
        @DisplayName("program warnings keep reporting to the panel, never to the command line")
        void programNotices_stayOnThePanel() {
            List<String> errors = model.setProgram("train 99 set speed 3;");

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(panelWarned("Train 99 not found"), messages.toString());
            assertTrue(model.getCommandNotice().isEmpty(),
                    "a program is async: " + model.getCommandNotice());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // program {}
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("program {} validation and notices")
    class Program {

        @Test
        @DisplayName("a syntax error lists line/column and executes nothing (not even valid lines)")
        void syntaxError_executesNothing() {
            List<String> errors = model.setProgram("train 1 set speed 4;\ntrain 1 set speed;");

            assertFalse(errors.isEmpty(), "the syntax error must be reported");
            assertTrue(errors.get(0).contains("line 2"), errors.toString());
            assertEquals(0, ((Locomotive) train.getDirectorLinker()).getTargetSpeed(),
                    "nothing may execute when the program does not parse");
        }

        @Test
        @DisplayName("lexer errors are reported with line/column too")
        void lexerError_isReported() {
            List<String> errors = model.setProgram("train 1 set speed 4;\n# comment");

            assertFalse(errors.isEmpty(), "the lexer error must be reported");
            assertTrue(errors.stream().anyMatch(e -> e.contains("line 2")), errors.toString());
            assertEquals(0, ((Locomotive) train.getDirectorLinker()).getTargetSpeed());
        }

        @Test
        @DisplayName("a broken program leaves the previously applied automation in place")
        void brokenProgram_keepsPreviousAutomation() {
            Station two = new Station(2);
            two.setName("B");
            model.addStation(two);
            model.setProgram("""
                    create itinerary "ok" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "ok" to train 1;
                    """);
            assertTrue(train.getAutopilot().itinerary().isPresent());

            List<String> errors = model.setProgram("create itinerary \"broken\" {");

            assertFalse(errors.isEmpty(), errors.toString());
            assertTrue(train.getAutopilot().itinerary().isPresent(),
                    "the old automation must survive an invalid program (nothing is applied)");
        }

        @Test
        @DisplayName("program warnings reach the model's visible sink")
        void programWarnings_areVisible() {
            model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));

            List<String> errors = model.setProgram("train 99 set speed 3;");

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(warned("Train 99 not found"), messages.toString());
        }

        @Test
        @DisplayName("a mission rejected after a program-assigned itinerary warns on the visible sink")
        void waypointMissionNotifier_isInstalled() {
            Station two = new Station(2);
            two.setName("B");
            model.addStation(two);
            model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));
            List<String> errors = model.setProgram("""
                    create itinerary "ok" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "ok" to train 1;
                    """);
            assertTrue(errors.isEmpty(), errors.toString());

            boolean started = train.getAutopilot()
                    .startMission(TrainMission.forItinerary(TrainMission.Kind.SENSOR, 99, 2));

            assertFalse(started, "a mission to an unknown sensor cannot start");
            assertTrue(warned("sensor 99 not found"),
                    "the mission notice must reach the game console, got: " + messages);
        }

        @Test
        @DisplayName("'train  at' with two spaces is a visible syntax error (exact-token gap)")
        void doubleSpaceTrainAt_isVisibleSyntaxError() {
            String error = run("sensor 1 on train enter { train  at station 1 set speed 0; };");

            assertNotNull(error, "the exact 'train at' token gap must be a visible error");
        }

        @Test
        @DisplayName("loading a savegame re-applies its valid program")
        void loadValidProgram_reapplies() {
            Station two = new Station(2);
            two.setName("B");
            model.addStation(two);
            String program = """
                    create itinerary "ok" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "ok" to train 1;
                    """;
            List<String> errors = model.setProgram(program);
            assertTrue(errors.isEmpty(), errors.toString());

            Model loaded = new GameSaveService().fromBytes(new GameSaveService().toBytes(model));

            assertNotNull(loaded);
            assertTrue(loaded.isProgramValid(), "the saved program must re-apply cleanly");
            assertEquals(program, loaded.getProgram(),
                    "the stored text must survive the round-trip");
            assertTrue(loaded.getTrainFromLocomotiveId(1).getAutopilot().itinerary().isPresent(),
                    "the saved program must run again on load");
        }

        @Test
        @DisplayName("a savegame with a broken program keeps and marks the text and warns (O3)")
        void loadBrokenProgram_warnsVisiblyAndKeepsText() {
            // What a player leaves in the editor is stored even when the engine rejects it.
            model.setProgram("create itinerary \"broken\" {");
            assertFalse(model.isProgramValid(), "the broken text must be marked as invalid");

            Model loaded = new GameSaveService().fromBytes(new GameSaveService().toBytes(model));

            assertNotNull(loaded);
            assertEquals("create itinerary \"broken\" {", loaded.getProgram(),
                    "the invalid text must survive the load so it stays editable");
            assertFalse(loaded.isProgramValid(), "the rejection must be marked after the load");

            // The load happened before any presenter existed: the notice is queued, not log-only,
            // and must reach the game console as soon as the client wires its sink.
            List<String> visible = new ArrayList<>();
            loaded.setUserMessageSink((title, text) -> visible.add(title + ": " + text));

            assertTrue(visible.stream().anyMatch(m -> m.contains("Saved program not applied")),
                    "the rejection must reach the visible channel, got: " + visible);
        }

        @Test
        @DisplayName("a savegame's valid program with a rejected order warns when the sink is wired")
        void loadValidProgramWithSemanticProblem_warnsAfterLoad() {
            // `fork 99 set n;` parses and applies, but no fork 99 exists: the engine warns. While
            // the savegame is re-applied there is no presenter, so the notice must be queued for
            // the visible channel instead of staying in the log (D1 residual).
            model.setProgram("fork 99 set n;");
            assertTrue(model.isProgramValid(), "a semantic warning does not reject the program");

            Model loaded = new GameSaveService().fromBytes(new GameSaveService().toBytes(model));

            assertNotNull(loaded);
            assertTrue(loaded.isProgramValid(), "the saved program must re-apply cleanly");

            List<String> visible = new ArrayList<>();
            loaded.setUserMessageSink((title, text) -> visible.add(title + ": " + text));

            assertTrue(visible.stream().anyMatch(m -> m.contains("Fork 99 not found")),
                    "the semantic warning must reach the visible channel, got: " + visible);
        }

        @Test
        @DisplayName("headless load (no sink): the program applies and the caller's channel works")
        void loadValidProgramWithSemanticProblem_headlessKeepsOldBehavior() {
            model.setProgram("fork 99 set n;");

            Model loaded = new GameSaveService().fromBytes(new GameSaveService().toBytes(model));

            assertNotNull(loaded);
            assertNull(loaded.getUserMessageSink(), "a headless load must not invent a channel");
            assertTrue(loaded.isProgramValid(), "the ignored order must not reject the program");
            assertNull(loaded.getFork(99), "the order must stay ignored, exactly as before");

            // A headless client owns its problem channel: its mission notifier keeps the warnings.
            List<String> messages = new ArrayList<>();
            loaded.getTrainFromLocomotiveId(1).getAutopilot().setMissionNotifier(messages::add);
            assertFalse(loaded.getTrainFromLocomotiveId(1).getAutopilot()
                    .startMission(TrainMission.stopAtSensor(99, 2)));
            assertTrue(messages.stream().anyMatch(m -> m.contains("sensor 99 not found")),
                    "the headless caller's notifier must keep working, got: " + messages);
        }
    }
}
