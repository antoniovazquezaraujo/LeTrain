package letrain.gui;

import letrain.model.GameModel;

interface GamePresenter {
    void onGameModeSelected(GameModel.Mode mode);

    void onMakerAdvance();

    void onMakerInverse();

    void onMakerTurnLeft();

    void onMakerTurnRight();

    void onMakerCreateTunnel();

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
