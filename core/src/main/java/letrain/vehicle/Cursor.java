package letrain.vehicle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import letrain.map.Point;
import letrain.visitor.Visitor;

public class Cursor extends Vehicle {
    public enum CursorMode {
        DRAWING, ERASING, MOVING, MAKING_TRACKS
    }

    /** How long the locate flash triggered by the 'o' key stays on screen (milliseconds). */
    public static final long PING_DURATION_MS = 300;

    private CursorMode mode;
    private float progress = 0f;

    /** Wall-clock instant (ms) until which the locate flash must be painted. */
    @JsonIgnore
    private long pingUntil = Long.MIN_VALUE;

    public void setProgress(float p) {
        this.progress = p;
    }

    public float getProgress() {
        return this.progress;
    }

    private Point constructionPosition = null;

    public void setConstructionPosition(Point p) {
        this.constructionPosition = p;
    }

    public Point getConstructionPosition() {
        return this.constructionPosition;
    }

    public Cursor() {
        this.mode = CursorMode.DRAWING;
    }

    @Override
    public void setPosition(Point pos) {
        super.setPosition(pos);
    }

    public void setMode(CursorMode mode) {
        this.mode = mode;
    }

    public CursorMode getMode() {
        return mode;
    }

    /**
     * Starts a brief, high-contrast locate flash at the cursor. {@code now} is a wall-clock instant
     * in milliseconds supplied by the caller (renderer or presenter) so tests stay deterministic.
     */
    public void ping(long now) {
        this.pingUntil = now + PING_DURATION_MS;
    }

    /** Whether the locate flash started with {@link #ping(long)} is still on at {@code now}. */
    public boolean isPinging(long now) {
        return now < pingUntil;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitCursor(this);
    }

    @Override
    public void toggleReversed() {
        setReversed(!isReversed());
    }

    @Override
    public void destroy() {}

    @Override
    public boolean isDestroying() {
        return false;
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }
}
