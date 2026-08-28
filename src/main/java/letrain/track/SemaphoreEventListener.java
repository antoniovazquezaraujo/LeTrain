package letrain.track;

import java.io.Serializable;
import letrain.vehicle.rail.impl.Train;

public interface SemaphoreEventListener extends Serializable {
    public default void onOpen() {}

    public default void onClosed() {}

    public default void onEnterTrain(Train train, boolean isForward) {}

    public default void onExitTrain(Train train, boolean isForward) {}
}
