package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure logic of the red tail lamps (#796): which vehicle carries them, on which face, and when they
 * blink. The 3D renderer only draws what these queries decide, so the whole selection and phase
 * rule is pinned here without any GDX context.
 */
@DisplayName("Red tail lamps logic (#796)")
class TailLampsTest {

    private static final float EPSILON = 1e-4f;

    // ---------------------------------------------------------------------------------------------
    // Blink phase
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("lamp A is lit during the first 300 ms of the cycle (ticks 0-5)")
    void givenPhaseZeroToFive_whenQueried_thenLampAOnAndBOff() {
        for (long ticks = 0; ticks <= 5; ticks++) {
            assertTrue(TailLamps.lampOn(ticks, 0), "A must be on at tick " + ticks);
            assertFalse(TailLamps.lampOn(ticks, 1), "B must be off at tick " + ticks);
        }
    }

    @Test
    @DisplayName("both lamps are dark during the gap (ticks 6-13)")
    void givenGapPhase_whenQueried_thenBothLampsOff() {
        for (long ticks = 6; ticks <= 13; ticks++) {
            assertFalse(TailLamps.lampOn(ticks, 0), "A must be off at tick " + ticks);
            assertFalse(TailLamps.lampOn(ticks, 1), "B must be off at tick " + ticks);
        }
    }

    @Test
    @DisplayName("lamp B is lit during the last 300 ms of the cycle (ticks 14-19)")
    void givenPhaseFourteenToNineteen_whenQueried_thenLampBOnAndAOff() {
        for (long ticks = 14; ticks <= 19; ticks++) {
            assertFalse(TailLamps.lampOn(ticks, 0), "A must be off at tick " + ticks);
            assertTrue(TailLamps.lampOn(ticks, 1), "B must be on at tick " + ticks);
        }
    }

    @Test
    @DisplayName("the cycle repeats every 20 ticks")
    void givenNextCycle_whenQueried_thenPhaseWrapsBack() {
        // Tick 20 == tick 0 (A), tick 21 == tick 1 (A), tick 34 == tick 14 (B).
        assertTrue(TailLamps.lampOn(TailLamps.BLINK_PERIOD, 0));
        assertFalse(TailLamps.lampOn(TailLamps.BLINK_PERIOD, 1));
        assertTrue(TailLamps.lampOn(21, 0));
        assertTrue(TailLamps.lampOn(34, 1));
        assertFalse(TailLamps.lampOn(34, 0));
    }

    @Test
    @DisplayName("phase boundaries are exact: A_END and B_START")
    void givenBoundaryTicks_whenQueried_thenExactLimits() {
        assertTrue(TailLamps.lampOn(TailLamps.A_END - 1, 0));
        assertFalse(TailLamps.lampOn(TailLamps.A_END, 0));
        assertFalse(TailLamps.lampOn(TailLamps.B_START - 1, 1));
        assertTrue(TailLamps.lampOn(TailLamps.B_START, 1));
    }

    @Test
    @DisplayName("the phase is derived from the tick counter, negatives included")
    void givenNegativeTicks_whenQueried_thenPhaseIsStillDeterministic() {
        // floorMod(-1, 20) == 19 -> lamp B on.
        assertTrue(TailLamps.lampOn(-1, 1));
        assertFalse(TailLamps.lampOn(-1, 0));
        assertTrue(TailLamps.lampOn(-TailLamps.BLINK_PERIOD, 0));
    }

    @Test
    @DisplayName("the constants match the documented 20-tick cycle")
    void should_ExposeTheDocumentedCycleConstants() {
        assertEquals(20, TailLamps.BLINK_PERIOD);
        assertEquals(6, TailLamps.A_END);
        assertEquals(14, TailLamps.B_START);
    }

    // ---------------------------------------------------------------------------------------------
    // Which vehicle carries the lamps
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("tail of [loco][wagons] is the last wagon and does not move on reversal")
    void givenTailWagon_whenReversed_thenLampsStayOnTheSameWagon() {
        Train train = new Train(1);
        Locomotive loco = new Locomotive(1, "A");
        Wagon last = new Wagon("w");
        train.pushBack(loco);
        train.pushBack(new Wagon("v"));
        train.pushBack(last);
        train.setDirectorLinker(loco);

        Linker tail = TailLamps.tailVehicle(train);
        assertSame(last, tail);

        // Reversing the train does not reorder the composition, so a single-loco train keeps the
        // tail wagon: only the outward sign flips (see the sign test below).
        loco.setReversed(true);
        assertSame(last, TailLamps.tailVehicle(train));
    }

    @Test
    @DisplayName("push-pull: the tail is the physical rear and swaps ends on reversal")
    void givenPushPull_whenReversed_thenTailSwapsToTheOtherLocomotive() {
        Train train = new Train(2);
        Locomotive frontLoco = new Locomotive(2, "A");
        Locomotive rearLoco = new Locomotive(3, "B");
        train.pushBack(frontLoco);
        train.pushBack(new Wagon("w"));
        train.pushBack(rearLoco);
        train.setDirectorLinker(frontLoco);

        assertSame(rearLoco, TailLamps.tailVehicle(train));
        assertSame(train.getPhysicalRear(), TailLamps.tailVehicle(train));

        frontLoco.setReversed(true);

        assertSame(frontLoco, TailLamps.tailVehicle(train));
        assertSame(train.getPhysicalRear(), TailLamps.tailVehicle(train));
    }

    @Test
    @DisplayName("a lone locomotive carries the tail lamps itself, on its back face")
    void givenLoneLocomotive_whenQueried_thenTailIsTheLocomotiveWithNegativeSign() {
        Train train = new Train(3);
        Locomotive loco = new Locomotive(3, "A");
        train.pushBack(loco);
        train.setDirectorLinker(loco);

        assertSame(loco, TailLamps.tailVehicle(train));
        assertEquals(-1f, TailLamps.outwardSign(train, loco), EPSILON);
    }

    @Test
    @DisplayName("no headlight locomotive: falls back to the physical rear")
    void givenSeveralLocomotivesAndWagonFront_whenQueried_thenTailIsThePhysicalRear() {
        Train train = new Train(4);
        Locomotive first = new Locomotive(4, "A");
        Locomotive second = new Locomotive(5, "B");
        train.pushBack(new Wagon("w"));
        train.pushBack(first);
        train.pushBack(second);
        train.setDirectorLinker(first);

        // No white headlight (wagon at front) -> the reds fall back to the physical rear.
        assertNull(train.getHeadlightLocomotive());
        assertSame(train.getPhysicalRear(), TailLamps.tailVehicle(train));
        assertSame(second, TailLamps.tailVehicle(train));
    }

    @Test
    @DisplayName("null or empty train has no tail")
    void givenNullOrEmptyTrain_whenQueried_thenNoTail() {
        assertNull(TailLamps.tailVehicle(null));
        assertNull(TailLamps.tailVehicle(new Train(5)));
    }

    // ---------------------------------------------------------------------------------------------
    // Which face of the tail vehicle
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a tail wagon keeps its lamps on the same face; the sign flips on reversal")
    void givenTailWagon_whenReversed_thenOutwardSignFlips() {
        Train train = new Train(6);
        Locomotive loco = new Locomotive(6, "A");
        Wagon tail = new Wagon("w");
        train.pushBack(loco);
        train.pushBack(tail);
        train.setDirectorLinker(loco);

        // Normal: the wagon is at the physical rear, its free face looks backwards.
        assertEquals(-1f, TailLamps.outwardSign(train, tail), EPSILON);

        // Reversed: the same wagon now leads, so the lamps' face looks forward.
        loco.setReversed(true);
        assertEquals(1f, TailLamps.outwardSign(train, tail), EPSILON);
    }

    @Test
    @DisplayName("outward sign defaults to -1 when the train or tail is missing")
    void givenMissingTrainOrTail_whenQueried_thenNegativeSign() {
        Train train = new Train(7);
        train.pushBack(new Locomotive(7, "A"));

        assertEquals(-1f, TailLamps.outwardSign(null, null), EPSILON);
        assertEquals(-1f, TailLamps.outwardSign(train, null), EPSILON);
        assertEquals(-1f, TailLamps.outwardSign(null, new Wagon("w")), EPSILON);
    }

    // ---------------------------------------------------------------------------------------------
    // Engine gate
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("no locomotive means the engine is not running")
    void givenNoLocomotive_whenQueried_thenEngineNotRunning() {
        Train train = new Train(8);
        train.pushBack(new Wagon("w"));

        assertFalse(TailLamps.engineRunning(train));
        assertFalse(TailLamps.engineRunning(null));
    }

    @Test
    @DisplayName("lamps need a running engine: any locomotive with the engine on is enough")
    void givenOneRunningLocomotive_whenQueried_thenEngineRunning() {
        Train train = new Train(9);
        Locomotive off = new Locomotive(8, "A");
        Locomotive on = new Locomotive(9, "B");
        train.pushBack(off);
        train.pushBack(new Wagon("w"));
        train.pushBack(on);

        assertFalse(TailLamps.engineRunning(train));

        on.setEngineOn(true);
        assertTrue(TailLamps.engineRunning(train));
    }
}
