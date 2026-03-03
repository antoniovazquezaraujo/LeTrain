package letrain.track;

import java.io.Serializable;

import letrain.vehicle.impl.rail.Train;

public interface ForkEventListener extends Serializable {
    default public void onEnterTrain(Train train, boolean isForward) {
    }

    default public void onExitTrain(Train train, boolean isForward) {
    }

    default public void onDirectionChanged(boolean normal) {
    }
}
