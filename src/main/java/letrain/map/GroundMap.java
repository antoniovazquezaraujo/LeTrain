package letrain.map;

import letrain.visitor.Renderable;

public interface GroundMap extends Renderable {
    public void update(int startX, int startY, int width, int height);

    int getWidth();

    int getHeight();

    int get(int x, int y);

    int get(Point pos);

    void set(int x, int y, int value);

    void set(Point pos, int value);

}
