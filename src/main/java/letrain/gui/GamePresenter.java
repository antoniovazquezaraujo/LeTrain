package letrain.gui;

import letrain.sim.GameModel;

interface GamePresenter {
    void onGameModeSelected(GameModel.Mode mode);

    void onMakerAdvance();

    void onMakerInverse();

    void onMakerTurnLeft();

    void onMakerTurnRight();

    void onMakerCreateTunnel();

    void onMakerCreateFactory();

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
