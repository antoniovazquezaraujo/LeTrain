package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import letrain.audio.material.MaterialId;

/**
 * Pure planner for ordered notch transitions (ADR-029 §2/§3, PR D).
 *
 * <p>
 * Given a requested ride {@code from -> to} and the material/legacy availability of the running
 * synth, it returns the ordered stages that cover the ride. The stages follow the incremental
 * fallback chain of ADR-029 §3:
 *
 * <ol>
 * <li><b>Exact pair</b>: {@code trans-<from>-<to>} when declared.</li>
 * <li><b>Adjacent chain</b>: {@code from -> from±1 -> ... -> to} using the declared adjacent pairs;
 * stages without material fall to level 3.</li>
 * <li><b>Legacy ramp</b>: a pitch ramp over the legacy {@code cruise} segment (today's behaviour)
 * for stages without material, while {@code train-sound.wav} is retained.</li>
 * <li><b>Loop crossfade</b>: the remaining ride collapses into a crossfade to the loop of the final
 * destination, the safety net when neither material nor legacy ramp exists. A plan is never
 * empty.</li>
 * </ol>
 *
 * <p>
 * The class is stateless and side-effect free: the caller decides what "available" means (declared
 * in the profile, decodable, ...) through the predicate.
 */
public final class TransitionPlanner {

    /** Lowest notch index (idle). */
    public static final int MIN_NOTCH = 0;

    /** Highest notch index. */
    public static final int MAX_NOTCH = 10;

    /** How a single stage of a plan is rendered. */
    public enum Kind {
        /** Play the material of the stage pair (one-shot). */
        MATERIAL,
        /** Pitch ramp over the legacy {@code cruise} segment (ADR-029 §3 level 3). */
        LEGACY_RAMP,
        /** No transition material/sound: crossfade the loop to the destination (level 4). */
        LOOP_CROSSFADE
    }

    /** One ordered stage of a ride: it always starts and ends in a different notch. */
    public record Step(int from, int to, Kind kind) {

        public Step {
            Objects.requireNonNull(kind, "kind must not be null");
            requireNotch(from, "from");
            requireNotch(to, "to");
            if (from == to) {
                throw new IllegalArgumentException("step endpoints must differ: " + from);
            }
        }

        /** Material id of the stage pair; only meaningful for {@link Kind#MATERIAL}. */
        public MaterialId materialId() {
            return MaterialId.transition(from, to);
        }
    }

    /** Ordered stages covering {@code from -> to}; empty when both notches are equal. */
    public record Plan(int from, int to, List<Step> steps) {

        public Plan {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
        }

        public boolean isEmpty() {
            return steps.isEmpty();
        }

        /** First stage, or {@code null} when the plan is empty. */
        public Step first() {
            return steps.isEmpty() ? null : steps.get(0);
        }
    }

    private TransitionPlanner() {}

    /**
     * Plans the ride {@code from -> to}. Never returns an empty plan for {@code from != to}, and
     * never plays anything for {@code from == to}.
     *
     * @param materialAvailable predicate answering whether a transition material id is usable
     * @param legacyRampAvailable true when the legacy {@code cruise} segment is retained
     */
    public static Plan plan(int from, int to, Predicate<MaterialId> materialAvailable,
            boolean legacyRampAvailable) {
        Objects.requireNonNull(materialAvailable, "materialAvailable must not be null");
        requireNotch(from, "from");
        requireNotch(to, "to");

        if (from == to) {
            return new Plan(from, to, List.of());
        }
        if (materialAvailable.test(MaterialId.transition(from, to))) {
            return new Plan(from, to, List.of(new Step(from, to, Kind.MATERIAL)));
        }

        int direction = Integer.signum(to - from);
        List<Step> steps = new ArrayList<>();
        for (int notch = from; notch != to; notch += direction) {
            int next = notch + direction;
            if (materialAvailable.test(MaterialId.transition(notch, next))) {
                steps.add(new Step(notch, next, Kind.MATERIAL));
            } else if (legacyRampAvailable) {
                steps.add(new Step(notch, next, Kind.LEGACY_RAMP));
            } else {
                steps.add(new Step(notch, to, Kind.LOOP_CROSSFADE));
                break;
            }
        }
        return new Plan(from, to, steps);
    }

    private static void requireNotch(int notch, String field) {
        if (notch < MIN_NOTCH || notch > MAX_NOTCH) {
            throw new IllegalArgumentException(
                    field + " out of range [" + MIN_NOTCH + ".." + MAX_NOTCH + "]: " + notch);
        }
    }
}
