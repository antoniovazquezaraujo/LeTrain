package letrain.vehicle.impl.rail;

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

    default public void onEnterTrain(Train train, boolean isForward) {
    }

    default public void onExitTrain(Train train, boolean isForward) {
    }

    default public void onSegmentOccupied(Train train, letrain.core.segments.Segment segment) {
    }
}
