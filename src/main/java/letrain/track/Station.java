package letrain.track;

import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Visitor;

public class Station extends Sensor {

    public Station(int id) {
        super(id);
    }

    public void onEnterTrain(Train train) {
        super.onEnterTrain(train);
        train.setStationId(getId());
    }

    public void onExitTrain(Train train) {
        super.onEnterTrain(train);
        train.setStationId(0);
    }

    @Override
    public String toString() {
        return "Station [id=" + getId() + "]";
    }

    private int exportCargo = 100;
    private int importCargo = 0;
    private int maxCargo = 500;

    public int getExportCargoAmount() {
        return exportCargo;
    }

    public int getImportCargoAmount() {
        return importCargo;
    }

    // Legacy support for renderer, mapped to export for now?
    // Or renderer uses new methods. Let's redirect getCargoAmount to exportCargo
    // just in case.
    public int getCargoAmount() {
        return exportCargo;
    }

    public void regenerateCargo() {
        if (exportCargo < maxCargo) {
            exportCargo++;
        }
        // Import cargo is consumed (processed) by the station/city over time
        if (importCargo > 0) {
            if (Math.random() < 0.25) { // 25% chance per tick to consume (previously 10%)
                importCargo--;
            }
        }
    }

    public int takeExportCargo(int amount) {
        int taken = Math.min(amount, exportCargo);
        exportCargo -= taken;
        return taken;
    }

    public void receiveImportCargo(int amount) {
        importCargo += amount;
        if (importCargo > maxCargo) {
            importCargo = maxCargo;
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStation(this);
    }
}
