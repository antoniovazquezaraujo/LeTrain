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

    default Point getScrollOffset() {
        return getMapScrollPage();
    }

    default void setScrollOffset(Point pos) {
        setMapScrollPage(pos);
    }

    default void centerOn(int x, int y) {
        setPageOfPos(x, y);
    }


    default void ensureVisible(int x, int y, int radius, boolean paginate) {
        setPageOfPos(x, y);
    }

    default boolean isCameraPagination() {
        return false;
    }

    default void setCameraPagination(boolean paginate) {
    }

    default int getCameraDeadzone() {
        return 0;
    }

    default void setCameraDeadzone(int margin) {
    }

    default void flashCameraDeadzone() {
    }

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setMenu(List<GameModeMenuOption> options);

    void setHelpBarText(String info);

    void showSaveDialog();

    void showLoadDialog();

    void showIDE();

    void showExitDialog();

    public int getCols();

    public int getRows();

    void showMessage(String title, String message);

}
