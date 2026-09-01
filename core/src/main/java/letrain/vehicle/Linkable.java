package letrain.vehicle;

import letrain.vehicle.rail.impl.Train;

public interface Linkable {
    Train getTrain();

    void setTrain(Train train);
}
