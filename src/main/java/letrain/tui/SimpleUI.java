package letrain.tui;

import javafx.scene.paint.Color;
import letrain.map.Point;
import letrain.view.Renderer;

public interface SimpleUI {
    Point getPos();
    void setPos(Point pos);
    void paint();
    public void clear();
    public void set(int x, int y, String c);

    void setColor(int x, int y, Color color);

    public void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);
}
