package letrain.track;

import java.io.Serializable;
import letrain.vehicle.rail.impl.Train;

public interface ForkEventListener extends Serializable {
    public default void onEnterTrain(Train train, boolean isForward) {}

    public default void onExitTrain(Train train, boolean isForward) {}

    public default void onDirectionChanged(boolean normal) {}
}
