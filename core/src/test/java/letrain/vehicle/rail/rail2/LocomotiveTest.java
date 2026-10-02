package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Locomotive Engine and Speed Logic Tests")
class LocomotiveTest {

    @Test
    @DisplayName("should turn on engine when target speed is set to positive value")
    void shouldTurnOnEngineWhenTargetSpeedIsPositive() {
        Locomotive locomotive = new Locomotive(1, "A");
        assertFalse(locomotive.isEngineOn(), "engine should be off initially");

        locomotive.setTargetSpeed(5);
        assertTrue(locomotive.isEngineOn(),
                "engine should turn on automatically when setting speed > 0");
        assertEquals(5, locomotive.getTargetSpeed(), "target speed should be updated correctly");
    }

    @Test
    @DisplayName("should not turn on engine when target speed is set to zero or negative value")
    void shouldNotTurnOnEngineWhenTargetSpeedIsZeroOrNegative() {
        Locomotive locomotive = new Locomotive(2, "B");
        assertFalse(locomotive.isEngineOn(), "engine should be off initially");

        locomotive.setTargetSpeed(0);
        assertFalse(locomotive.isEngineOn(), "engine should remain off when speed is 0");

        locomotive.setTargetSpeed(-1);
        assertFalse(locomotive.isEngineOn(), "engine should remain off when speed is negative");
    }

    @Test
    @DisplayName("should assign non-empty color upon creation")
    void shouldAssignColorOnCreation() {
        Locomotive locomotive = new Locomotive(3, "C");
        assertNotNull(locomotive.getColor());
        assertFalse(locomotive.getColor().isBlank());

        Locomotive withSpecificColor = new Locomotive(4, "D", "CYAN_BRIGHT");
        assertEquals("CYAN_BRIGHT", withSpecificColor.getColor());
    }

    @ParameterizedTest(name = "speed {0} needs {1} rails to stop")
    @CsvSource({"0, 0", "1, 1", "2, 3", "3, 6", "4, 10", "10, 55"})
    @DisplayName("brakingRails follows S(S+1)/2 (issue #633)")
    void brakingRails_knownValues(int speed, int expected) {
        assertEquals(expected, Locomotive.brakingRails(speed));
    }

    @Test
    @DisplayName("brakingRails matches the real deceleration driven by Locomotive.update()")
    void brakingRails_matchesRealDeceleration() {
        for (int speed = 0; speed <= Locomotive.MAX_SPEED; speed++) {
            assertEquals(Locomotive.brakingRails(speed), simulateStopRails(speed),
                    "real deceleration from speed " + speed);
        }
    }

    @ParameterizedTest(name = "{0} rails fit at most speed {1}")
    @CsvSource({"0, 0", "1, 1", "2, 1", "3, 2", "4, 2", "5, 2", "6, 3", "10, 4", "55, 10",
            "100, 10"})
    @DisplayName("maxSpeedForRails is the braking-curve ceiling (issue #633)")
    void maxSpeedForRails_knownValues(int rails, int expected) {
        assertEquals(expected, Locomotive.maxSpeedForRails(rails));
    }

    @Test
    @DisplayName("brakingRailsFromCurrentState matches a real deceleration from a partway counter")
    void brakingRailsFromCurrentState_matchesRealDeceleration() {
        for (int speed = 1; speed <= Locomotive.MAX_SPEED; speed++) {
            for (int preRails = 0; preRails < speed * 2; preRails++) {
                Simulation simulation = new Simulation(speed);
                simulation.developCounter(preRails);
                int predicted = simulation.loco.brakingRailsFromCurrentState();
                int real = simulation.brakeAndCountRails();
                assertEquals(predicted, real, "speed " + speed + " after " + preRails
                        + " accel rails: brakingRailsFromCurrentState must match the real stop");
            }
        }
    }

    @Test
    @DisplayName("setTargetSpeedDirect bypasses the wait gate; setTargetSpeed still intercepts")
    void setTargetSpeedDirect_bypassesWaitGate() {
        Simulation simulation = new Simulation(2);
        simulation.loco.getTrain().getSafetyManager().onEmergencyStop(); // isWaitingForBlock = true

        simulation.loco.setTargetSpeed(5);
        assertEquals(0, simulation.loco.getTargetSpeed(),
                "the wait gate must keep intercepting normal speed orders");

        simulation.loco.setTargetSpeedDirect(2);
        assertEquals(2, simulation.loco.getTargetSpeed(),
                "the direct setter must apply the target while waiting");
    }

    @Test
    @DisplayName("from standstill the 0→1 step lands exactly on START_STEP_TICKS (100 ticks)")
    void startFromStandstill_takesStartStepTicks() {
        Simulation simulation = new Simulation(0);
        simulation.loco.setTargetSpeed(1);

        simulation.runTicks(Locomotive.START_STEP_TICKS - 1);
        assertEquals(0, simulation.loco.getSpeed(),
                "a heavy train must not jump to notch 1 while starting");

        simulation.runTicks(1);
        assertEquals(1, simulation.loco.getSpeed(),
                "the 0→1 step must complete exactly on tick " + Locomotive.START_STEP_TICKS);
    }

