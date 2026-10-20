package letrain.mvp;

import java.util.List;
import java.util.Map;
import letrain.map.Point;
import letrain.mvp.Model.GameModeMenuOption;

public interface View {
    Point getMapScrollPage();

    void setMapScrollPage(Point pos);

    void paint();

    void clear();

    void set(int x, int y, String c);

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

    default void setCameraPagination(boolean paginate) {}

    default int getCameraDeadzone() {
        return 0;
    }

    default void setCameraDeadzone(int margin) {}

    default void flashCameraDeadzone() {}

    void clear(int x, int y);

    void fill(int x, int y, int width, int height, String c);

    void box(int x, int y, int width, int height);

    void setStatusBarText(String info);

    void setInfoBarText(String info);

    void setMenu(List<GameModeMenuOption> options);

    void setHelpBarText(String info);

    default void setHelpLevel(int helpLevel) {}

    void showSaveDialog();

    void showLoadDialog();

    default void showExportDialog() {}

    default void showImportDialog() {}

    void showIDE();

    /** Opens the File menu (Esc in both clients): New/Load/Save/Settings/Quit. */
    void showFileMenu();

    /**
     * Opens the New Game dialog (2D today, 3D in S4) prefilled with the current effective
     * configuration.
     *
     * @return the validated settings chosen by the player, or {@code null} when the dialog was
     *         cancelled
     */
    default Map<String, String> showNewGameDialog(Map<String, String> currentSettings) {
        return null;
    }

    /**
     * Simple yes/no confirmation dialog; {@code false} when there is no dialog or the player says
     * no.
     */
    default boolean confirm(String title, String message) {
        return false;
    }

    public int getCols();

    public int getRows();

    void showMessage(String title, String message);

    default void drawCommandLine(String text, String error) {}

    /**
     * Command bar with its short feedback (D1 contextual channel): {@code notice} is the last typed
     * command's warning when it fits on one line. A long/multiline notice is not painted here; it
     * opens the scrollable message panel instead.
     */
    default void drawCommandLine(String text, String error, String notice) {
        drawCommandLine(text, error);
    }
}
