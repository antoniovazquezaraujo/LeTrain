package letrain.planner;

/**
 * Authoring policy that decides which terrain the cost-aware route planner may cross.
 *
 * <p>
 * The policy is a pure planning input: it never mutates the terrain or the economy. Avoid policies
 * are literal: they block only the terrain named by the policy and then minimise <em>geometric
 * distance</em>. Allowed water/rock may be crossed when that is shorter, even if the bridge or
 * tunnel is expensive; the plan reports the real construction cost/time. {@link #CHEAPEST} is the
 * policy that minimises real construction cost.
 */
public enum RoutePolicy {
    /** No terrain blocking; minimise geometric distance, then report the real cost/time. */
    DIRECT,
    /** Block {@code ROCK} cells; water (bridges) is allowed. Minimise geometric distance. */
    AVOID_MOUNTAINS,
    /** Block {@code WATER} cells; rock (tunnels) is allowed. Minimise geometric distance. */
    AVOID_WATER,
    /** Block both {@code WATER} and {@code ROCK} cells. Minimise geometric distance. */
    AVOID_BOTH,
    /** No terrain blocking; minimise real construction cost (normal/bridge/tunnel). */
    CHEAPEST
}
