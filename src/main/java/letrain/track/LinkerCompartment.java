package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.rail.Linker;

interface LinkerCompartment<T extends Track> {
    boolean enterLinkerFromDir(Dir dir, Linker t);

    Linker removeLinker();

    Linker getLinker();

    void setLinker(Linker linker);

    void addLinkerCompartmentListener(LinkerCompartmentListener listener);

    void removeLinkerCompartmentListener(LinkerCompartmentListener listener);
}
