package letrain.vehicle.impl.rail;

import letrain.track.Station;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Wagon;

import java.util.List;

/**
 * Centraliza la lógica de carga, descarga y acciones industriales.
 * Extraído para adherirse al principio de Responsabilidad Única.
 */
public class TrainLogisticsManager {

    private final List<Linker> linkers;

    public TrainLogisticsManager(List<Linker> linkers) {
        this.linkers = linkers;
    }

    public void performIndustrialAction(Station station) {
        if (station == null) {
            return;
        }
        for (Linker linker : linkers) {
            if (linker instanceof Wagon wagon) {
                station.transferCargo(wagon);
            }
        }
    }

    public double getTotalCargoWeight() {
        return linkers.stream()
                .filter(Wagon.class::isInstance)
                .mapToDouble(l -> ((Wagon) l).getWeight())
                .sum();
    }
}
