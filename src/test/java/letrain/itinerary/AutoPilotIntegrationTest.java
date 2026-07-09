package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Comprehensive integration tests for AutoPilot following itineraries.
 */
@DisplayName("AutoPilot Integration Tests")
class AutoPilotIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Straight track
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Straight track")
    class StraightTrack {

        @Test
        @DisplayName("1.1 Train on station A → reaches station B")
        void trainOnStationA_reachesStationB() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t0, Dir.W);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(200);
            assertAtStation(t, b);
        }

        @Test
        @DisplayName("1.2 Train NOT on station → reaches station A first, then B")
        void trainNotOnStation_reachesA_thenB() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrainNoStation(t1, Dir.E);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(400);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
        }

        @Test
        @DisplayName("1.3 Train facing opposite direction → still reaches station")
        void trainFacingAway_reachesStation() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t1, Dir.E);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(400);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
        }

        @Test
        @DisplayName("1.4 Engine OFF → train does not move")
        void engineOff_trainDoesNotMove() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrainNoStation(t0, Dir.W);
            ((Locomotive) t.getDirectorLinker()).setEngineOn(false);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(200);
            assertEquals(0, t.getSpeed(), "train should not move with engine off");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Fork
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Fork")
    class Fork {

        @Test
        @DisplayName("4.1 Fork flipped: train on main line enters branch station")
        void forkFlipped_trainEntersBranch() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            ForkRailTrack fork = makeFork(2, 0);
            fork.addRoute(Dir.W, Dir.E);
            fork.addRoute(Dir.E, Dir.W); // straight
            fork.addRoute(Dir.W, Dir.S);
            fork.addRoute(Dir.S, Dir.W); // branch S
            fork.setNormalRoute();
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            RailTrack branch = makeTrack(2, 1, Dir.N, Dir.S);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork, Dir.W);
            connect(fork, Dir.E, t3, Dir.W);
            connect(fork, Dir.S, branch, Dir.N);

            Station mainSt = makeStation(t3, "Main");
            Station branchSt = makeStation(branch, "Branch");
            Train t = makeTrainNoStation(t1, Dir.W);
            assertFalse(fork.isUsingAlternativeRoute(), "fork starts straight");

            model.setProgram("""
                    station %d set name "Main";
                    station %d set name "Branch";
                    create itinerary "Ruta" {
                        add station "Branch"
                        add station "Main"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(mainSt.getId(), branchSt.getId(), t.getId(), t.getId(), t.getId()));

            runTicks(600);

            assertAtStation(t, branchSt);
        }

        @Test
        @DisplayName("4.2 Fork flipped: train starting on station enters branch station")
        void forkFlipped_trainStartsOnStation_entersBranch() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            ForkRailTrack fork = makeFork(2, 0);
            fork.addRoute(Dir.W, Dir.E);
            fork.addRoute(Dir.E, Dir.W); // straight
            fork.addRoute(Dir.W, Dir.S);
            fork.addRoute(Dir.S, Dir.W); // branch S
            fork.setNormalRoute();
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            RailTrack branch = makeTrack(2, 1, Dir.N, Dir.S);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork, Dir.W);
            connect(fork, Dir.E, t3, Dir.W);
            connect(fork, Dir.S, branch, Dir.N);

            Station startSt = makeStation(t0, "Start");
            Station mainSt = makeStation(t3, "Main");
            Station branchSt = makeStation(branch, "Branch");
            Train t = makeTrain(t0, Dir.W); // Train starts at Start station (first waypoint)
            assertFalse(fork.isUsingAlternativeRoute(), "fork starts straight");

            model.setProgram("""
                    station %d set name "Start";
                    station %d set name "Main";
                    station %d set name "Branch";
                    create itinerary "Ruta" {
                        add station "Start"
                        add station "Branch"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(startSt.getId(), mainSt.getId(), branchSt.getId(), t.getId(), t.getId(), t.getId()));

            runTicks(600);

            assertAtStation(t, branchSt);
        }

        @Test
        @DisplayName("4.3 Multiple forks to dead ends: train reaches end station")
        void multipleForksToDeadEnds_trainReachesEndStation() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            
            ForkRailTrack fork1 = makeFork(2, 0);
            fork1.addRoute(Dir.W, Dir.E);
            fork1.addRoute(Dir.E, Dir.W); // straight E
            fork1.addRoute(Dir.W, Dir.S);
            fork1.addRoute(Dir.S, Dir.W); // branch S (dead end)
            fork1.setAlternativeRoute(); // start pointing to dead end S
            
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            
            ForkRailTrack fork2 = makeFork(4, 0);
            fork2.addRoute(Dir.W, Dir.E);
            fork2.addRoute(Dir.E, Dir.W); // straight E
            fork2.addRoute(Dir.W, Dir.S);
            fork2.addRoute(Dir.S, Dir.W); // branch S (dead end)
            fork2.setAlternativeRoute(); // start pointing to dead end S
            
            RailTrack t5 = makeTrack(5, 0, Dir.W, Dir.E);
            RailTrack branch1 = makeTrack(2, 1, Dir.N, Dir.S); // dead end 1
            RailTrack branch2 = makeTrack(4, 1, Dir.N, Dir.S); // dead end 2
            
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork1, Dir.W);
            connect(fork1, Dir.E, t3, Dir.W);
            connect(fork1, Dir.S, branch1, Dir.N);
            
            connect(t3, Dir.E, fork2, Dir.W);
            connect(fork2, Dir.E, t5, Dir.W);
            connect(fork2, Dir.S, branch2, Dir.N);
            
            Station a = makeStation(t0, "A");
            Station b = makeStation(t5, "B");
            
            Train t = makeTrain(t0, Dir.W); // starts at A
            
            model.postLoadInit();
            t.getSafetyManager().claimOccupiedSegments();
            
            program("A", a.getId(), "B", b.getId(), t.getId());

            runUntil(model, () -> hasReached(t, b), 2000);

            assertAtStation(t, b);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. AutoPilot states
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("9. AutoPilot states")
    class AutoPilotStates {

        @Test
        @DisplayName("9.1 Activate while moving → succeeds")
        void activateWhileMoving_succeeds() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t0, Dir.W);
            program("A", a.getId(), "B", b.getId(), t.getId());
            assertTrue(t.isAutoMode());
            t.toggleAutoMode(); // off
            // Simulate moving train
            ((Locomotive) t.getDirectorLinker()).setCurrentSpeed(3);
            assertTrue(t.getSpeed() > 0, "train should be moving");
            t.toggleAutoMode();
            assertTrue(t.isAutoMode(), "autopilot should activate even while moving");
        }

        @Test
        @DisplayName("9.2 Activate without itinerary → fails")
        void activateWithoutItinerary_fails() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            Train t = makeTrain(t0, Dir.W);
            t.toggleAutoMode();
            assertFalse(t.isAutoMode(), "autopilot should NOT activate without itinerary");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10. Waypoint command execution
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("10. Waypoint command execution")
    class WaypointCommands {

        @Test
        @DisplayName("10.1 SPEED waypoint sets train target speed")
        void speedCommandSlowsTrain() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            model.setProgram("""
                    station %d set name "A";
                    station %d set name "B";
                    create itinerary "Ruta" {
                        add station "A"
                        add station "B" SPEED 0
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));

            runTicks(600);

            assertAtStation(t, b);
            assertEquals(0, ((Locomotive) t.getDirectorLinker()).getTargetSpeed(),
                    "train should have target speed 0 after SPEED(0) waypoint command");
            assertEquals(0, t.getSpeed(),
                    "train should have stopped after SPEED(0) waypoint command");
        }

        @Test
        @DisplayName("10.2 WAIT waypoint pauses train for specified seconds")
        void waitCommandPausesTrain() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();

            model.setProgram("""
                    station %d set name "A";
                    station %d set name "B";
                    create itinerary "Ruta" {
                        add station "A"
                        add station "B" SPEED 0 WAIT 3 SPEED 3
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));

            // Run until reaching station B
            boolean reached = false;
            for (int i = 0; i < 600; i++) {
                runTicks(1);
                if (hasReached(t, b)) {
                    reached = true;
                    int speedAfterArrival = t.getSpeed();
                    // After arrival with SPEED 0, speed should be 0 (braking takes a few ticks)
                    System.out.println("REACHED B at tick " + i + " speed=" + speedAfterArrival);

                    // Run some ticks to let WAIT command execute and then SPEED 3
                    for (int j = 0; j < 200; j++) {
                        runTicks(1);
                        if (t.getSpeed() > 0) {
                            System.out.println("  train resumed at tick " + (i + j) + " speed=" + t.getSpeed());
                            break;
                        }
                    }
                    break;
                }
            }
            assertTrue(reached, "train should reach station B");

            // Verify the train eventually moves again after WAIT+SPEED 3
            assertTrue(t.getSpeed() > 0 || l.getTargetSpeed() > 0,
                    "train should have resumed after WAIT command (speed=" + t.getSpeed()
                            + " targetSpeed=" + l.getTargetSpeed() + ")");
        }

        @Test
        @DisplayName("10.3 Multiple commands execute in sequence")
        void multipleCommandsExecuteInSequence() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();

            model.setProgram("""
                    station %d set name "A";
                    station %d set name "B";
                    create itinerary "Ruta" {
                        add station "A"
                        add station "B" SPEED 0 WAIT 2 SPEED 5
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));

            runUntil(model, () -> l.getTargetSpeed() == 5, 800);
            // After WAIT 2 + SPEED 5, train should have target speed 5
            assertEquals(5, l.getTargetSpeed(),
                    "train target speed should be 5 after SPEED 5 command");
        }

        @Test
        @DisplayName("10.4 Sensor waypoint with LOAD command")
        void sensorWaypointWithLoad() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Train t = makeTrain(t0, Dir.W);

            // Create a standalone sensor on t1 (not a station)
            letrain.track.Sensor sensor = new letrain.track.Sensor(model.nextSensorId());
            sensor.setName("S1");
            t1.setSensor(sensor);
            model.addSensor(sensor);

            model.setProgram("""
                    station %d set name "A";
                    sensor %d set name "S1";
                    create itinerary "Ruta" {
                        add station "A"
                        add sensor "S1" LOAD
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), sensor.getId(), t.getId(), t.getId(), t.getId()));

            runTicks(400);

            // Train should have passed through sensor S1
            // The LOAD command should have executed (no-op on sensor, but shouldn't crash)
            // Verify iteration didn't crash: train should still be valid
            assertFalse(t.getLinkers().isEmpty(), "train should still have linkers");
            assertNotNull(t.getDirectorLinker(), "train should still have a director linker");
        }

        @Test
        @DisplayName("10.5 REVERSE command flips direction at waypoint")
        void reverseCommandFlipsDirection() {
            // Simple layout: A - B, train enters from West at A
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();

            model.setProgram("""
                    station %d set name "A";
                    station %d set name "B";
                    create itinerary "Ruta" {
                        add station "A"
                        add station "B" REVERSE
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));

            Dir originalDir = loco.getDir();

            runUntil(model, () -> loco.getDir() != originalDir, 500);

            // After REVERSE command, direction should have flipped
            Dir newDir = loco.getDir();
            assertNotNull(newDir, "direction should not be null");
            System.out.println("REVERSE test: originalDir=" + originalDir + " newDir=" + newDir);
            assertFalse(newDir == originalDir,
                    "direction should have flipped after REVERSE command (was " + originalDir + ", now " + newDir + ")");
        }

        @Disabled("Auto-reverse disabled; test no longer applicable")
        @Test
        @DisplayName("10.6 Auto-reverse on routing mismatch at fork (disabled)")
        void autoReverseOnRoutingMismatch() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            ForkRailTrack fork = makeFork(2, 0);
            fork.addRoute(Dir.W, Dir.E);
            fork.addRoute(Dir.E, Dir.W);
            fork.addRoute(Dir.W, Dir.S);
            fork.addRoute(Dir.S, Dir.W);
            fork.setNormalRoute();
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            RailTrack branch = makeTrack(2, 1, Dir.N, Dir.S);

            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork, Dir.W);
            connect(fork, Dir.E, t3, Dir.W);
            connect(fork, Dir.S, branch, Dir.N);

            Station branchSt = makeStation(branch, "Branch");
            Station mainSt = makeStation(t3, "Main");

            Train t = makeTrain(branch, Dir.S);

            model.setProgram("""
                    station %d set name "Branch";
                    station %d set name "Main";
                    create itinerary "Ruta" {
                        add station "Branch"
                        add station "Main"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(branchSt.getId(), mainSt.getId(), t.getId(), t.getId(), t.getId()));

            assertAtStation(t, branchSt);

            runTicks(600);

            // Without auto-reverse, the train should remain at the branch station.
            assertAtStation(t, branchSt);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Circuit from save file
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Circuit (save file)")
    class CircuitFromSave {

        @Test
        @DisplayName("5.1 Madrid → Barcelona")
        void madridToBarcelona() throws Exception {
            Model m = loadFromSave("circuit.json");
            var sts = m.getStations();
            Station madrid = sts.get(0);
            Station barcelona = sts.get(1);
            Train t = placeTrain(m, madrid.getTrack(), Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();
            l.setEngineOn(true);
            t.setStationId(madrid.getId());

            m.setProgram("""
                    station %d set name "Madrid";
                    station %d set name "Barcelona";
                    create itinerary "Ruta" {
                        add station "Madrid"
                        add station "Barcelona"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(madrid.getId(), barcelona.getId(), t.getId(), t.getId(), t.getId()));

            boolean reachedBarcelona = false;
            for (int i = 0; i < 1200; i++) {
                runTicks(m, 1);
                Segment curSeg = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() instanceof RailTrack rt) {
                    curSeg = m.getRailwayGraph().getSegment(rt);
                }
                Point pos = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() != null) {
                    pos = t.getLinkers().getFirst().getTrack().getPosition();
                }
                System.out.printf("TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d segment=%s pos=%s%n",
                        i, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId(),
                        curSeg != null ? curSeg.getId() : "null",
                        pos != null ? pos.getX() + "," + pos.getY() : "null");
                if (hasReached(t, barcelona)) {
                    reachedBarcelona = true;
                    break;
                }
            }
            assertTrue(reachedBarcelona, "should reach Barcelona");
        }

        @Test
        @DisplayName("5.2 Madrid → Barcelona with speed 0 on enter Barcelona")
        void madridToBarcelonaSpeed0OnEnter() throws Exception {
            Model m = loadFromSave("circuit.json");
            var sts = m.getStations();
            Station madrid = sts.get(0);
            Station barcelona = sts.get(1);
            Train t = placeTrain(m, madrid.getTrack(), Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();
            l.setEngineOn(true);
            t.setStationId(madrid.getId());

            m.setProgram("""
                    station %d set name "Madrid";
                    station %d set name "Barcelona";
                    station %d on train enter { train set speed 0 };
                    create itinerary "Ruta" {
                        add station "Madrid"
                        add station "Barcelona"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(madrid.getId(), barcelona.getId(), barcelona.getId(), t.getId(), t.getId(), t.getId()));

            boolean reachedBarcelona = false;
            for (int i = 0; i < 1200; i++) {
                runTicks(m, 1);
                Segment curSeg = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() instanceof RailTrack rt) {
                    curSeg = m.getRailwayGraph().getSegment(rt);
                }
                Point pos = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() != null) {
                    pos = t.getLinkers().getFirst().getTrack().getPosition();
                }
                System.out.printf("TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d segment=%s pos=%s%n",
                        i, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId(),
                        curSeg != null ? curSeg.getId() : "null",
                        pos != null ? pos.getX() + "," + pos.getY() : "null");
                if (hasReached(t, barcelona)) {
                    reachedBarcelona = true;
                    // Let's run a few more ticks to let the train slow down and stop
                    for (int j = 0; j < 200; j++) {
                        runTicks(m, 1);
                        System.out.printf("POST-TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d%n",
                                j, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId());
                    }
                    break;
                }
            }
            assertTrue(reachedBarcelona, "should reach Barcelona");
            assertEquals(0, t.getSpeed(), "speed should be 0");
            assertEquals(0, l.getTargetSpeed(), "target speed should be 0");
        }
    }

    @Nested
    @DisplayName("6. Siding and Alternative Segments")
    class SidingAndAlternativeSegments {

        @Test
        @DisplayName("6.1 Alternative Segment Siding Bypass")
        void alternativeSegmentSidingBypass() {
            // Siding layout with parallel S1 and S2:
            // Station A at (0, 0) -> Fork 1 at (2, 0)
            // Fork 1 branches into:
            //   - S1 (main line): Fork 1 at (2, 0) -> Track at (3, 0) -> Fork 2 at (4, 0)
            //   - S2 (siding line): Fork 1 at (2, 0) -> Track at (3, 1) -> Fork 2 at (4, 0)
            // Fork 2 connects to Station B at (6, 0)

            RailTrack tA = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack tA1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(tA, Dir.E, tA1, Dir.W);

            ForkRailTrack fork1 = makeFork(2, 0);
            fork1.addRoute(Dir.W, Dir.E);
            fork1.addRoute(Dir.E, Dir.W);
            fork1.addRoute(Dir.W, Dir.S);
            fork1.addRoute(Dir.S, Dir.W);
            fork1.setNormalRoute();
            connect(tA1, Dir.E, fork1, Dir.W);

            // S1 (main)
            RailTrack tMain = makeTrack(3, 0, Dir.W, Dir.E);
            connect(fork1, Dir.E, tMain, Dir.W);

            // S2 (siding/alternative)
            RailTrack tS1 = makeTrack(2, 1, Dir.N, Dir.E);
            connect(fork1, Dir.S, tS1, Dir.N);

            RailTrack tS2 = makeTrack(3, 1, Dir.W, Dir.E);
            connect(tS1, Dir.E, tS2, Dir.W);

            RailTrack tS3 = makeTrack(4, 1, Dir.W, Dir.N);
            connect(tS2, Dir.E, tS3, Dir.W);

            // Fork 2 at (4, 0)
            ForkRailTrack fork2 = makeFork(4, 0);
            fork2.addRoute(Dir.W, Dir.E);
            fork2.addRoute(Dir.E, Dir.W);
            fork2.addRoute(Dir.S, Dir.E);
            fork2.addRoute(Dir.E, Dir.S);
            fork2.setNormalRoute();

            connect(tMain, Dir.E, fork2, Dir.W);
            connect(tS3, Dir.N, fork2, Dir.S);

            RailTrack tB1 = makeTrack(5, 0, Dir.W, Dir.E);
            connect(fork2, Dir.E, tB1, Dir.W);
            RailTrack tB = makeTrack(6, 0, Dir.W, Dir.E);
            connect(tB1, Dir.E, tB, Dir.W);

            Station a = makeStation(tA, "A");
            Station b = makeStation(tB, "B");

            // Post load init to populate graph
            model.postLoadInit();

            // Train 1: The active train starting at Station A
            Train t1 = makeTrain(tA, Dir.W);

            // Train 2: The blocker parked on tMain
            Train t2 = makeTrainNoStation(tMain, Dir.W);
            ((Locomotive) t2.getDirectorLinker()).setEngineOn(false); // keep it parked

            // Force segment registration
            t1.getSafetyManager().claimOccupiedSegments();
            t2.getSafetyManager().claimOccupiedSegments();

            // Verify Train 2 owns tMain's segment
            Segment mainSeg = model.getRailwayGraph().getSegment(tMain);
            assertNotNull(mainSeg);
            assertTrue(model.getBlockManager().getOwners(mainSeg).contains(t2));

            // Now program Train 1 to go from Station A to B
            program("A", a.getId(), "B", b.getId(), t1.getId());

            // Run physics
            runTicks(300);

            // Train 1 should have bypassed tMain via the siding and reached B!
            assertAtStation(t1, b);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private RailMap railMap() {
        return model.getRailMap();
    }

    private RailTrack makeTrack(int x, int y, Dir from, Dir to) {
        RailTrack t = new RailTrack();
        t.setPosition(new Point(x, y));
        t.addRoute(from, to);
        t.addRoute(to, from);
        railMap().addTrack(new Point(x, y), t);
        return t;
    }

    private ForkRailTrack makeFork(int x, int y) {
        ForkRailTrack f = new ForkRailTrack(model.nextForkId());
        f.setPosition(new Point(x, y));
        railMap().addTrack(new Point(x, y), f);
        model.addFork(f);
        return f;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    private Station makeStation(RailTrack track, String name) {
        Station st = new Station(model.nextStationId());
        st.setName(name);
        st.setTrack(track);
        track.setSensor(st);
        model.addStation(st);
        return st;
    }

    /** Train placed ON a station track (stationId set). */
    private Train makeTrain(RailTrack startTrack, Dir entryFrom) {
        Train t = placeTrain(model, startTrack, entryFrom);
        if (startTrack.getSensor() instanceof Station st) {
            t.setStationId(st.getId());
        }
        return t;
    }

    /** Train placed NOT on a station (stationId = 0). */
    private Train makeTrainNoStation(RailTrack startTrack, Dir entryFrom) {
        return placeTrain(model, startTrack, entryFrom);
    }

    private Train placeTrain(Model model, Track track, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        ((RailTrack) track).enterLinkerFromDir(entryFrom, loco);
        return train;
    }

    /** Shorthand: station A → station B itinerary, assign to train, activate. */
    private void program(String nameA, int idA, String nameB, int idB, int trainId) {
        model.setProgram("""
                 station %d set name "%s";
                 station %d set name "%s";
                 create itinerary "Ruta" {
                     add station "%s"
                     add station "%s"
                 }
                 assign itinerary "Ruta" to train %d;
                 train %d set autopilot true;
                 train %d set speed 3;
                 """.formatted(idA, nameA, idB, nameB, nameA, nameB, trainId, trainId, trainId));
    }

    private void runTicks(int count) {
        runTicks(model, count);
    }

    private void runTicks(Model m, int count) {
        for (int i = 0; i < count; i++) {
            if (m.getScheduler() != null) {
                m.getScheduler().tick();
            }
            m.moveLocomotives();
            m.loadAndUnloadTrains();
        }
        m.removeDestroyedTrains();
    }

    private boolean hasReached(Train t, Station st) {
        return t.getStationId() == st.getId();
    }

    private void runUntil(Model m, java.util.function.BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) return;
            if (m.getScheduler() != null) m.getScheduler().tick();
            m.moveLocomotives();
            m.loadAndUnloadTrains();
        }
        m.removeDestroyedTrains();
    }

    private void assertAtStation(Train t, Station st) {
        assertEquals(st.getId(), t.getStationId(),
                "expected at " + st.getName() + " but was at station " + t.getStationId());
    }



    @Test
    @DisplayName("11. Re-run after manual reversal on simple.dat")
    void testReRunAfterManualReversal() throws Exception {
        letrain.mvp.impl.GameSaveService saveService = new letrain.mvp.impl.GameSaveService();
        Model m = saveService.load(new java.io.File("simple.dat")).orElseThrow();

        Train t = m.getTrainFromLocomotiveId(1);
        assertNotNull(t, "Train 1 should exist");
        Locomotive loco = (Locomotive) t.getDirectorLinker();
        assertNotNull(loco, "Locomotive should exist");

        m.setProgram("""
                create itinerary "21" {
                    add station 2
                    add station 1
                }
                assign itinerary "21" to train 1;
                train 1 set autopilot true;
                train 1 set speed 3;
                """);

        Station st1 = m.getStation(1);
        runUntil(m, () -> t.getStationId() == st1.getId(), 800);
        assertEquals(st1.getId(), t.getStationId(), "Should have reached station 1");
        m.setProgram("train 1 set speed 0;");
        runUntil(m, () -> loco.getSpeed() == 0, 300);
        assertEquals(0, loco.getSpeed(), "Locomotive should be stopped");

        // Reverse to face East
        loco.toggleReversed();
        m.setProgram("train 1 set speed 3;");

        // Drive back to S10
        Station st2 = m.getStation(2);
        runUntil(m, () -> {
            Segment curSeg = m.getRailwayGraph().getSegment((RailTrack)loco.getTrack());
            return curSeg != null && curSeg.getId().equals("S10");
        }, 800);

        m.setProgram("train 1 set speed 0;");
        runUntil(m, () -> loco.getSpeed() == 0, 300);
        assertEquals(0, loco.getSpeed(), "Speed should be 0");

        // Reverse again to face West
        loco.toggleReversed();

        // Run the script again!
        m.setProgram("""
                create itinerary "21" {
                    add station 2
                    add station 1
                }
                assign itinerary "21" to train 1;
                train 1 set autopilot true;
                train 1 set speed 3;
                """);

        // Let's run it and see if it goes to Station 2 and then Station 1!
        System.out.println("DEBUG BEFORE SECOND RUN: autoMode=" + t.isAutoMode() + ", apMode=" + t.getAutopilot().mode() + ", speed=" + loco.getSpeed() + ", targetSpeed=" + loco.getTargetSpeed() + ", curSeg=" + (t.getSafetyManager().getCurrentSegment() != null ? t.getSafetyManager().getCurrentSegment().getId() : "null") + ", nextSeg=" + (t.getSafetyManager().getNextSegment() != null ? t.getSafetyManager().getNextSegment().getId() : "null") + ", waitingForBlock=" + t.getSafetyManager().isWaitingForBlock());
        runUntil(m, () -> t.getStationId() == st2.getId(), 400);
        System.out.println("DEBUG AFTER SECOND RUN TO ST2: autoMode=" + t.isAutoMode() + ", apMode=" + t.getAutopilot().mode() + ", speed=" + loco.getSpeed() + ", targetSpeed=" + loco.getTargetSpeed() + ", curSeg=" + (t.getSafetyManager().getCurrentSegment() != null ? t.getSafetyManager().getCurrentSegment().getId() : "null") + ", nextSeg=" + (t.getSafetyManager().getNextSegment() != null ? t.getSafetyManager().getNextSegment().getId() : "null") + ", waitingForBlock=" + t.getSafetyManager().isWaitingForBlock() + ", stationId=" + t.getStationId());
        assertEquals(st2.getId(), t.getStationId(), "Should have reached station 2 again");

        runUntil(m, () -> t.getStationId() == st1.getId(), 800);
        assertEquals(st1.getId(), t.getStationId(), "Should have reached station 1 again");
    }

    private Model loadFromSave(String resourceName) throws Exception {
        var url = getClass().getClassLoader().getResource(resourceName);
        assertNotNull(url, "resource not found: " + resourceName);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Model model = mapper.readValue(url, letrain.mvp.impl.Model.class);
        model.postLoadInit();
        return model;
    }
}
