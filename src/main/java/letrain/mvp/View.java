package letrain.mvp;

import java.util.List;

import letrain.map.Point;
import letrain.mvp.Model.GameModeMenuOption;

public interface View {
    Point getMapScrollPage();

    void setMapScrollPage(Point pos);

    void paint();

    void clear();

    void set(int x, int y, String c);

    // Removed Lanterna specific color methods, will use abstract semantic styles or implementation specifics
    // void setFgColor(TextColor color);
    // void setBgColor(TextColor color);

    void setPageOfPos(int x, int y);

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setMenu(List<GameModeMenuOption> options);

    void setHelpBarText(String info);

    // Lanterna specific types removed
    // boolean isEndOfGame(KeyStroke stroke);
    // public KeyStroke readKey();
    // public void setScreen(Screen screen);
    // TextColor getFgColor();

    void showSaveDialog();

    void showLoadDialog();

    void showIDE();

    void showExitDialog();

    public int getCols();

    public int getRows();

    void showMessage(String title, String message);

    void showReferenceGuide();
}
