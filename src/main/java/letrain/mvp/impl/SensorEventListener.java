package letrain.mvp.impl;

import letrain.vehicle.impl.rail.Train;

public interface SensorEventListener {

    public void onExitTrain(Train train);

    public void onEnterTrain(Train train);

}
