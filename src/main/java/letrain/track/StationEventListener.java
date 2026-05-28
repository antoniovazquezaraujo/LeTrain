package letrain.track;

import java.io.Serializable;

import letrain.vehicle.rail.impl.Train;

public interface StationEventListener extends Serializable {
    default public void onEnterTrain(Train train, boolean isForward) {
    }

    default public void onExitTrain(Train train, boolean isForward) {
    }

    default public void onLoad(Train train) {
    }

    default public void onUnload(Train train) {
    }

    default public void onStartLoad(Train train) {
    }

    default public void onEndLoad(Train train) {
    }

    default public void onStartUnload(Train train) {
    }

    default public void onEndUnload(Train train) {
    }

    default public void onLink(Train train) {
    }

    default public void onUnlink(Train train) {
    }
}
