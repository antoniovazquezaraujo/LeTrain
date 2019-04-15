package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public interface LinkerCompartmentListener {
    boolean canEnter(Dir d, Linker v);

    boolean canExit(Dir d);

}
