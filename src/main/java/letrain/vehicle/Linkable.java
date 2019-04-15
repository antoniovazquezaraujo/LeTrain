package letrain.vehicle;

import letrain.vehicle.impl.rail.Train;

public interface Linkable {
    Train getTrain();
    void setTrain(Train train);
}
