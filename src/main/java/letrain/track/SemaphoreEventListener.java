package letrain.track;

import java.io.Serializable;

import letrain.vehicle.rail.impl.Train;

public interface SemaphoreEventListener extends Serializable {
    default public void onOpen() {
    }

    default public void onClosed() {
    }

    default public void onEnterTrain(Train train, boolean isForward) {
    }

    default public void onExitTrain(Train train, boolean isForward) {
    }
}
