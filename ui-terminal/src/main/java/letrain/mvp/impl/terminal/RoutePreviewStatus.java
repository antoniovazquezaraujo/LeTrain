package letrain.mvp.impl.terminal;

import java.util.Locale;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePlanningProgress;
import letrain.planner.RoutePolicy;
import letrain.planner.RoutePreviewSession;

/**
 * Builds the one-line status shown at the bottom of the 2D map while CONNECT is active
 * (ADR-035/ADR-036).
 *
 * <p>
 * Pure string formatting so it can be unit tested without a terminal: the presenter only feeds it
 * the current session state and asks {@link TerminalView} to paint the result. Costs use the game
 * currency ({@code $}) and construction time is expressed in game minutes/hours from the session's
 * effective ticks (planner ticks plus the base delay per cell).
 */
final class RoutePreviewStatus {

    /** Game ticks that make one game minute (20 TPS, one game minute per real second). */
    static final long TICKS_PER_GAME_MINUTE = 20;

    private RoutePreviewStatus() {}

    /** Width of the animated progress bar while PLANNING, excluding the brackets. */
    static final int PLANNING_BAR_WIDTH = 8;

    /** Status line for the current session, or an empty string when there is no session. */
    static String format(RoutePreviewSession session) {
        return format(session, null);
    }

    /**
     * Status line for the current session, including an optional buildability warning for the
     * current plan. A warning never hides the planner reason of an infeasible plan.
     */
    static String format(RoutePreviewSession session, String buildWarning) {
        if (session == null) {
            return "";
        }
        if (session.state() == RoutePreviewSession.State.PLANNING) {
            return formatPlanning(session.planningProgress().orElse(null));
        }
        return format(session.state(), session.policy(), session.lastPlan().orElse(null),
                session.effectiveConstructionTicks(), session.lastMessage().orElse(null),
                buildWarning);
    }

    static String format(RoutePreviewSession.State state, RoutePolicy policy, RoutePlan plan,
            long effectiveTicks, String message) {
        return format(state, policy, plan, effectiveTicks, message, null);
    }

    static String format(RoutePreviewSession.State state, RoutePolicy policy, RoutePlan plan,
            long effectiveTicks, String message, String buildWarning) {
        switch (state) {
            case PICK_START:
                return "Connect · PICK_START · move + Enter · Esc cancel";
            case PICK_END:
                String reason = message == null || message.isBlank() ? "" : " · " + message;
                return "Connect · PICK_END · A set · move + Enter" + reason + " · Esc cancel";
            case PLANNING:
                return formatPlanning(null);
            case PREVIEW:
                return previewLine(policy, plan, effectiveTicks, message, buildWarning);
            case SELECTED:
                return "Connect · SELECTED · plan stored (no construction)";
            case CANCELLED:
            default:
                return "Connect · cancelled";
        }
    }

    /**
     * PLANNING status: phase, expanded nodes, elapsed time and an indeterminate/animated bar. The
     * bar is driven from the progress snapshot (nodes and elapsed time), never from a fake
     * percentage, and contains no cost or Enter hint because there is no plan yet.
     */
    static String formatPlanning(RoutePlanningProgress progress) {
        int nodes = progress == null ? 0 : progress.expandedNodes();
        long elapsedMillis = progress == null ? 0 : progress.elapsedMillis();
        String phase = progress == null ? "starting" : progress.phase().name();
        return "Connect · PLANNING · " + phase + " · " + formatNodes(nodes) + " · "
                + formatSeconds(elapsedMillis) + " · " + progressBar(nodes, elapsedMillis) + " · "
                + "Esc cancel";
    }

