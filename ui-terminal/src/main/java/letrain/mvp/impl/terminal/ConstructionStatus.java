package letrain.mvp.impl.terminal;

import letrain.construction.ConstructionProgress;
import letrain.planner.RoutePlan;

/**
 * One-line construction status and notices for the 2D map (ADR-037).
 *
 * <p>
 * Pure formatting: costs, cells and time come from the accepted plan/progress, never from a guessed
 * estimate. Time uses the same game-minute conversion as the route preview and includes the queue's
 * one tick per cell on top of the planner time.
 */
final class ConstructionStatus {

    static final String BLOCKED_COMMAND_MESSAGE =
            "Only navigation commands are available while construction is active";
    static final String CONFIRM_CANCEL =
            "Cancel construction? Esc again to confirm · any key to continue";
    static final String COMPLETE = "Construction complete";
    static final String CANCELLED = "Construction cancelled · partial track remains";

    private ConstructionStatus() {}

    /** Normal {@code Building · built/total · $cost · time · Esc cancel} line. */
    static String format(ConstructionProgress progress, RoutePlan plan, boolean cancelArmed) {
        double cost = plan == null ? 0 : plan.totalCost();
        long ticks = plan == null ? 0
                : Math.addExact(plan.totalConstructionTicks(), plan.steps().size());
        return format(progress, plan, cancelArmed, cost, ticks);
    }

    /** Same line with the prepaid quote cost/time for an automatic company work. */
    static String format(ConstructionProgress progress, RoutePlan plan, boolean cancelArmed,
            double quotedCost, long quotedTicks) {
        if (cancelArmed) {
            return CONFIRM_CANCEL;
        }
        int total =
                progress != null ? progress.totalSteps() : plan == null ? 0 : plan.steps().size();
        int built = progress != null ? progress.builtSteps() : 0;
        return "Building · " + built + "/" + total + " · "
                + RoutePreviewStatus.formatCost(quotedCost) + " · "
                + RoutePreviewStatus.formatTicks(quotedTicks) + " · Esc cancel";
    }

    static String rejected(String reason) {
        String detail = reason == null || reason.isBlank() ? "route cannot be built" : reason;
        return "Construction rejected · " + detail;
    }
}
