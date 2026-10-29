package letrain.planner;

import java.util.Objects;
import letrain.map.Point;
import letrain.mvp.Presenter;

/**
 * One planned track cell: where to build and which rail kind the terrain requires.
 *
 * <p>
 * Gate types ({@code BRIDGE_GATE_TRACK}, {@code TUNNEL_GATE_TRACK}) are deliberately <b>not</b>
 * modelled here. Gates are a construction-time transition detail (see
 * {@code RailTrackMaker.detectTrackType}); the authoring plan only needs to say "normal", "bridge"
 * or "tunnel".
 *
 * <p>
 * Immutable: the position is copied on construction so a caller cannot mutate a plan through the
 * {@link Point} it handed to the planner.
 */
public final class RouteStep {

    private final Point point;
    private final Presenter.TrackType trackType;

    public RouteStep(Point point, Presenter.TrackType trackType) {
        this.point = new Point(Objects.requireNonNull(point, "point must not be null"));
        this.trackType = Objects.requireNonNull(trackType, "trackType must not be null");
    }

    public Point point() {
        return new Point(point);
    }

    public Presenter.TrackType trackType() {
        return trackType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteStep step)) {
            return false;
        }
        return point.getX() == step.point.getX() && point.getY() == step.point.getY()
                && trackType == step.trackType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(point.getX(), point.getY(), trackType);
    }

    @Override
    public String toString() {
        return "RouteStep[" + point + ", " + trackType + "]";
    }
}
