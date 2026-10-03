package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.List;
import letrain.mvp.impl.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SpeedSignal;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;

/**
 * Handles navigation and selection state for entities in the game model (locomotives, forks,
 * stations, semaphores, speed signals, and sensors).
 */
@JsonIgnoreType
public final class ModelSelectionService {

    private ModelSelectionService() {}

    // ── Locomotive Selection ──────────────────────────────────────────

    public static boolean selectLocomotive(Model model, int id) {
        List<Locomotive> locos = model.getLocomotives();
        for (Locomotive loco : locos) {
            if (loco.getId() == id) {
                model.setSelectedLocomotive(loco);
                model.setSelectedLocomotiveIndex(locos.indexOf(loco));
                return true;
            }
        }
        return false;
    }

    public static boolean selectNextLocomotive(Model model) {
        List<Locomotive> locos = model.getLocomotives();
        if (locos.isEmpty()) {
            return false;
        }
        int index = model.getSelectedLocomotiveIndex();
        int count = 0;
        Locomotive selected = null;
        do {
            index++;
            if (index >= locos.size()) {
                index = 0;
            }
            selected = locos.get(index);
            count++;
        } while (!selected.isDirectorLinker() && count < locos.size());

        if (selected != null && selected.isDirectorLinker()) {
            model.setSelectedLocomotiveIndex(index);
            model.setSelectedLocomotive(selected);
            return true;
        }
        return false;
    }

    public static boolean selectPrevLocomotive(Model model) {
        List<Locomotive> locos = model.getLocomotives();
        if (locos.isEmpty()) {
            return false;
        }
        int index = model.getSelectedLocomotiveIndex();
        int count = 0;
        Locomotive selected = null;
        do {
            index--;
            if (index < 0) {
                index = locos.size() - 1;
            }
            selected = locos.get(index);
            count++;
        } while (!selected.isDirectorLinker() && count < locos.size());

        if (selected != null && selected.isDirectorLinker()) {
            model.setSelectedLocomotiveIndex(index);
            model.setSelectedLocomotive(selected);
            return true;
        }
        return false;
    }

    // ── Fork Selection ───────────────────────────────────────────────

    public static boolean selectFork(Model model, int id) {
        List<ForkRailTrack> forks = model.getForks();
        for (ForkRailTrack fork : forks) {
            if (fork.getId() == id) {
                model.setSelectedFork(fork);
                model.setSelectedForkIndex(forks.indexOf(fork));
                return true;
            }
        }
        return false;
    }

    public static boolean selectNextFork(Model model) {
        List<ForkRailTrack> forks = model.getForks();
        if (forks.isEmpty()) {
            return false;
        }
        int index = model.getSelectedForkIndex() + 1;
        if (index >= forks.size()) {
            index = 0;
        }
        model.setSelectedForkIndex(index);
        model.setSelectedFork(forks.get(index));
        return true;
    }

    public static boolean selectPrevFork(Model model) {
        List<ForkRailTrack> forks = model.getForks();
        if (forks.isEmpty()) {
            return false;
        }
        int index = model.getSelectedForkIndex() - 1;
        if (index < 0) {
            index = forks.size() - 1;
        }
        model.setSelectedForkIndex(index);
        model.setSelectedFork(forks.get(index));
        return true;
    }

    // ── Semaphore Selection ──────────────────────────────────────────

    public static boolean selectSemaphore(Model model, int id) {
        List<RailSemaphore> semaphores = model.getSemaphores();
        for (RailSemaphore semaphore : semaphores) {
            if (semaphore.getId() == id) {
                model.setSelectedSemaphore(semaphore);
                model.setSelectedSemaphoreIndex(semaphores.indexOf(semaphore));
                return true;
            }
        }
        return false;
    }

    public static boolean selectNextSemaphore(Model model) {
        List<RailSemaphore> semaphores = model.getSemaphores();
        if (semaphores.isEmpty()) {
            return false;
        }
        int index = model.getSelectedSemaphoreIndex() + 1;
        if (index >= semaphores.size()) {
            index = 0;
        }
        model.setSelectedSemaphoreIndex(index);
        model.setSelectedSemaphore(semaphores.get(index));
        return true;
    }

    public static boolean selectPrevSemaphore(Model model) {
        List<RailSemaphore> semaphores = model.getSemaphores();
        if (semaphores.isEmpty()) {
            return false;
        }
        int index = model.getSelectedSemaphoreIndex() - 1;
        if (index < 0) {
            index = semaphores.size() - 1;
        }
        model.setSelectedSemaphoreIndex(index);
        model.setSelectedSemaphore(semaphores.get(index));
        return true;
    }

