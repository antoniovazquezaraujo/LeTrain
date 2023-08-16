package letrain.vehicle;

import java.io.Serializable;

import letrain.map.Dir;
import letrain.map.Mapeable;
import letrain.map.Point;
import letrain.map.Reversible;
import letrain.map.Rotable;
import letrain.track.Track;
import letrain.visitor.Renderable;

public abstract class Vehicle<T extends Track>
        implements
        Serializable,
        Rotable,
        Reversible,
        Selectable,
        Mapeable,
        Transportable,
        Renderable,
        Destructible {
    protected Point pos = new Point(0, 0);
    protected Dir dir;
    private boolean selected = false;
    private boolean reversed = false;

    protected Vehicle() {
    }

    /***********************************************************
     * Transportable implementation
     **********************************************************/
    @Override
    public boolean advance() {
        return true;
    };

    /***********************************************************
     * Mapeable implementation
     **********************************************************/
    public Point getPosition() {
        return pos;
    }

    public void setPosition(Point pos) {
        this.pos.setX(pos.getX());
        this.pos.setY(pos.getY());
    }

    /***********************************************************
     * Selectable implementation
     **********************************************************/
    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /***********************************************************
     * Rotable implementation
     **********************************************************/
    @Override
    public void rotateLeft() {
        rotateLeft(1);
    }

    @Override
    public void rotateLeft(int angle) {
        dir = Dir.add(dir, angle);
    }

    @Override
    public void rotateRight() {
        rotateRight(1);
    }

    @Override
    public void rotateRight(int angle) {
        dir = Dir.add(dir, angle * -1);
    }

    @Override
    public void rotate(int angle) {
        dir = Dir.add(dir, angle);
    }

    @Override
    public Dir getDir() {
        return dir;
    }

    @Override
    public void setDir(Dir dir) {
        this.dir = dir;
    }

    /***********************************************************
     * Reversible implementation
     **********************************************************/

    @Override
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    @Override
    public boolean isReversed() {
        return reversed;
    }

    @Override
    public void toggleReversed() {
        setReversed(!isReversed());
    }

    @Override
    public Dir getRealDir() {
        return this.dir;
    }

}
