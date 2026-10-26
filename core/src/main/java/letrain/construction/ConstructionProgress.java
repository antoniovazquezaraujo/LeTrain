package letrain.construction;

import java.util.Optional;
import letrain.planner.RouteStep;

/**
 * Immutable snapshot of a construction job (ADR-037).
 *
 * <p>
 * It is deliberately UI-free: a presenter may format it, but the construction core never knows
 * about views, terminals or glyphs.
 *
 * @param state current lifecycle state
 * @param builtSteps number of planned cells already materialised (0 for rejected/cancelled before
 *        the first cell)
 * @param totalSteps number of cells in the plan
 * @param currentStep cell currently being built, or {@code null} when there is none (idle,
 *        rejected, done or cancelled)
 * @param remainingTicks ticks still needed for {@code currentStep} at the time of the snapshot;
 *        {@code 0} when there is no pending work in the current cell
 * @param reason rejection or cancellation message, or {@code null} when there is none
 */
public record ConstructionProgress(ConstructionState state, int builtSteps, int totalSteps,
        RouteStep currentStep, long remainingTicks, String reason) {

    public ConstructionProgress {
        if (state == null) {
            throw new NullPointerException("state must not be null");
        }
        if (builtSteps < 0) {
            throw new IllegalArgumentException(
                    "builtSteps must be non-negative, but was: " + builtSteps);
        }
        if (totalSteps < 0) {
            throw new IllegalArgumentException(
                    "totalSteps must be non-negative, but was: " + totalSteps);
        }
        if (builtSteps > totalSteps) {
            throw new IllegalArgumentException("builtSteps must not exceed totalSteps, but was: "
                    + builtSteps + " > " + totalSteps);
        }
        if (remainingTicks < 0) {
            throw new IllegalArgumentException(
                    "remainingTicks must be non-negative, but was: " + remainingTicks);
        }
    }

    /** Progress of a queue with no job. */
    public static ConstructionProgress idle() {
        return new ConstructionProgress(ConstructionState.IDLE, 0, 0, null, 0, null);
    }

    /** Current cell wrapped in an {@link Optional}. */
    public Optional<RouteStep> currentStepOptional() {
        return Optional.ofNullable(currentStep);
    }

    /** Rejection or cancellation reason wrapped in an {@link Optional}. */
    public Optional<String> reasonOptional() {
        return Optional.ofNullable(reason);
    }

    /** True when the job will not do any further work. */
    public boolean isTerminal() {
        return state == ConstructionState.REJECTED || state == ConstructionState.DONE
                || state == ConstructionState.CANCELLED;
    }
}
