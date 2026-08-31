package letrain.mvp;

import letrain.mvp.input.InputEvent;
import java.io.File;
import letrain.map.Point;

public interface GameViewListener {
    void onGameModeSelected(Model.GameMode mode);

    void onNewGame();

    void onSaveGame(File file);

    void onLoadGame(File file);

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
