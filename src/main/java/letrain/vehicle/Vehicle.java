package letrain.vehicle;

import letrain.map.*;
import letrain.track.Track;
import letrain.visitor.Renderable;

import java.io.Serializable;

public abstract class Vehicle<T extends Track>
        implements
        Serializable,
        Rotable,
        Reversible,
        Selectable,
        Mapeable,
        Transportable,
        Renderable {
    protected Point pos = new Point(0, 0);


    protected Dir dir = Dir.N;
    private boolean selected = false;
    private boolean reversed = false;


    protected Vehicle() {
    }

    /***********************************************************
     * Transportable implementation
     **********************************************************/
    @Override
    public boolean advance(){
        return true;
    };

    @Override
    public float getMass() {
        return 1000.0F;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.0F;
    }

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
    public void reverse(boolean reversed){
        this.reversed = reversed;
    }

    @Override
    public boolean isReversed() {
        return reversed;
    }

}
