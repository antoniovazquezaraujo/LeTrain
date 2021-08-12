package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

interface LinkerCompartment<T extends Track> {
    void enter(  Linker t);

    Linker removeLinker();

    Linker getLinker();

    void setLinker(Linker linker);

    void addLinkerCompartmentListener(LinkerCompartmentListener listener);

    void removeLinkerCompartmentListener(LinkerCompartmentListener listener);
}
