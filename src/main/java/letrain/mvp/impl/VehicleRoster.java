package letrain.mvp.impl;

import java.util.ArrayList;
import java.util.List;

import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleRoster {
    
    @JsonProperty("locomotives")
    private List<Locomotive> locomotives = new ArrayList<>();
    
    @JsonProperty("wagons")
    private List<Wagon> wagons = new ArrayList<>();
    
    @JsonIgnore
    private Locomotive selectedLocomotive;

    @JsonIgnore
    private int selectedLocomotiveIndex = -1;

    public VehicleRoster() {
        // Constructor for Jackson
    }

    public List<Locomotive> getLocomotives() {
        return locomotives;
    }

    public void setLocomotives(List<Locomotive> locomotives) {
        this.locomotives = locomotives;
    }

    public List<Wagon> getWagons() {
        return wagons;
    }

    public void setWagons(List<Wagon> wagons) {
        this.wagons = wagons;
    }

    @JsonIgnore
    public Locomotive getSelectedLocomotive() {
        return selectedLocomotive;
    }

    @JsonIgnore
    public void setSelectedLocomotive(Locomotive locomotive) {
        this.selectedLocomotive = locomotive;
        this.selectedLocomotiveIndex = locomotives.indexOf(locomotive);
    }

    public boolean selectNextLocomotive() {
        if (getLocomotives().isEmpty()) {
            return false;
        }
        do {
            selectedLocomotiveIndex++;
            if (selectedLocomotiveIndex >= getLocomotives().size()) {
                selectedLocomotiveIndex = 0;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex < getLocomotives().size());
        return true;
    }

    public boolean selectPrevLocomotive() {
        if (getLocomotives().isEmpty()) {
            return false;
        }
        do {
            selectedLocomotiveIndex--;
            if (selectedLocomotiveIndex < 0) {
                selectedLocomotiveIndex = getLocomotives().size() - 1;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex >= 0);
        return true;
    }
    
    public void addLocomotive(Locomotive l) {
        locomotives.add(l);
    }

    public void removeLocomotive(Locomotive l) {
        locomotives.remove(l);
        if (selectedLocomotive == l) {
            selectedLocomotive = null;
            selectedLocomotiveIndex = -1;
        }
    }

    public void addWagon(Wagon w) {
        wagons.add(w);
    }

    public void removeWagon(Wagon w) {
        wagons.remove(w);
    }
}