    /** Expanded-node count: exact below 1000, {@code 234k} in the thousands, {@code 1.2M} above. */
    static String formatNodes(int nodes) {
        if (nodes < 1_000) {
            return nodes + (nodes == 1 ? " node" : " nodes");
        }
        if (nodes < 1_000_000) {
            double thousands = nodes / 1_000.0;
            if (thousands < 10) {
                return String.format(Locale.ROOT, "%.1fk nodes", thousands);
            }
            return Math.round(thousands) + "k nodes";
        }
        return String.format(Locale.ROOT, "%.1fM nodes", nodes / 1_000_000.0);
    }

    /** Elapsed planning time with one decimal: {@code 3.2 s}. */
    static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.1f s", Math.max(0, millis) / 1_000.0);
    }

    /**
     * Indeterminate bar with a two-cell activity window that travels with the snapshot. Same
     * snapshot always renders the same bar, so status tests stay deterministic, while successive
     * frames move the window and show that the worker is alive.
     */
    static String progressBar(int expandedNodes, long elapsedMillis) {
        int width = PLANNING_BAR_WIDTH;
        int travel = (int) Math.floorMod(elapsedMillis / 120 + expandedNodes / 250, (long) width);
        StringBuilder bar = new StringBuilder(width);
        for (int cell = 0; cell < width; cell++) {
            int distance = Math.floorMod(cell - travel, width);
            bar.append(distance < 2 ? '█' : '░');
        }
        return "[" + bar + "]";
    }

    private static String previewLine(RoutePolicy policy, RoutePlan plan, long effectiveTicks,
            String message, String buildWarning) {
        String policyName = policyLabel(policy);
        String hints = " · Space A/B · [ ] policy · Esc discard";
        if (plan == null || !plan.feasible()) {
            String reason =
                    message == null || message.isBlank() ? "no route" : "no route: " + message;
            return "Connect · " + policyName + " · " + reason + hints;
        }
        String warning = warningSuffix(buildWarning);
        String action = warning.isEmpty() ? " · Enter select" : " · Enter disabled";
        return "Connect · " + policyName + " · " + formatCost(plan.totalCost()) + " · "
                + formatTicks(effectiveTicks) + " · " + plan.steps().size() + " cells" + action
                + warning + hints;
    }

    /**
     * Short warning suffix shown in PREVIEW when the queue would reject the feasible plan. The
     * cost/time/cells figures stay before it so the author still sees what the plan would cost.
     */
    private static String warningSuffix(String buildWarning) {
        if (buildWarning == null || buildWarning.isBlank()) {
            return "";
        }
        return " · ! cannot build: " + buildWarning;
    }

    /**
     * Policy name plus a short reminder of what it blocks. The status line is the only place where
     * the author can see why a route is allowed to cross rock or water, so "AVOID_MOUNTAINS" must
     * not be confused with "AVOID_WATER" or with the unrestricted DIRECT/CHEAPEST policies.
     */
    static String policyLabel(RoutePolicy policy) {
        if (policy == null) {
            return "?";
        }
        return switch (policy) {
            case CHEAPEST -> "CHEAPEST (may cross)";
            case DIRECT -> "DIRECT (crosses all)";
            case AVOID_MOUNTAINS -> "AVOID_MOUNTAINS (no rock)";
            case AVOID_WATER -> "AVOID_WATER (no water)";
            case AVOID_ALL -> "AVOID_ALL (no water/rock)";
        };
    }

    /** Game currency with dot grouping, matching the ADR examples ({@code $1.234.500}). */
    static String formatCost(double cost) {
        long rounded = Math.round(cost);
        String grouped = String.format(Locale.US, "%,d", Math.abs(rounded)).replace(',', '.');
        return (rounded < 0 ? "-$" : "$") + grouped;
    }

    /**
     * Construction time from game ticks: 20 ticks per game minute, rounded up so a non-zero build
     * never reads as {@code 0 min}. Hours are shown only when they are needed.
     */
    static String formatTicks(long ticks) {
        if (ticks <= 0) {
            return "0 min";
        }
        long totalMinutes = (ticks + TICKS_PER_GAME_MINUTE - 1) / TICKS_PER_GAME_MINUTE;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }
}
