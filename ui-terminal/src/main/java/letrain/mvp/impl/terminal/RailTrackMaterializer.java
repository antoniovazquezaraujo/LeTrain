package letrain.mvp.impl.terminal;

import java.util.Optional;
import letrain.construction.TrackMaterializer;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.Presenter;
import letrain.mvp.impl.RailTrackMaker;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import letrain.vehicle.Cursor;
import letrain.vehicle.Cursor.CursorMode;

/**
 * {@link TrackMaterializer} backed by the existing {@link RailTrackMaker} (ADR-037).
 *
 * <p>
 * It lays an <b>isolated</b> route: the planned cells are materialised one by one through the same
 * machinery as manual construction, chained only to the pieces this materializer built. It never
 * adopts an existing rail piece, so it cannot auto-connect to the world, create forks at a junction
 * or attach a station. Crossings are out of scope; preflight rejects any planned cell that is
 * already occupied by a rail piece or a rail element.
 *
 * <p>
 * The maker reads the model cursor, so each cell is built with the cursor temporarily placed on the
 * planned cell and facing the exit direction. The cursor position, direction and mode are restored
 * before returning, which keeps the author's navigation untouched while the queue ticks in the
 * background. Journal recording is suppressed around the build: construction is not a keyboard edit
 * and must not pollute undo/redo.
 */
final class RailTrackMaterializer implements TrackMaterializer {

    private final Presenter presenter;
    private final RailTrackMaker maker;

    RailTrackMaterializer(Presenter presenter) {
        this.presenter = presenter;
        // A dedicated maker keeps the construction chaining state (oldTrack) isolated from the
        // interactive RAILS maker and guarantees the first built cell starts a fresh piece.
        this.maker = new RailTrackMaker(presenter);
    }

    @Override
    public Optional<String> prepare(RoutePlan plan) {
        return RailBuildability.check(presenter.getModel(), plan)
                .map(RailBuildability.Issue::description);
    }

    @Override
    public void buildStep(RoutePlan plan, int index, RouteStep previous, RouteStep next) {
        if (index < 0 || index >= plan.steps().size()) {
            throw new IllegalArgumentException("Step index out of range: " + index);
        }
        RouteStep step = plan.steps().get(index);
        Point cell = step.point();
        Dir exit = exitDirection(plan, index, previous, next);

        Model model = presenter.getModel();
        Cursor cursor = model.getCursor();
        Point savedPosition = new Point(cursor.getPosition());
        Dir savedDir = cursor.getDir();
        CursorMode savedMode = cursor.getMode();
        try {
            maker.setJournalSuppressed(true);
            cursor.setPosition(new Point(cell));
            cursor.setDir(exit);
            maker.reset();
            if (!maker.makeTrack(null)) {
                throw new IllegalStateException(
                        "Unable to materialise planned cell " + cell + " at step " + index);
            }
        } finally {
            maker.setJournalSuppressed(false);
            cursor.setPosition(savedPosition);
            cursor.setDir(savedDir);
            cursor.setMode(savedMode);
        }
    }

    /**
     * Exit direction used to build {@code index}: towards the next planned cell. The last cell
     * continues straight from its entry direction (there is no next turn to encode) and a one-cell
     * plan keeps the cursor facing.
     */
    private Dir exitDirection(RoutePlan plan, int index, RouteStep previous, RouteStep next) {
        RouteStep step = plan.steps().get(index);
        if (next != null) {
            return step.point().locate(next.point());
        }
        if (previous != null) {
            return previous.point().locate(step.point());
        }
        return presenter.getModel().getCursor().getDir();
    }
}
