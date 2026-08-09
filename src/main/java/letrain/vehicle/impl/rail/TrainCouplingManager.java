package letrain.vehicle.impl.rail;

import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.Tractor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gestiona el acoplamiento y la recuperación de tractores dentro de un tren.
 * Extraído para adherirse al principio de Responsabilidad Única.
 */
public class TrainCouplingManager {

    private final List<Linker> linkers;

    public TrainCouplingManager(List<Linker> linkers) {
        this.linkers = linkers;
    }

    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(Linker::isTractor)
                .map(Tractor.class::cast)
                .collect(Collectors.toList());
    }

    public boolean isMultiLinker() {
        return linkers.size() > 1;
    }
}
