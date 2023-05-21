package letrain.track;

import letrain.vehicle.impl.rail.Train;

public class PlatformSensor extends Sensor {

    public PlatformSensor(int id) {
        super(id);
    }

    public void onEnterTrain(Train train) {
        super.onEnterTrain(train);
        train.setPlatformId(getId());
    }

    public void onExitTrain(Train train) {
        super.onEnterTrain(train);
        train.setPlatformId(0);
    }

    @Override
    public String toString() {
        return "PlatformSensor [id=" + getId() + "]";
    }

}
