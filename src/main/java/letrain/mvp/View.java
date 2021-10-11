package letrain.mvp;

import javafx.scene.paint.Color;
import letrain.physics.Vector2D;

public interface View {
    Vector2D getMapScrollPage();

    void setMapScrollPage(Vector2D pos);

    void paint();

    void clear();

    void set(double x, double y, String c);

    void setColor(Color color);

    void setPageOfPos(double x, double y);

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setHelpBarText(String info);
}
