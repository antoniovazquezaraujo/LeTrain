package letrain.track;

import letrain.vehicle.impl.rail.Train;

public interface SensorEventListener {

    public void onExitTrain(Train train);

    public void onEnterTrain(Train train);

}
