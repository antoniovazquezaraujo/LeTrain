package letrain.planner;

/**
 * Receives throttled progress reports from {@link RoutePlanner}.
 *
 * <p>
 * The listener is called from the thread running the plan (normally a background worker in
 * {@link RoutePlanningJob}), never per expanded node: at phase start, every
 * {@value RoutePlanner#PROGRESS_INTERVAL_EXPANSIONS} expansions and once at the end.
 */
@FunctionalInterface
public interface RoutePlanningListener {

    /** Listener used by the backwards-compatible {@link RoutePlanner#plan} overload. */
    RoutePlanningListener NO_OP = progress -> {
        // No progress consumer.
    };

    /**
     * Called with an immutable progress report.
     *
     * @param progress latest progress; never {@code null}
     */
    void onProgress(RoutePlanningProgress progress);
}
