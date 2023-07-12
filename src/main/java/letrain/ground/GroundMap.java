package letrain.ground;

import java.util.function.Consumer;

import letrain.map.Point;
import letrain.visitor.Renderable;

public interface GroundMap extends Renderable {
    void renderBlock(int startx, int starty, int width, int height);

    Integer getValueAt(int x, int y);

    Integer getValueAt(Point pos);

    void setValueAt(int x, int y, Integer value);

    void setValueAt(Point pos, Integer value);

    Integer removeValueAt(int x, int y);

    Integer removeValueAt(Point pos);

    public void forEach(Consumer<Ground> c);

}
