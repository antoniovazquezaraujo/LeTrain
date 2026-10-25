package letrain.mvp.impl.terminal;

import java.util.Locale;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RoutePreviewSession;

/**
 * Builds the one-line status shown at the bottom of the 2D map while CONNECT is active (ADR-035).
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

    /** Status line for the current session, or an empty string when there is no session. */
    static String format(RoutePreviewSession session) {
        if (session == null) {
            return "";
        }
        return format(session.state(), session.policy(), session.lastPlan().orElse(null),
                session.effectiveConstructionTicks(), session.lastMessage().orElse(null));
    }

    static String format(RoutePreviewSession.State state, RoutePolicy policy, RoutePlan plan,
            long effectiveTicks, String message) {
        switch (state) {
            case PICK_START:
                return "Connect · PICK_START · move + Enter · Esc cancel";
            case PICK_END:
                return "Connect · PICK_END · A set · move + Enter · Esc cancel";
            case PREVIEW:
                return previewLine(policy, plan, effectiveTicks, message);
            case SELECTED:
                return "Connect · SELECTED · plan stored (no construction)";
            case CANCELLED:
            default:
                return "Connect · cancelled";
        }
    }

    private static String previewLine(RoutePolicy policy, RoutePlan plan, long effectiveTicks,
            String message) {
        String policyName = policy == null ? "?" : policy.name();
        String hints = " · ←/→ policy · Esc discard";
        if (plan == null || !plan.feasible()) {
            String reason =
                    message == null || message.isBlank() ? "no route" : "no route: " + message;
            return "Connect · " + policyName + " · " + reason + hints;
        }
        return "Connect · " + policyName + " · " + formatCost(plan.totalCost()) + " · "
                + formatTicks(effectiveTicks) + " · " + plan.steps().size()
                + " tramos · Enter select" + hints;
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