    @Test
    @DisplayName("a blocked start does not accumulate: the counter stays at 0 while stopped")
    void blockedStart_doesNotAccumulate() {
        Simulation simulation = new Simulation(0);
        simulation.blockNextRail();
        simulation.loco.setTargetSpeed(1);

        simulation.runTicks(Locomotive.START_STEP_TICKS * 3);

        assertEquals(0, simulation.loco.getSpeed(), "the blocked train must stay stopped");
        assertEquals(0, simulation.loco.getRailsSinceLastSpeedChange(),
                "the start counter must not bank stopped ticks");

        simulation.unblock();
        simulation.loco.setTargetSpeed(1); // the block gate zeroed the target

        simulation.runTicks(Locomotive.START_STEP_TICKS - 1);
        assertEquals(0, simulation.loco.getSpeed(),
                "after the block clears the start must take the full START_STEP_TICKS");
        simulation.runTicks(1);
        assertEquals(1, simulation.loco.getSpeed(), "the train must start once the block is gone");
    }

    @Test
    @DisplayName("a standing train with target speed 0 never starts nor accumulates")
    void standingWithZeroTarget_neverStarts() {
        Simulation simulation = new Simulation(0);

        simulation.runTicks(Locomotive.START_STEP_TICKS * 5);

        assertEquals(0, simulation.loco.getSpeed());
        assertEquals(0, simulation.loco.getRailsSinceLastSpeedChange());
    }

    @Test
    @DisplayName("0→1→2 keeps one START_STEP_TICKS (100 ticks) per notch")
    void twoNotchesFromStandstill_keepStartStepTicksPerStep() {
        Simulation simulation = new Simulation(0);
        simulation.loco.setTargetSpeed(2);

        simulation.runTicks(Locomotive.START_STEP_TICKS);
        assertEquals(1, simulation.loco.getSpeed(), "notch 1 must land on tick 100");

        // One move at speed 1 (50 ticks) plus the second rail (another 50) complete the 1→2 step.
        simulation.runTicks(Locomotive.START_STEP_TICKS - 1);
        assertEquals(1, simulation.loco.getSpeed(), "the 1→2 step must also take 100 ticks");

        simulation.runTicks(1);
        assertEquals(2, simulation.loco.getSpeed(), "notch 2 must land on tick 200");
    }

    /**
     * Drives a real deceleration on a long straight line from a steady cruise (counter 0); counts
     * the successful {@code update()} moves until the loco is fully stopped.
     */
    private static int simulateStopRails(int speed) {
        return new Simulation(speed).brakeAndCountRails();
    }

    /** Test harness: one locomotive on a long straight line with controllable inertia state. */
    private static final class Simulation {
        private final Locomotive loco;
        private final List<RailTrack> line;

        private Simulation(int speed) {
            line = straightLine(120);
            loco = new Locomotive(1, "L", "RED");
            Train train = new Train(1);
            train.pushBack(loco);
            train.setDirectorLinker(loco);
            line.get(0).enterLinkerFromDir(Dir.W, loco);

            loco.setEngineOn(true);
            loco.setCurrentSpeed(speed);
            loco.setTargetSpeed(speed); // steady cruise: the rail counter starts at 0
        }

        /** Places a foreign locomotive on the next rail so the start branch detects contact. */
        private void blockNextRail() {
            Locomotive blocker = new Locomotive(2, "B", "RED");
            Train blockerTrain = new Train(2);
            blockerTrain.pushBack(blocker);
            blockerTrain.setDirectorLinker(blocker);
            line.get(1).enterLinkerFromDir(Dir.W, blocker);
        }

        private void unblock() {
            line.get(1).removeLinker();
        }

        /** Drives {@code count} real engine ticks through {@link Locomotive#update()}. */
        private void runTicks(int count) {
            for (int i = 0; i < count; i++) {
                loco.update();
            }
        }

        /**
         * Runs {@code preRails} real moves while the target is above the speed, so the inertia
         * counter is left partway (without notching up yet: preRails < 2 * speed).
         */
        private void developCounter(int preRails) {
            loco.setTargetSpeed(loco.getSpeed() + 2);
            int moves = 0;
            int guard = 10000;
            while (moves < preRails && guard-- > 0) {
                if (loco.update()) {
                    moves++;
                }
            }
            assertEquals(preRails, moves, "the harness must develop the counter");
        }

        private int brakeAndCountRails() {
            loco.setTargetSpeed(0);
            int rails = 0;
            int guard = 10000;
            while (loco.getSpeed() > 0 && guard-- > 0) {
                if (loco.update()) {
                    rails++;
                }
            }
            assertEquals(0, loco.getSpeed(), "the simulated train must end fully stopped");
            return rails;
        }
    }

    /** Horizontal line at y=0, connected west-east, long enough for a full 55-rail brake. */
    private static List<RailTrack> straightLine(int length) {
        List<RailTrack> tracks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            RailTrack track = new RailTrack();
            track.setPosition(new Point(x, 0));
            track.addRoute(Dir.E, Dir.W);
            track.addRoute(Dir.W, Dir.E);
            tracks.add(track);
        }
        for (int x = 0; x + 1 < length; x++) {
            tracks.get(x).connect(Dir.E, tracks.get(x + 1));
            tracks.get(x + 1).connect(Dir.W, tracks.get(x));
        }
        return tracks;
    }
}
