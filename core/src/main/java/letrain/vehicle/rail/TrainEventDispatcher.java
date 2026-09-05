package letrain.vehicle.rail;

import java.util.List;
import letrain.map.Point;

/** Manages event listener registrations and broadcasts train events. */
public interface TrainEventDispatcher {

    List<ScriptTrainEventListener> getScriptTrainListeners();

    List<CoreTrainEventListener> getCoreTrainListeners();

    void addScriptTrainEventListener(ScriptTrainEventListener listener);

    void removeScriptTrainEventListener(ScriptTrainEventListener listener);

    void addCoreTrainEventListener(CoreTrainEventListener listener);

    void removeCoreTrainEventListener(CoreTrainEventListener listener);

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

    void notifyWaypointReached(letrain.itinerary.Waypoint waypoint);

    void notifyLoadingFinished();
}
