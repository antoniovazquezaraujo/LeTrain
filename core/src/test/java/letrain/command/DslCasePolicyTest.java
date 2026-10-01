package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * D2 strict case policy: the same text behaves the same in the console and in a program. Keywords
 * are strictly lowercase in every entry point (a program runs verbatim, no lowercasing), strings
 * and names keep the case the author wrote, and names are matched exactly: a wrong-case reference
 * warns (D1) and does nothing.
 *
 * <p>
 * U4: references that were numeric-only accept exact quoted names wherever the entity has one
 * (trigger sensor/station selectors and {@code train at station|sensor}); forks, semaphores and
 * signals have no name and stay numeric. The trigger selector is resolved when the trigger is
 * registered; {@code train at} when the order runs.
 */
@DisplayName("D2/U4: strict case policy and name references")
class DslCasePolicyTest {

    private Model model;
    private Train trainAtStation;
    private Train trainAtSensor;
    private Station central;
    private Sensor norte;
    private final List<String> panelMessages = new ArrayList<>();
    private final List<String> consoleNotices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();

        RailTrack stationTrack = addTrack(0, 0);
        central = new Station(1);
        central.setName("Central");
        central.setTrack(stationTrack);
        stationTrack.setComponent(central);
        model.addStation(central);

        RailTrack sensorTrack = addTrack(5, 0);
        norte = new Sensor(1);
        norte.setName("Norte");
        norte.setTrack(sensorTrack);
        sensorTrack.setComponent(norte);
        model.addSensor(norte);

        trainAtStation = placeTrain(stationTrack, 1);
        trainAtSensor = placeTrain(sensorTrack, 2);

