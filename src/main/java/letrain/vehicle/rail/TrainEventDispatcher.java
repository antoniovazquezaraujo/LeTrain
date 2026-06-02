package letrain.vehicle.rail;

import letrain.map.Point;

/**
 * Manages event listener registrations and broadcasts train events.
 */
public interface TrainEventDispatcher {

    java.util.List<TrainEventListener> getScriptTrainListeners();

    java.util.List<TrainEventListener> getCoreTrainListeners();

    void addScriptTrainEventListener(TrainEventListener listener);

    void removeScriptTrainEventListener(TrainEventListener listener);

    void addCoreTrainEventListener(TrainEventListener listener);

    void removeCoreTrainEventListener(TrainEventListener listener);

    void removeAllScriptTrainEventListeners();

    void postLoadInit();

    void notifySpeedChanged(int speed);

    void notifySenseChanged(boolean forward);

    void notifyLink();

    void notifyUnlink();

    void notifyEnterSensor(boolean isForward);

    void notifyExitSensor(boolean isForward);

    void notifyContact(Point pos, int speed);

    void notifyCrash(Point pos, int speed);
}
