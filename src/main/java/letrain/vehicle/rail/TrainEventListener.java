package letrain.vehicle.rail;

import letrain.vehicle.rail.impl.Train;
import letrain.itinerary.Waypoint;

import java.io.Serializable;

public interface TrainEventListener extends Serializable {
    default public void onSpeedChanged(int speed) {
    }

    default public void onSenseChanged(boolean forward) {
    }

    default void onLink(Train train) {
    }

    default void onUnlink(Train train) {
    }

    default public void onCrash(Train train, letrain.map.Point pos, int speed) {
    }

    default public void onContact(Train train, letrain.map.Point pos, int speed) {
    }

    default public void onSensorEnter(Train train, boolean isForward) {
    }

    default public void onSensorExit(Train train, boolean isForward) {
    }

    default void onWaypointReached(Train train, Waypoint waypoint) {
    }

    default public void onLoadingFinished(Train train) {
    }
}
