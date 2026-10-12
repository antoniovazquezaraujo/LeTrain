package letrain.mvp;

import letrain.mvp.input.InputEvent;
import java.io.File;
import letrain.map.Point;

public interface GameViewListener {
    void onGameModeSelected(Model.GameMode mode);

    void onNewGame();

    /**
     * Opens the in-game Settings dialog (S3b, 2D): File → Settings. Clients without it (3D until
     * S4) keep the no-op default.
     */
    default void onSettings() {}

    /**
     * True while a game is running (started from New or loaded). The File menu disables {@code
     * Save} at the boot menu until then (D8); clients without a boot menu keep the default.
     */
    default boolean hasActiveGame() {
        return true;
    }

    void onSaveGame(File file);

    void onLoadGame(File file);

    default void onExportScenario(File file) {}

    default void onImportScenario(File file) {}

    /**
     * Generates the current scenario text (seed + on build + on start + program) for the editor.
     */
    default String getScenarioText() {
        return "";
    }

    /** Generates the scenario's {@code configuration { ... }} section (the effective settings). */
    default String getConfigurationText() {
        return "";
    }

    /** Exports the (possibly hand-edited) scenario text to {@code file}. */
    default void onExportScenarioText(File file, String text) {}

    /** Plays a scenario from its text (used by the editor's Play button). */
    default void onPlayScenarioText(String text) {}

    /** Whether there is an edit journal to export (used to enable/disable the Export button). */
    default boolean canExportScenario() {
        return true;
    }

    /** Whether the command journal is currently recording edits (for the REC indicator). */
    default boolean isRecordingCommands() {
        return false;
    }

    /** Compact game clock for the map corner, e.g. {@code D1 08:00} (ADR-022). */
    default String getGameTimeText() {
        return "";
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
