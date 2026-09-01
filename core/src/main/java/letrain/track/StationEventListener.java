package letrain.track;

import java.io.Serializable;
import letrain.vehicle.rail.impl.Train;

public interface StationEventListener extends Serializable {
    public default void onEnterTrain(Train train, boolean isForward) {}

    public default void onExitTrain(Train train, boolean isForward) {}

    public default void onLoad(Train train) {}

    public default void onUnload(Train train) {}

    public default void onStartLoad(Train train) {}

    public default void onEndLoad(Train train) {}

    public default void onStartUnload(Train train) {}

    public default void onEndUnload(Train train) {}
}
