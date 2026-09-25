package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.itinerary.Itinerary;
import letrain.itinerary.TrainMission;
import letrain.itinerary.WaypointCommand;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #626 / ADR-022 phase 2f: the waypoint plan accepts the train orders of the scripts
 * ({@code uncouple}/{@code couple}, {@code stop at …}, {@code stop when blocked …}, fork actions)
 * with the strict 2a grammar: commas mandatory, {@code arrival} first, actions in order,
 * {@code departure} last.
 */
@DisplayName("Issue #626: waypoint maneuver grammar and model")
class WaypointManeuverGrammarTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station a = new Station(1);
        a.setName("a");
        model.addStation(a);
        Station b = new Station(2);
        b.setName("b");
        model.addStation(b);
        Sensor s5 = new Sensor(5);
        s5.setName("s5");
        model.addSensor(s5);
        Sensor s6 = new Sensor(6);
        s6.setName("s6");
        model.addSensor(s6);
        ForkRailTrack fork = new ForkRailTrack(3);
        fork.setPosition(new letrain.map.Point(0, 1));
        model.addFork(fork);
        Locomotive loco = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(loco);
        model.addLocomotive(loco);
    }

    private Itinerary run(String program) {
        List<String> errors = model.setProgram(program);
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
        return model.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();
    }

    private List<String> errors(String program) {
        return model.setProgram(program);
    }

    @Test
    @DisplayName("the run-around plan parses into the ordered commands")
    void runAroundPlan_isParsedInOrder() {
        Itinerary itinerary = run("""
                create itinerary "runaround" {
                    add station "b" arrival 06:27,
                                   uncouple forward 1,
                                   stop at sensor 5 speed 2,
                                   reverse,
                                   fork 3 set curved,
                                   stop at sensor 6 speed 2,
                                   couple forward 1,
                                   reverse,
                                   departure 06:45
                    add station "a"
                }
                assign itinerary "runaround" to train 1;
                """);

        List<WaypointCommand> commands = itinerary.waypoints().get(0).commands();
        assertEquals(List.of(WaypointCommand.uncouple(true, 1),
                WaypointCommand.mission(TrainMission.Kind.SENSOR, 5, 2), WaypointCommand.REVERSE,
                WaypointCommand.forkSetDirection(3, "curved"),
                WaypointCommand.mission(TrainMission.Kind.SENSOR, 6, 2),
                WaypointCommand.couple(true, 1), WaypointCommand.REVERSE), commands);
    }

    @Test
    @DisplayName("stop at station/end, stop when blocked, stop on contact and fork flip are accepted")
    void otherTrainOrders_areAccepted() {
        Itinerary itinerary = run("""
                create itinerary "orders" {
                    add station "a"
                        stop at station "b" speed 3,
                        stop at end,
                        stop when blocked speed 2,
                        stop on contact speed 2,
                        fork 3 flip
                    add station "b"
                }
                assign itinerary "orders" to train 1;
                """);

        List<WaypointCommand> commands = itinerary.waypoints().get(0).commands();
        assertEquals(List.of(WaypointCommand.mission(TrainMission.Kind.STATION, 2, 3),
                WaypointCommand.mission(TrainMission.Kind.END_OF_TRACK, -1, 0),
                WaypointCommand.mission(TrainMission.Kind.WHEN_BLOCKED, -1, 2),
                WaypointCommand.mission(TrainMission.Kind.ON_CONTACT, -1, 2),
                WaypointCommand.forkFlip(3)), commands);
    }

    @Test
    @DisplayName("a 'stop on contact' waypoint action survives a savegame round-trip (issue #645)")
    void stopOnContact_survivesGameSaveRoundTrip() {
        Itinerary itinerary = run("""
                create itinerary "coupling" {
                    add station "a"
                        stop on contact speed 2,
                        couple forward all
                    add station "b"
                }
                assign itinerary "coupling" to train 1;
                """);

        List<WaypointCommand> commands = itinerary.waypoints().get(0).commands();
        assertEquals(List.of(WaypointCommand.mission(TrainMission.Kind.ON_CONTACT, -1, 2),
                WaypointCommand.couple(true, letrain.vehicle.rail.TrainCouplingManager.ALL)),
                commands);

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));
        Itinerary restoredItinerary =
                restored.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();

        assertEquals(commands, restoredItinerary.waypoints().get(0).commands());
    }

    @Test
    @DisplayName("the 2a strictness is kept: commas and order arrival → actions → departure")
    void strictness_isKept() {
        assertFalse(errors("""
                create itinerary "bad" {
                    add station "a" reverse uncouple forward 1
                    add station "b"
                }
                """).isEmpty(), "missing comma must be rejected");
        assertFalse(errors("""
                create itinerary "bad" {
                    add station "a" departure 09:00, reverse
                    add station "b"
                }
                """).isEmpty(), "action after departure must be rejected");
        assertFalse(errors("""
                create itinerary "bad" {
                    add station "a" reverse, arrival 09:00
                    add station "b"
                }
                """).isEmpty(), "action before arrival must be rejected");
        assertFalse(errors("""
                create itinerary "bad" {
                    add station "a" stop at
                    add station "b"
                }
                """).isEmpty(), "stop at without destination must be rejected");
        assertFalse(errors("""
                create itinerary "bad" {
                    add station "a" uncouple
                    add station "b"
                }
                """).isEmpty(), "uncouple without direction must be rejected");
    }

    @Test
    @DisplayName("couple/uncouple accept 'all' as the count")
    void couplingAll_isParsed() {
        Itinerary itinerary = run("""
                create itinerary "all" {
                    add station "a"
                        uncouple backward all,
                        couple forward all
                    add station "b"
                }
                assign itinerary "all" to train 1;
                """);

        List<WaypointCommand> commands = itinerary.waypoints().get(0).commands();
        assertEquals(List.of(
                WaypointCommand.uncouple(false, letrain.vehicle.rail.TrainCouplingManager.ALL),
                WaypointCommand.couple(true, letrain.vehicle.rail.TrainCouplingManager.ALL)),
                commands);
    }

    @Test
    @DisplayName("an 'all' command survives a savegame round-trip")
    void couplingAll_survivesGameSaveRoundTrip() {
        run("""
                create itinerary "all" {
                    add station "a" uncouple backward all
                    add station "b"
                }
                assign itinerary "all" to train 1;
                """);

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));
        Itinerary itinerary =
                restored.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();

        assertEquals(
                List.of(WaypointCommand.uncouple(false,
                        letrain.vehicle.rail.TrainCouplingManager.ALL)),
                itinerary.waypoints().get(0).commands());
    }

    @Test
    @DisplayName("the new waypoint commands survive a savegame round-trip")
    void commands_surviveGameSaveRoundTrip() {
        run("""
                create itinerary "runaround" {
                    add station "b" arrival 06:27,
                                   uncouple forward 1,
                                   stop at sensor 5 speed 2,
                                   reverse,
                                   fork 3 set curved,
                                   stop at sensor 6 speed 2,
                                   couple forward 1,
                                   reverse,
                                   departure 06:45
                    add station "a"
                }
                assign itinerary "runaround" to train 1;
                """);

        GameSaveService saves = new GameSaveService();
        Model restored = saves.fromBytes(saves.toBytes(model));
        Itinerary itinerary =
                restored.getTrainFromLocomotiveId(1).getAutopilot().itinerary().orElseThrow();

        assertEquals(
                List.of(WaypointCommand.uncouple(true, 1),
                        WaypointCommand.mission(TrainMission.Kind.SENSOR, 5, 2),
                        WaypointCommand.REVERSE, WaypointCommand.forkSetDirection(3, "curved"),
                        WaypointCommand.mission(TrainMission.Kind.SENSOR, 6, 2),
                        WaypointCommand.couple(true, 1), WaypointCommand.REVERSE),
                itinerary.waypoints().get(0).commands());
    }
}