    // ── Station Selection ────────────────────────────────────────────

    public static boolean selectStation(Model model, int id) {
        List<Station> stations = model.getStations();
        for (Station station : stations) {
            if (station.getId() == id) {
                model.setSelectedStation(station);
                model.setSelectedStationIndex(stations.indexOf(station));
                return true;
            }
        }
        return false;
    }

    public static boolean selectNextStation(Model model) {
        List<Station> stations = model.getStations();
        if (stations.isEmpty()) {
            return false;
        }
        int index = model.getSelectedStationIndex() + 1;
        if (index >= stations.size()) {
            index = 0;
        }
        model.setSelectedStationIndex(index);
        model.setSelectedStation(stations.get(index));
        return true;
    }

    public static boolean selectPrevStation(Model model) {
        List<Station> stations = model.getStations();
        if (stations.isEmpty()) {
            return false;
        }
        int index = model.getSelectedStationIndex() - 1;
        if (index < 0) {
            index = stations.size() - 1;
        }
        model.setSelectedStationIndex(index);
        model.setSelectedStation(stations.get(index));
        return true;
    }

    // ── SpeedSignal Selection ────────────────────────────────────────

    public static List<SpeedSignal> getSpeedSignals(Model model) {
        return model.getSensors().stream().filter(s -> s instanceof SpeedSignal)
                .map(s -> (SpeedSignal) s).toList();
    }

    public static boolean selectSpeedSignal(Model model, int id) {
        List<SpeedSignal> sigs = getSpeedSignals(model);
        for (int i = 0; i < sigs.size(); i++) {
            if (sigs.get(i).getId() == id) {
                model.setSelectedSpeedSignal(sigs.get(i));
                model.setSelectedSpeedSignalIndex(i);
                return true;
            }
        }
        return false;
    }

    public static boolean selectNextSpeedSignal(Model model) {
        List<SpeedSignal> sigs = getSpeedSignals(model);
        if (sigs.isEmpty()) {
            return false;
        }
        int index = model.getSelectedSpeedSignalIndex() + 1;
        if (index >= sigs.size()) {
            index = 0;
        }
        model.setSelectedSpeedSignalIndex(index);
        model.setSelectedSpeedSignal(sigs.get(index));
        return true;
    }

    public static boolean selectPrevSpeedSignal(Model model) {
        List<SpeedSignal> sigs = getSpeedSignals(model);
        if (sigs.isEmpty()) {
            return false;
        }
        int index = model.getSelectedSpeedSignalIndex() - 1;
        if (index < 0) {
            index = sigs.size() - 1;
        }
        model.setSelectedSpeedSignalIndex(index);
        model.setSelectedSpeedSignal(sigs.get(index));
        return true;
    }

    // ── Plain Sensor Selection ───────────────────────────────────────

    public static boolean selectSensor(Model model, int id) {
        Sensor s = model.getSensor(id);
        if (s != null && s.getClass() == Sensor.class) {
            model.setSelectedSensor(s);
            return true;
        }
        return false;
    }

    public static boolean selectNextSensor(Model model) {
        List<Sensor> pureSensors =
                model.getSensors().stream().filter(s -> s.getClass() == Sensor.class).toList();
        if (pureSensors.isEmpty()) {
            return false;
        }
        Sensor current = model.getSelectedSensor();
        if (current == null) {
            model.setSelectedSensor(pureSensors.get(0));
            return true;
        }
        int i = pureSensors.indexOf(current);
        if (i < pureSensors.size() - 1) {
            model.setSelectedSensor(pureSensors.get(i + 1));
        } else {
            model.setSelectedSensor(pureSensors.get(0));
        }
        return true;
    }

    public static boolean selectPrevSensor(Model model) {
        List<Sensor> pureSensors =
                model.getSensors().stream().filter(s -> s.getClass() == Sensor.class).toList();
        if (pureSensors.isEmpty()) {
            return false;
        }
        Sensor current = model.getSelectedSensor();
        if (current == null) {
            model.setSelectedSensor(pureSensors.get(pureSensors.size() - 1));
            return true;
        }
        int i = pureSensors.indexOf(current);
        if (i > 0) {
            model.setSelectedSensor(pureSensors.get(i - 1));
        } else {
            model.setSelectedSensor(pureSensors.get(pureSensors.size() - 1));
        }
        return true;
    }
}
