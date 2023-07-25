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

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStation(this);
    }

}
