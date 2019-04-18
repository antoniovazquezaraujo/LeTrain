package letrain.mvp;

public interface GamePresenter {
    void onGameModeSelected(GameModel.Mode mode);

    void onMakerAdvance();

    void onMakerInverse();

    void onMakerTurnLeft();

    void onMakerTurnRight();

    void onMakerCreateTunnelTrack();

    void onMakerCreateStopTrack();

    void onMakerCreateFactoryGateTrack();

    void onFactoryCreateTrain(String trainName);

    void onFactoryCreateLocomotive();

    void onFactoryCreateWagon();

    void onSelectTrain(String trainName);

    void onIncTrainAcceleration();

    void onDecTrainAcceleration();

    void onSelectFork(String forkName);

    void onChangeForkDirection();

    void paintLoop();
}
