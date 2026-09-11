package letrain.mvp;

import letrain.mvp.input.InputEvent;
import java.io.File;
import letrain.map.Point;

public interface GameViewListener {
    void onGameModeSelected(Model.GameMode mode);

    void onNewGame();

    void onSaveGame(File file);

    void onLoadGame(File file);

    default void onExportScenario(File file) {}

    default void onImportScenario(File file) {}

    /** Whether there is an edit journal to export (used to enable/disable the Export button). */
    default boolean canExportScenario() {
        return true;
    }

    /** Whether the command journal is currently recording edits (for the REC indicator). */
    default boolean isRecordingCommands() {
        return false;
    }

    void onSaveCommands(File file);

    void onLoadCommands(File file);

    void onEditCommands(String content);

    void onExitGame();

    void onPlay();

    void onChar(InputEvent c);

    void onKeyUp(InputEvent c);

    String getProgram();

    void setProgram(String program);

    void onMapPageChanged(Point mapScrollPage, int columns, int rows);

    void onScreenResized(int columns, int rows);

    String getGameObjectsReport();

    java.util.List<String> getEventLogEntries();
}
