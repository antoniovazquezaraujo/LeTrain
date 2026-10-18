package letrain.visitor.gdx3d;

import java.util.ArrayList;
import java.util.List;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;

/**
 * Which vehicle carries the red tail lamps and when they blink (#796). Pure maths and composition
 * queries on the GDX-free model, so selection, placement sign and blink phase are unit-testable
 * exactly like the headlight helpers ({@link HeadlightGlows}, {@link Headlights}).
 *
 * <p>
 * The lamps are <em>fixtures</em>: they belong to the vehicle, not to the sense of march. The tail
 * is the composition end opposite the white headlight locomotive; because the composition order
 * does not change when the train is reversed, a wagon at the tail keeps its lamps on the same face
 * even when it later leads. Push-pull is the exception only because the headlight locomotive itself
 * swaps ends, so the opposite end swaps with it. A lone locomotive (or no headlight at all) falls
 * back to the physical rear.
 */
public final class TailLamps {

    /** Blink cycle in game ticks: 1 s at 20 TPS. */
    public static final int BLINK_PERIOD = 20;

    /**
     * Lamp A (left) is lit during ticks {@code [0, A_END)}, lamp B (right) during
     * {@code [B_START, B_END)}; both are off in between. Cadence retuned after the first in-game
     * review (#796): ≈200 ms on each, ≈400 ms off, well under the old 600 ms.
     */
    public static final int A_END = 4;
    public static final int B_START = 12;
    public static final int B_END = 16;

    /** Each lamp carries its own ground pool; cap how many are drawn like the headlights do. */
    public static final int MAX_SOURCES = 4;

    private TailLamps() {}

    /**
     * Whether lamp {@code lamp} (0 = A, 1 = B) is lit at game tick {@code ticks}. Derived from the
     * tick counter, never from wall-clock time, so replays and scenarios stay deterministic.
     */
    public static boolean lampOn(long ticks, int lamp) {
        int phase = (int) Math.floorMod(ticks, (long) BLINK_PERIOD);
        if (lamp == 0) {
            return phase < A_END;
        }
        return phase >= B_START && phase < B_END;
    }

    /**
     * Sign, along a locomotive's own travel tangent, of the face that carries its <em>white</em>
     * headlights: {@code +1} when it is the physical front (lone locomotive, pulling, or the
     * leading loco of a push-pull) and {@code -1} when it drags its composition behind (a single
     * locomotive pushing). A locomotive with no train keeps {@code +1}, its own front. The lamps
     * are fixtures: deriving the sign from the composition instead of the sense of march is what
     * stops them from hopping to the opposite face when the train is reversed (#796).
     */
    public static float headlightSign(Train train, Locomotive locomotive) {
        if (train == null) {
            return 1f;
        }
        return locomotive == train.getPhysicalFront() ? 1f : -1f;
    }

    /**
     * The vehicle that carries the red tail lamps: the composition end opposite the white headlight
     * locomotive, or the physical rear when there is no headlight locomotive. Null when the train
     * is null or empty.
     */
    public static Linker tailVehicle(Train train) {
        if (train == null || train.isEmpty()) {
            return null;
        }
        Locomotive head = train.getHeadlightLocomotive();
        if (head == null) {
            return train.getPhysicalRear();
        }
        List<Linker> linkers = new ArrayList<>(train.getLinkers());
        int index = linkers.indexOf(head);
        if (index < 0) {
            return train.getPhysicalRear();
        }
        int toFront = index;
        int toBack = linkers.size() - 1 - index;
        return toFront >= toBack ? train.getFront() : train.getBack();
    }

    /**
     * Sign, along the vehicle's own travel tangent, of the face that carries the lamps: {@code +1}
     * when the free face looks forward (the vehicle is at the physical front) and {@code -1} when
     * it looks back. A lone locomotive keeps its whites ahead, so its reds are its only exception,
     * on the back face.
     */
    public static float outwardSign(Train train, Linker tail) {
        if (train == null || tail == null) {
            return -1f;
        }
        if (tail == train.getHeadlightLocomotive()) {
            return -1f;
        }
        return tail == train.getPhysicalFront() ? 1f : -1f;
    }

    /** Whether any locomotive of the train has its engine running; the lamps need it at night. */
    public static boolean engineRunning(Train train) {
        if (train == null) {
            return false;
        }
        for (Locomotive locomotive : train.getLocomotives()) {
            if (locomotive.isEngineOn()) {
                return true;
            }
        }
        return false;
    }
}
