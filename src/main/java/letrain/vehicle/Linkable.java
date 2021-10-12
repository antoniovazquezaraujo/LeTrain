package letrain.vehicle;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Train;

public interface Linkable {
    Train getTrain();
    void setTrain(Train train);
    Dir getUnlinkedDir();
    Linker drag();
}
