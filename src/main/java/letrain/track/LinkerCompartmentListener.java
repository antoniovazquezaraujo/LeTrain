package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public interface LinkerCompartmentListener<T extends Track> {
    boolean canEnter(Dir d, Linker<T> v);

    boolean canExit(Dir d);

}
