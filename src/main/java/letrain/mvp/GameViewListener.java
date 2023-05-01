package letrain.mvp;

import com.googlecode.lanterna.input.KeyStroke;

public interface GameViewListener {
    void onGameModeSelected(Model.GameMode mode);

    // void onUp();
    //
    // void onDown();
    //
    // void onLeft();
    //
    // void onRight();

    void onChar(KeyStroke c);

}
