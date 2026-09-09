package letrain.command;

import letrain.mvp.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.utils.ValidationUtils;

/**
 * Headless turtle builder: executes the {@code write/move/del/clear} turtle commands on a
 * {@link Model} through the exact same {@link RailTrackMaker} machinery the interactive UI uses.
 *
 * <p>
 * The console ({@code PlayerCommandExecutor} + both 2D/3D presenters) currently wires this same
 * behaviour inline as anonymous {@link TurtleDelegate}s. This class is the single, reusable,
 * view-agnostic implementation of that wiring so a future headless scenario executor (ADR-020) can
 * replay a script without a screen.
 *
 * <p>
 * <b>Builder semantics implemented (as of the current engine):</b>
 * <ul>
 * <li><b>Start cell:</b> a {@code write} builds the first piece on the cell the cursor is standing
 * on (the "next free cell" of the line), exactly like pressing Shift+arrow in Rails mode.
 * {@code RailTrackMaker.makeTrack} places the piece at {@code cursor.getPosition()}
 * (RailTrackMaker.makeTrack) and then advances the cursor one cell along the current direction
 * ({@code newPos.move(cursor.getDir(), 1)}).</li>
 * <li><b>Advance:</b> after every piece the cursor moves one cell forward, so {@code write N}
 * produces N consecutive, connected pieces starting at the initial cursor cell.</li>
 * <li><b>Connection:</b> chaining is kept by {@code RailTrackMaker.oldTrack}: each new piece is
 * connected to the previous one ({@code track.connect(oldDir, oldTrack)}) and the previous piece is
 * connected back, so separate {@code write} commands issued while the cursor is still adjacent to
 * the last laid rail continue the same line.</li>
 * <li><b>Terrain:</b> the piece type is derived from the ground under the cursor
 * ({@code RailTrackMaker.detectTrackType}): GROUND→NORMAL, WATER→BRIDGE (+ gates at the edges),
 * ROCK→TUNNEL (+ gates at the edges), mirroring manual construction.</li>
 * <li><b>Fork/divergence:</b> when the cursor stands on an existing piece and the next piece exits
 * through a third direction, {@code RailTrackMaker} promotes that tile to a
 * {@code ForkRailTrack}, same as the UI.</li>
 * <li><b>Curves:</b> {@code turnLeft/turnRight} rotate the cursor facing. The 45-degree rule is
 * enforced by the maker ({@code Math.abs(oldDir.inverse().angularDistance(dir)) > 1} aborts).</li>
 * </ul>
 *
 * <p>
 * <b>Determinism contract:</b> given the same base {@link Model} state (same seed/terrain, same
 * cursor) and the same script, replaying through this builder produces an identical final model
 * state. The builder keeps no state beyond the {@link RailTrackMaker} it is given, and the maker
 * state is entirely derived from the model + the sequence of operations.
 */
public class TurtleBuilder implements TurtleDelegate {

    private final Model model;
    private final RailTrackMaker trackMaker;

    /**
     * @param model      the model being edited (also exposed via {@link #getModel()} so a headless
     *                   executor can inspect cursor/state after a sequence)
     * @param trackMaker the track maker wired to the {@code model}; for headless execution the
     *                   caller provides a maker backed by a stub/mock presenter whose {@code view}
     *                   is a no-op (see tests).
     */
    public TurtleBuilder(Model model, RailTrackMaker trackMaker) {
        this.model = ValidationUtils.requireNonNull(model, "model");
        this.trackMaker = ValidationUtils.requireNonNull(trackMaker, "trackMaker");
    }

    public Model getModel() {
        return model;
    }

    public RailTrackMaker getTrackMaker() {
        return trackMaker;
    }

    @Override
    public void startSequence() {
        trackMaker.setJournalSuppressed(true);
        trackMaker.reset();
        trackMaker.makingTracks = false;
    }

    @Override
    public void moveForward() {
        trackMaker.cursorForward();
    }

    @Override
    public void buildForward() {
        trackMaker.createTrack(null);
    }

    @Override
    public void eraseForward() {
        trackMaker.removeTrack(true);
    }

    @Override
    public void turnLeft() {
        trackMaker.cursorTurnLeft();
    }

    @Override
    public void turnRight() {
        trackMaker.cursorTurnRight();
    }

    @Override
    public void endSequence() {
        trackMaker.makingTracks = false;
        trackMaker.setJournalSuppressed(false);
    }
}
