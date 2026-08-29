package letrain.vehicle;

import letrain.map.Point;
import letrain.visitor.Visitor;

public class Cursor extends Vehicle {
    public enum CursorMode {
        DRAWING, ERASING, MOVING, MAKING_TRACKS
    }

    private CursorMode mode;
    private float progress = 0f;
    public void setProgress(float p) { this.progress = p; }
    public float getProgress() { return this.progress; }

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
