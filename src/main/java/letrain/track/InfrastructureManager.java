package letrain.track;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import letrain.track.rail.ForkRailTrack;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InfrastructureManager {

    @JsonProperty("forks")
    private List<ForkRailTrack> forks = new ArrayList<>();

    @JsonProperty("sensors")
    private List<Sensor> sensors = new ArrayList<>();

    @JsonProperty("semaphores")
    private List<RailSemaphore> semaphores = new ArrayList<>();

    @JsonProperty("stations")
    private List<Station> stations = new ArrayList<>();

    @JsonIgnore
    private ForkRailTrack selectedFork;

    @JsonProperty("selectedForkIndex")
    private int selectedForkIndex = -1;

    @JsonIgnore
    private RailSemaphore selectedSemaphore;

    @JsonProperty("selectedSemaphoreIndex")
    private int selectedSemaphoreIndex = -1;

    @JsonIgnore
    private Station selectedStation;

    @JsonProperty("selectedStationIndex")
    private int selectedStationIndex = -1;

    public InfrastructureManager() {
        // Constructor for Jackson
    }

    public void postLoadInit() {
        if (selectedForkIndex >= 0 && selectedForkIndex < forks.size()) {
            selectedFork = forks.get(selectedForkIndex);
        }
        if (selectedSemaphoreIndex >= 0 && selectedSemaphoreIndex < semaphores.size()) {
            selectedSemaphore = semaphores.get(selectedSemaphoreIndex);
        }
        if (selectedStationIndex >= 0 && selectedStationIndex < stations.size()) {
            selectedStation = stations.get(selectedStationIndex);
        }
    }

    public List<ForkRailTrack> getForks() {
        return forks;
    }

    public void setForks(List<ForkRailTrack> forks) {
        this.forks = forks;
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<Sensor> sensors) {
        this.sensors = sensors;
    }

    public List<RailSemaphore> getSemaphores() {
        return semaphores;
    }

    public void setSemaphores(List<RailSemaphore> semaphores) {
        this.semaphores = semaphores;
    }

    public List<Station> getStations() {
        return stations;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    @JsonIgnore
    public ForkRailTrack getSelectedFork() {
        return selectedFork;
    }

    @JsonIgnore
    public void setSelectedFork(ForkRailTrack selectedFork) {
        this.selectedFork = selectedFork;
        this.selectedForkIndex = forks.indexOf(selectedFork);
    }

    @JsonIgnore
    public RailSemaphore getSelectedSemaphore() {
        return selectedSemaphore;
    }

    @JsonIgnore
    public void setSelectedSemaphore(RailSemaphore selectedSemaphore) {
        this.selectedSemaphore = selectedSemaphore;
        this.selectedSemaphoreIndex = semaphores.indexOf(selectedSemaphore);
    }

    @JsonIgnore
    public Station getSelectedStation() {
        return selectedStation;
    }

    @JsonIgnore
    public void setSelectedStation(Station selectedStation) {
        this.selectedStation = selectedStation;
        this.selectedStationIndex = stations.indexOf(selectedStation);
    }

    private <T> int getNextIndex(List<T> list, int currentIndex) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        int newIndex = currentIndex + 1;
        if (newIndex >= list.size()) {
            newIndex = 0;
        }
        return newIndex;
    }

    private <T> int getPrevIndex(List<T> list, int currentIndex) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        int newIndex = currentIndex - 1;
        if (newIndex < 0) {
            newIndex = list.size() - 1;
        }
        return newIndex;
    }

    public boolean selectNextFork() {
        int newIndex = getNextIndex(forks, selectedForkIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedForkIndex = newIndex;
        selectedFork = forks.get(selectedForkIndex);
        return true;
    }

    public boolean selectPrevFork() {
        int newIndex = getPrevIndex(forks, selectedForkIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedForkIndex = newIndex;
        selectedFork = forks.get(selectedForkIndex);
        return true;
    }

    public boolean selectNextSemaphore() {
        int newIndex = getNextIndex(semaphores, selectedSemaphoreIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedSemaphoreIndex = newIndex;
        selectedSemaphore = semaphores.get(selectedSemaphoreIndex);
        return true;
    }

    public boolean selectPrevSemaphore() {
        int newIndex = getPrevIndex(semaphores, selectedSemaphoreIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedSemaphoreIndex = newIndex;
        selectedSemaphore = semaphores.get(selectedSemaphoreIndex);
        return true;
    }

    public boolean selectNextStation() {
        int newIndex = getNextIndex(stations, selectedStationIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedStationIndex = newIndex;
        selectedStation = stations.get(selectedStationIndex);
        return true;
    }

    public boolean selectPrevStation() {
        int newIndex = getPrevIndex(stations, selectedStationIndex);
        if (newIndex == -1) {
            return false;
        }
        selectedStationIndex = newIndex;
        selectedStation = stations.get(selectedStationIndex);
        return true;
    }

    public void addFork(ForkRailTrack f) {
        forks.add(f);
    }

    public void removeFork(ForkRailTrack f) {
        forks.remove(f);
        if (selectedFork == f) {
            selectedFork = null;
            selectedForkIndex = -1;
        }
    }

    public void addSensor(Sensor s) {
        sensors.add(s);
    }

    public void removeSensor(Sensor s) {
        sensors.remove(s);
    }

    public void addSemaphore(RailSemaphore s) {
        semaphores.add(s);
    }

    public void removeSemaphore(RailSemaphore s) {
        semaphores.remove(s);
        if (selectedSemaphore == s) {
            selectedSemaphore = null;
            selectedSemaphoreIndex = -1;
        }
    }

    public void addStation(Station s) {
        stations.add(s);
    }

    public void removeStation(Station s) {
        stations.remove(s);
        if (selectedStation == s) {
            selectedStation = null;
            selectedStationIndex = -1;
        }
    }
}