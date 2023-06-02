package letrain.mvp;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;

import letrain.map.Point;

public interface View {
    Point getMapScrollPage();

    void setMapScrollPage(Point pos);

    void paint();

    void clear();

    void set(int x, int y, String c);

    void setFgColor(TextColor color);

    void setBgColor(TextColor color);

    void setPageOfPos(int x, int y);

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setMenu(String[] options, int selectedOption);

    void setHelpBarText(String info);

    boolean isEndOfGame(KeyStroke stroke);

    public KeyStroke readKey();

    public void setScreen(Screen screen);

    TextColor getFgColor();

    void showMainDialog();
}
