package letrain.mvp;

import javafx.scene.paint.Color;
import letrain.map.Point;

public interface View {
    Point getMapScrollPage();

    void setMapScrollPage(Point pos);

    void paint();

    void clear();

    void set(int x, int y, String c);

    void setColor(Color color);

    void setPageOfPos(int x, int y);

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setHelpBarText(String info);
}
