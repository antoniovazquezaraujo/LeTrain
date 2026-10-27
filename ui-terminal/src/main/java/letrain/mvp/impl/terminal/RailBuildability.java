package letrain.mvp.impl.terminal;

import java.util.Objects;
import java.util.Optional;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SpeedSignal;
import letrain.track.Station;

/**
 * Shared preflight for automatic route construction (ADR-037).
 *
 * <p>
 * It answers the one question the preview and the queue must agree on: can every planned cell be
 * materialised as an <b>isolated</b> rail piece? A cell is unbuildable when the rail map already
 * has a track there or when it holds a rail element (sensor, station, semaphore or speed signal).
 * The preview uses it while the author is still choosing, and {@link RailTrackMaterializer} uses it
 * again as the last line of defence before a construction job starts.
 */
final class RailBuildability {

    /** What already occupies a planned cell. */
    enum Occupant {
        TRACK, RAIL_ELEMENT
    }

    /** First unbuildable cell found in a plan, with the reason the author must see. */
    record Issue(Point cell, Occupant occupant) {
        Issue {
            Objects.requireNonNull(cell, "cell must not be null");
            Objects.requireNonNull(occupant, "occupant must not be null");
            cell = new Point(cell);
        }

        /**
         * Concise, lower-case reason suitable for the status line, e.g.
         * {@code cell (2,0) already has track}.
         */
        String description() {
            String what = occupant == Occupant.TRACK ? "track" : "a rail element";
            return "cell (" + cell + ") already has " + what;
        }
    }

    private RailBuildability() {}

    /**
     * Checks a feasible plan cell by cell. An infeasible plan has no cells to build, so it is not a
     * buildability problem: its planner reason takes precedence.
     */
    static Optional<Issue> check(Model model, RoutePlan plan) {
        Objects.requireNonNull(model, "model must not be null");
        if (plan == null || !plan.feasible()) {
            return Optional.empty();
        }
        for (RouteStep step : plan.steps()) {
            Optional<Issue> issue = checkCell(model, step.point());
            if (issue.isPresent()) {
                return issue;
            }
        }
        return Optional.empty();
    }

    /** Checks a single cell; useful for the immediate A-pick warning. */
    static Optional<Issue> checkCell(Model model, Point point) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(point, "point must not be null");
        if (model.getRailMap().getTrackAt(point) != null) {
            return Optional.of(new Issue(point, Occupant.TRACK));
        }
        if (isOccupiedByRailElement(model, point)) {
            return Optional.of(new Issue(point, Occupant.RAIL_ELEMENT));
        }
        return Optional.empty();
    }

    private static boolean isOccupiedByRailElement(Model model, Point point) {
        for (Sensor sensor : model.getSensors()) {
            if (point.equals(sensor.getPosition())) {
                return true;
            }
        }
        for (Station station : model.getStations()) {
            if (point.equals(station.getPosition())) {
                return true;
            }
        }
        for (RailSemaphore semaphore : model.getSemaphores()) {
            if (point.equals(semaphore.getPosition())) {
                return true;
            }
        }
        for (SpeedSignal signal : model.getSpeedSignals()) {
            if (point.equals(signal.getPosition())) {
                return true;
            }
        }
        return false;
    }
}