        // The panel channel: program and deferred trigger notices report through it (D1).
        model.setUserMessageSink((title, text) -> panelMessages.add(title + ": " + text));
    }

    private RailTrack addTrack(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.W, Dir.E);
        track.addRoute(Dir.E, Dir.W);
        model.getRailMap().addTrack(new Point(x, y), track);
        return track;
    }

    private Train placeTrain(RailTrack track, int locoId) {
        Locomotive loco = new Locomotive(locoId, "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(locoId);
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        track.enterLinkerFromDir(Dir.W, loco);
        return train;
    }

    /** Runs a console line exactly as the UI funnels do (the executor may append ';'). */
    private String run(String line) {
        String error = PlayerCommandExecutor.execute(line, model, null, null, null,
                (title, text) -> panelMessages.add(title + ": " + text), null, null, null, null,
                null, false);
        String notice = model.getCommandNotice();
        if (notice != null && !notice.isEmpty()) {
            consoleNotices.add(notice);
        }
        return error;
    }

    private int speedOf(Train train) {
        return ((Locomotive) train.getDirectorLinker()).getTargetSpeed();
    }

    private boolean consoleWarned(String fragment) {
        return consoleNotices.stream().anyMatch(m -> m.contains(fragment));
    }

    private boolean panelWarned(String fragment) {
        return panelMessages.stream().anyMatch(m -> m.contains(fragment));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Strict keywords: lowercase everywhere, program verbatim
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strict keywords")
    class Keywords {

        @Test
        @DisplayName("a program with an uppercased keyword fails with line/column and runs nothing")
        void programUppercaseKeyword_failsAndExecutesNothing() {
            List<String> errors = model.setProgram("train 1 set speed 4;\nTRAIN 1 set speed 5;");

            assertFalse(errors.isEmpty(), "an uppercased keyword must be rejected");
            assertTrue(errors.get(0).contains("line 2"), errors.toString());
            assertEquals(0, speedOf(trainAtStation),
                    "nothing may execute when the program does not parse (not even line 1)");
        }

        @Test
        @DisplayName("the console rejects an uppercased keyword the same way")
        void consoleUppercaseKeyword_isRejected() {
            String error = run("TRAIN 1 set speed 3;");

            assertNotNull(error, "the console must reject the uppercased keyword");
            assertEquals(0, speedOf(trainAtStation), "the order must not run");
        }

        @Test
        @DisplayName("the program keeps string case (no lowercasing of names)")
        void programSetName_keepsTheCase() {
            List<String> errors = model.setProgram("station 1 set name \"Central Station\";");

            assertTrue(errors.isEmpty(), errors.toString());
            assertEquals("Central Station", model.getStation(1).getName(),
                    "the stored name must keep the case the author wrote");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Exact names and case typos
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exact names")
    class Names {

        @Test
        @DisplayName("a program finds an exact name set beforehand (case preserved, exact match)")
        void programNameLookup_isExact() {
            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station "Central"
                        add station 1
                    }
                    assign itinerary "Ruta" to train 1;
                    """);

            assertTrue(errors.isEmpty(), errors.toString());
            assertTrue(trainAtStation.getAutopilot().itinerary().isPresent(),
                    "the exact name must resolve: " + panelMessages);
        }

        @Test
        @DisplayName("a wrong-case name is not found: the itinerary is rejected with a visible warning")
        void wrongCaseName_warns() {
            List<String> errors = model.setProgram("""
                    create itinerary "Typo" {
                        add station "central"
                        add station 1
                    }
                    """);

            assertTrue(errors.isEmpty(), "a wrong-case name is a semantic rejection: " + errors);
            assertTrue(panelWarned("station \"central\" not found"),
                    "the case typo must be visible on the panel: " + panelMessages);
            assertTrue(trainAtStation.getAutopilot().itinerary().isEmpty(),
                    "nothing may be assigned from the rejected plan");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // U4: names in trigger selectors
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Trigger selectors by name (U4)")
    class TriggerNames {

        @Test
        @DisplayName("sensor \"Norte\" on train enter registers and fires")
        void sensorTriggerByName_fires() {
            String error = run("sensor \"Norte\" on train enter { train 1 set speed 3; };");

            assertNull(error, error);
            assertTrue(consoleNotices.isEmpty(),
                    "the registration must be silent: " + consoleNotices);

            norte.onEnterTrain(trainAtStation);

            assertEquals(3, speedOf(trainAtStation), "the named trigger must fire");
        }

        @Test
        @DisplayName("a wrong-case sensor name warns and installs nothing")
        void sensorTriggerWrongCase_warns() {
            String error = run("sensor \"norte\" on train enter { train 1 set speed 5; };");

            assertNull(error, error);
            assertTrue(consoleWarned("Sensor \"norte\" not found; trigger ignored"),
                    consoleNotices.toString());

            norte.onEnterTrain(trainAtStation);

            assertEquals(0, speedOf(trainAtStation), "the typo trigger must not fire");
        }

        @Test
        @DisplayName("station \"Central\" on train enter registers and fires")
        void stationTriggerByName_fires() {
            String error = run("station \"Central\" on train enter { train 1 set speed 2; };");

            assertNull(error, error);
            assertTrue(consoleNotices.isEmpty(),
                    "the registration must be silent: " + consoleNotices);

            central.onSensorEnter(trainAtStation, true);

            assertEquals(2, speedOf(trainAtStation), "the named trigger must fire");
        }

        @Test
        @DisplayName("a wrong-case station name warns and installs nothing")
        void stationTriggerWrongCase_warns() {
            String error = run("station \"central\" on train enter { train 1 set speed 5; };");

            assertNull(error, error);
            assertTrue(consoleWarned("Station \"central\" not found; trigger ignored"),
                    consoleNotices.toString());

            central.onSensorEnter(trainAtStation, true);

            assertEquals(0, speedOf(trainAtStation), "the typo trigger must not fire");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // U4: names in `train at`
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("train at by name (U4)")
    class TrainAtNames {

        @Test
        @DisplayName("train at station \"Central\" targets the train resting there")
        void trainAtStationByName_targetsTheTrain() {
            String error = run(
                    "sensor \"Norte\" on train enter { train at station \"Central\" set speed 3; };");

            assertNull(error, error);
            norte.onEnterTrain(trainAtSensor);

            assertEquals(3, speedOf(trainAtStation),
                    "the train at the named station must be targeted");
            assertEquals(0, speedOf(trainAtSensor), "the other train must stay untouched");
        }

        @Test
        @DisplayName("train at sensor \"Norte\" targets the train resting there")
        void trainAtSensorByName_targetsTheTrain() {
            String error = run(
                    "sensor \"Norte\" on train enter { train at sensor \"Norte\" set speed 4; };");

            assertNull(error, error);
            norte.onEnterTrain(trainAtStation);

            assertEquals(4, speedOf(trainAtSensor),
                    "the train at the named sensor must be targeted");
            assertEquals(0, speedOf(trainAtStation), "the other train must stay untouched");
        }

        @Test
        @DisplayName("a wrong-case name warns 'not found' and runs no action")
        void trainAtWrongCase_warns() {
            String error = run(
                    "sensor \"Norte\" on train enter { train at station \"central\" set speed 5; };");

            assertNull(error, error);
            norte.onEnterTrain(trainAtStation);

            assertTrue(panelWarned("Station \"central\" not found; action ignored"),
                    panelMessages.toString());
            assertEquals(0, speedOf(trainAtStation), "no action may run");
        }

        @Test
        @DisplayName("a wrong-case sensor name warns 'not found' and runs no action")
        void trainAtWrongCaseSensor_warns() {
            String error = run(
                    "sensor \"Norte\" on train enter { train at sensor \"norte\" set speed 5; };");

            assertNull(error, error);
            norte.onEnterTrain(trainAtSensor);

            assertTrue(panelWarned("Sensor \"norte\" not found; action ignored"),
                    panelMessages.toString());
            assertEquals(0, speedOf(trainAtSensor), "no action may run");
        }
    }
}
