package letrain.ground;

import java.util.function.Consumer;

import letrain.map.Point;
import letrain.visitor.Renderable;

public interface GroundMap extends Renderable {
    public static final int GROUND = 0;
    public static final int WATER = 1;
    public static final int ROCK = 2;

    void renderBlock(int startx, int starty, int width, int height);

    Integer getValueAt(int x, int y);

    Integer getValueAt(Point pos);

    void setValueAt(int x, int y, Integer value);

    void setValueAt(Point pos, Integer value);

    Integer removeValueAt(int x, int y);

    Integer removeValueAt(Point pos);

    public void forEach(Consumer<Ground> c);

}
