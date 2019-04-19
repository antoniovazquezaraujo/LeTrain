package letrain.mvp;

public interface GameViewListener {
    void onGameModeSelected(GameView.GameMode mode);

    void onUp();

    void onDown();

    void onLeft();

    void onRight();

    void onChar(char c);

}
