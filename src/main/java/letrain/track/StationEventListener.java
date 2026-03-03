package letrain.track;

import java.io.Serializable;

import letrain.vehicle.impl.rail.Train;

public interface StationEventListener extends Serializable {
    default public void onEnterTrain(Train train, boolean isForward) {
    }

    default public void onExitTrain(Train train, boolean isForward) {
    }

    default public void onLoad(Train train) {
    }

    default public void onUnload(Train train) {
    }
}
