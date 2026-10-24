package letrain.planner;

/**
 * Authoring policy that decides which terrain the cost-aware route planner may cross.
 *
 * <p>
 * The policy is a pure planning input: it never mutates the terrain or the economy. Avoid policies
 * block cells and then optimise the <em>real</em> construction cost of the allowed cells, so the
 * returned route is the cheapest one that respects the constraint.
 */
public enum RoutePolicy {
    /** No terrain blocking; minimise geometric distance, then report the real cost/time. */
    DIRECT,
    /** Block {@code ROCK} cells; water (bridges) is allowed. */
    AVOID_MOUNTAINS,
    /** Block {@code WATER} cells; rock (tunnels) is allowed. */
    AVOID_WATER,
    /** Block both {@code WATER} and {@code ROCK} cells. */
    AVOID_BOTH,
    /** No terrain blocking; minimise real construction cost (normal/bridge/tunnel). */
    CHEAPEST
}
