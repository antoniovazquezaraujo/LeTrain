package letrain.mvp;

import javafx.scene.input.KeyEvent;

public interface GameViewListener {
    void onGameModeSelected(Model.GameMode mode);

//    void onUp();
//
//    void onDown();
//
//    void onLeft();
//
//    void onRight();

    void onChar(KeyEvent c);

}
