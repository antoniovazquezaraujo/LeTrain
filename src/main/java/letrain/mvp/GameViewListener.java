package letrain.mvp;

import java.io.File;

import com.googlecode.lanterna.input.KeyStroke;

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

    void onChar(KeyStroke c);

    String getProgram();

    void setProgram(String program);

}
