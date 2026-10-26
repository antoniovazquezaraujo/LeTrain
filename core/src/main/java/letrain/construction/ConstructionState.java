package letrain.construction;

/**
 * Lifecycle of a {@link RouteConstructionJob} (ADR-037).
 *
 * <p>
 * {@link #IDLE} is the "no construction" state used by {@link ConstructionProgress#idle()} when a
 * queue has no job. Once a job exists it moves to {@link #BUILDING}, {@link #REJECTED} or
 * {@link #DONE}; a running job can also become {@link #CANCELLED}.
 */
public enum ConstructionState {
    /** No job has been started, or the queue is empty. */
    IDLE,
    /** Preflight refused the plan; nothing was materialised. */
    REJECTED,
    /** The job is materialising planned cells, one at a time. */
    BUILDING,
    /** Every planned cell was materialised. */
    DONE,
    /** The author stopped the job; cells built so far remain in the world. */
    CANCELLED
}
