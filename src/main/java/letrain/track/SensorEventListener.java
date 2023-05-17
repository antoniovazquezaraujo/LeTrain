package letrain.track;

import letrain.vehicle.impl.rail.Train;

public interface SensorEventListener {

    default public void onExitTrain(Train train) {
    };

    default public void onEnterTrain(Train train) {
    };

}
