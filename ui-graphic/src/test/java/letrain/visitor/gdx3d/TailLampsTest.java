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
    @DisplayName("lamp A is lit during the first 200 ms of the cycle (ticks 0-3)")
    void givenPhaseZeroToThree_whenQueried_thenLampAOnAndBOff() {
        for (long ticks = 0; ticks < TailLamps.A_END; ticks++) {
            assertTrue(TailLamps.lampOn(ticks, 0), "A must be on at tick " + ticks);
            assertFalse(TailLamps.lampOn(ticks, 1), "B must be off at tick " + ticks);
        }
    }

    @Test
    @DisplayName("both lamps are dark during the first gap (ticks 4-11)")
    void givenFirstGap_whenQueried_thenBothLampsOff() {
        for (long ticks = TailLamps.A_END; ticks < TailLamps.B_START; ticks++) {
            assertFalse(TailLamps.lampOn(ticks, 0), "A must be off at tick " + ticks);
            assertFalse(TailLamps.lampOn(ticks, 1), "B must be off at tick " + ticks);
        }
    }

    @Test
    @DisplayName("lamp B is lit at mid-cycle (ticks 12-15)")
    void givenPhaseTwelveToFifteen_whenQueried_thenLampBOnAndAOff() {
        for (long ticks = TailLamps.B_START; ticks < TailLamps.B_END; ticks++) {
            assertFalse(TailLamps.lampOn(ticks, 0), "A must be off at tick " + ticks);
            assertTrue(TailLamps.lampOn(ticks, 1), "B must be on at tick " + ticks);
        }
    }

    @Test
    @DisplayName("both lamps are dark during the second gap (ticks 16-19)")
    void givenSecondGap_whenQueried_thenBothLampsOff() {
        for (long ticks = TailLamps.B_END; ticks < TailLamps.BLINK_PERIOD; ticks++) {
            assertFalse(TailLamps.lampOn(ticks, 0), "A must be off at tick " + ticks);
            assertFalse(TailLamps.lampOn(ticks, 1), "B must be off at tick " + ticks);
        }
    }

    @Test
    @DisplayName("the cycle repeats every 20 ticks")
    void givenNextCycle_whenQueried_thenPhaseWrapsBack() {
        // Tick 20 == tick 0 (A), tick 21 == tick 1 (A), tick 34 == tick 14 (B), tick 40 == tick 0.
        assertTrue(TailLamps.lampOn(TailLamps.BLINK_PERIOD, 0));
        assertFalse(TailLamps.lampOn(TailLamps.BLINK_PERIOD, 1));
        assertTrue(TailLamps.lampOn(21, 0));
        assertTrue(TailLamps.lampOn(34, 1));
        assertFalse(TailLamps.lampOn(34, 0));
        assertTrue(TailLamps.lampOn(40, 0));
        assertFalse(TailLamps.lampOn(36, 1));
    }

    @Test
    @DisplayName("phase boundaries are exact: A_END, B_START and B_END")
    void givenBoundaryTicks_whenQueried_thenExactLimits() {
        // A on [0, A_END): 3 on, 4 off.
        assertTrue(TailLamps.lampOn(TailLamps.A_END - 1, 0));
        assertFalse(TailLamps.lampOn(TailLamps.A_END, 0));
        // B on [B_START, B_END): 11 off, 12 on, 15 on, 16 off.
        assertFalse(TailLamps.lampOn(TailLamps.B_START - 1, 1));
        assertTrue(TailLamps.lampOn(TailLamps.B_START, 1));
        assertTrue(TailLamps.lampOn(TailLamps.B_END - 1, 1));
        assertFalse(TailLamps.lampOn(TailLamps.B_END, 1));
        // Wrap boundary: 19 off, 20 == 0 on for A.
        assertFalse(TailLamps.lampOn(19, 1));
        assertTrue(TailLamps.lampOn(TailLamps.BLINK_PERIOD, 0));
    }

    @Test
    @DisplayName("the phase is derived from the tick counter, negatives included")
    void givenNegativeTicks_whenQueried_thenPhaseIsStillDeterministic() {
        // floorMod(-1, 20) == 19 -> both off (second gap).
        assertFalse(TailLamps.lampOn(-1, 0));
        assertFalse(TailLamps.lampOn(-1, 1));
        // floorMod(-5, 20) == 15 -> lamp B on.
        assertTrue(TailLamps.lampOn(-5, 1));
        assertFalse(TailLamps.lampOn(-5, 0));
        // floorMod(-20, 20) == 0 -> lamp A on.
        assertTrue(TailLamps.lampOn(-TailLamps.BLINK_PERIOD, 0));
    }

    @Test
    @DisplayName("the constants match the documented 20-tick cycle")
    void should_ExposeTheDocumentedCycleConstants() {
        assertEquals(20, TailLamps.BLINK_PERIOD);
        assertEquals(4, TailLamps.A_END);
        assertEquals(12, TailLamps.B_START);
        assertEquals(16, TailLamps.B_END);
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
    // White headlight face (fix for the sense-of-march flip, #796)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a locomotive with no train keeps its whites on its own front (+1)")
    void givenLoneLocomotiveWithoutTrain_whenQueried_thenHeadlightSignPositive() {
        assertEquals(1f, TailLamps.headlightSign(null, new Locomotive(1, "A")), EPSILON);
    }

    @Test
    @DisplayName("a single locomotive pulling has its whites on the physical front (+1)")
    void givenSingleLocomotivePulling_whenQueried_thenHeadlightSignPositive() {
        Train train = new Train(1);
        Locomotive loco = new Locomotive(1, "A");
        train.pushBack(loco);
        train.pushBack(new Wagon("w"));
        train.setDirectorLinker(loco);

        assertEquals(1f, TailLamps.headlightSign(train, loco), EPSILON);
    }

    @Test
    @DisplayName("a single locomotive pushing has its whites on the back face (-1)")
    void givenSingleLocomotivePushing_whenQueried_thenHeadlightSignNegative() {
        // Physical front is the wagon, so the sole locomotive drags the consist behind it.
        Train train = new Train(2);
        Locomotive loco = new Locomotive(2, "A");
        train.pushBack(new Wagon("w"));
        train.pushBack(loco);
        train.setDirectorLinker(loco);

        assertEquals(-1f, TailLamps.headlightSign(train, loco), EPSILON);
    }

    @Test
    @DisplayName("push-pull: leading loco +1, trailing loco -1 (pure, even if it does not light)")
    void givenPushPull_whenQueried_thenSignsAreOpposite() {
        Train train = new Train(3);
        Locomotive frontLoco = new Locomotive(3, "A");
        Locomotive rearLoco = new Locomotive(4, "B");
        train.pushBack(frontLoco);
        train.pushBack(new Wagon("w"));
        train.pushBack(rearLoco);
        train.setDirectorLinker(frontLoco);

        assertEquals(1f, TailLamps.headlightSign(train, frontLoco), EPSILON);
        assertEquals(-1f, TailLamps.headlightSign(train, rearLoco), EPSILON);
    }

    @Test
    @DisplayName("push-pull inverted: the two signs swap")
    void givenPushPull_whenReversed_thenSignsSwap() {
        Train train = new Train(4);
        Locomotive frontLoco = new Locomotive(5, "A");
        Locomotive rearLoco = new Locomotive(6, "B");
        train.pushBack(frontLoco);
        train.pushBack(new Wagon("w"));
        train.pushBack(rearLoco);
        train.setDirectorLinker(frontLoco);

        frontLoco.setReversed(true);

        assertEquals(-1f, TailLamps.headlightSign(train, frontLoco), EPSILON);
        assertEquals(1f, TailLamps.headlightSign(train, rearLoco), EPSILON);
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
