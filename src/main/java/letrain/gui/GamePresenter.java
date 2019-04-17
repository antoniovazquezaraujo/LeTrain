package letrain.gui;

import letrain.sim.GameModel;

public interface GamePresenter {
    public void onGameModeSelected(GameModel.Mode mode);

    public void onMakerAdvance();

    public void onMakerInverse();

    public void onMakerTurnLeft();

    public void onMakerTurnRight();

    public void onMakerCreateTunnel();

    public void onMakerCreateFactory();

    public void onFactoryCreateTrain(String trainName);

    public void onFactoryCreateLocomotive();

    public void onFactoryCreateWagon();

    public void onSelectTrain(String trainName);

    public void onIncTrainAcceleration();

    public void onDecTrainAcceleration();

    public void onSelectFork(String forkName);

    public void onChangeForkDirection();

    void paintLoop();
}
