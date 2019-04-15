package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public interface LinkerCompartment<T extends Track> {
    void enterLinker(Dir d, Linker t);

    Linker exitLinker(Dir d);

    Linker getLinker();

    void setLinker(Linker linker);

    void addLinkerCompartmentListener(LinkerCompartmentListener listener);

    void removeLinkerCompartmentListener(LinkerCompartmentListener listener);
}
