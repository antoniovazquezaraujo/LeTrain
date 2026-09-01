package letrain.vehicle.rail;

import java.io.Serializable;
import letrain.itinerary.Waypoint;
import letrain.vehicle.rail.impl.Train;

public interface TrainEventListener extends Serializable {
    public default void onSpeedChanged(int speed) {}

    public default void onSenseChanged(boolean forward) {}

    default void onLink(Train train) {}

    default void onUnlink(Train train) {}

    public default void onCrash(Train train, letrain.map.Point pos, int speed) {}

    public default void onContact(Train train, letrain.map.Point pos, int speed) {}

    public default void onSensorEnter(Train train, boolean isForward) {}

    public default void onSensorExit(Train train, boolean isForward) {}

    default void onWaypointReached(Train train, Waypoint waypoint) {}

    public default void onLoadingFinished(Train train) {}
}
