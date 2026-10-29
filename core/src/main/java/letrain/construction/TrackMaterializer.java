package letrain.construction;

import java.util.Optional;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Port that turns a {@link RoutePlan} into real track cells (ADR-037).
 *
 * <p>
 * The construction core only knows this interface: it never touches {@code Model},
 * {@code RailTrackMaker}, views or the terminal. The future terminal/UI slice will provide an
 * adapter backed by the existing track machinery.
 *
 * <p>
 * Construction is an <b>isolated route</b>: the adapter must lay the planned cells as a fresh piece
 * of track, without connecting to existing rail, creating forks or linking stations. Crossings and
 * forks are out of scope for this flow.
 */
public interface TrackMaterializer {

    /**
     * Preflight of a whole plan. Runs once before any cell is built.
     *
     * <p>
     * The adapter must not mutate the world, the economy or the plan: if it cannot build the route
     * it returns the reason and the job is rejected with no materialisation. An empty result means
     * the plan can be built.
     *
     * @param plan plan to validate
     * @return rejection reason, or empty when the plan is buildable
     */
    Optional<String> prepare(RoutePlan plan);

    /**
     * Materialises exactly the planned cell at {@code index}.
     *
     * @param plan complete plan being built
     * @param index index of the cell to build in {@code plan.steps()}
     * @param previous previous step in the plan, or {@code null} for the first cell
     * @param next next step in the plan, or {@code null} for the last cell
     */
    void buildStep(RoutePlan plan, int index, RouteStep previous, RouteStep next);

    /**
     * Called once when the job finishes or is cancelled. Implementations must be safe to call even
     * when nothing was built.
     */
    default void finish() {
        // No-op by default: simple adapters do not need a cleanup hook.
    }
}
