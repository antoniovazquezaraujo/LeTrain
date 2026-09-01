package letrain.track;

import java.io.Serializable;
import letrain.vehicle.rail.impl.Train;

public interface SensorEventListener extends Serializable {

    public default void onExitTrain(Train train, boolean isForward) {};

    public default void onEnterTrain(Train train, boolean isForward) {};
}
