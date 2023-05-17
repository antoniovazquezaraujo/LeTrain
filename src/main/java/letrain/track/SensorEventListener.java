package letrain.track;

import java.io.Serializable;

import letrain.vehicle.impl.rail.Train;

public interface SensorEventListener extends Serializable {

    default public void onExitTrain(Train train) {
    };

    default public void onEnterTrain(Train train) {
    };

}
