package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public interface LinkerCompartment<T extends Track> {
    void enterLinker(Dir d, Linker<Track<T>> t);

    Linker<Track<T>> exitLinker(Dir d);

    Linker<Track<T>> getLinker();

    void setLinker(Linker<Track<T>> linker);

    void addLinkerCompartmentListener(LinkerCompartmentListener<T> listener);

    void removeLinkerCompartmentListener(LinkerCompartmentListener<T> listener);
}
