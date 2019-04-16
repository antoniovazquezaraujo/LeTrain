package letrain.vehicle;

import letrain.map.*;
import letrain.track.Track;

public abstract class Vehicle<T extends Track>
        implements
        Rotable,
        Reversible,
        Selectable,
        Mapeable,
        Transportable {
    protected Point pos = new Point(0, 0);


    protected Dir dir = Dir.N;
    protected boolean selected = false;
    protected boolean reversed = false;
    private int acceleration = 0;


    public Vehicle() {
    }

    /***********************************************************
     * Transportable implementation
     **********************************************************/
    @Override
    public abstract boolean advance();

    @Override
    public float getMass() {
        return 10000;
    }

    /***********************************************************
     * Mapeable implementation
     **********************************************************/
    public Point getPosition() {
        return pos;
    }

    public void setPosition(Point pos) {
        this.pos = pos;
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
    public boolean reverse() {
        boolean ret = reversed;
        reversed = !reversed;
        return ret;
    }

    @Override
    public boolean isReversed() {
        return reversed;
    }


}
