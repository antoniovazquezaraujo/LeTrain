package letrain.planner;

import java.util.Objects;
import java.util.Optional;
import letrain.map.Point;

/**
 * Immutable progress report emitted by {@link RoutePlanner} while it searches for a route.
 *
 * <p>
 * The planner does not know how many cells it will eventually expand, so this is an activity signal
 * rather than a percentage: a phase, the number of expanded nodes in that phase, the time elapsed
 * since planning started and, optionally, the cell currently being expanded.
 *
 * <p>
 * {@link #current()} may be {@code null}; use {@link #currentPosition()} when an {@link Optional}
 * is preferred. Both return a defensive copy, so callers cannot mutate the planner's state.
 */
public record RoutePlanningProgress(Phase phase, int expandedNodes, long elapsedMillis,
        Point current) {

    /** Search phase that produced this report. */
    public enum Phase {
        /** The single A* search that produces the plan. */
        SEARCH
    }

    public RoutePlanningProgress {
        Objects.requireNonNull(phase, "phase must not be null");
        if (expandedNodes < 0) {
            throw new IllegalArgumentException("expandedNodes must be non-negative");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("elapsedMillis must be non-negative");
        }
        current = current == null ? null : new Point(current.getX(), current.getY());
    }

    /** Cell currently being expanded, or {@code null} when the report has no current cell. */
    @Override
    public Point current() {
        return current == null ? null : new Point(current.getX(), current.getY());
    }

    /** {@link Optional} view of {@link #current()}. */
    public Optional<Point> currentPosition() {
        return Optional.ofNullable(current());
    }
}
