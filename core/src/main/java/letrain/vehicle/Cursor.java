package letrain.vehicle;

import letrain.map.Point;
import letrain.visitor.Visitor;

public class Cursor extends Vehicle {
    public enum CursorMode {
        DRAWING, ERASING, MOVING, MAKING_TRACKS
    }

    /**
     * How long the one-shot locate ping stays on screen (#696): one smooth sweep, long enough to
     * spot the cursor in under a second and without any repeated flashing (accessibility).
     */
    public static final long PING_DURATION_MS = 900L;

    private CursorMode mode;
    private float progress = 0f;

    /**
     * Wall-clock start of the active locate ping, or {@link Long#MIN_VALUE} when idle. Any time
     * value: both renderers pass {@code System.currentTimeMillis()} and tests pass explicit clocks.
     */
    private long pingStartMillis = Long.MIN_VALUE;

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

    /** Starts (or restarts) the locate ping of the "Locate" action ('o'). */
    public void ping(long nowMillis) {
        this.pingStartMillis = nowMillis;
    }

    /**
     * True while a ping started with {@link #ping(long)} is still on screen. The elapsed check also
     * guards the idle {@link Long#MIN_VALUE} start and any clock that jumps backwards.
     */
    public boolean isPinging(long nowMillis) {
        long elapsed = nowMillis - pingStartMillis;
        return elapsed >= 0 && elapsed < PING_DURATION_MS;
    }

    /** Ping progress from 0 (just started) to 1 (expired), or -1 while idle. */
    public float pingProgress(long nowMillis) {
        if (!isPinging(nowMillis)) {
            return -1f;
        }
        return (nowMillis - pingStartMillis) / (float) PING_DURATION_MS;
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
