package letrain.track;

import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Visitor;

public class Platform extends Sensor {

    public Platform(int id) {
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
        return "Platform [id=" + getId() + "]";
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitPlatform(this);
    }

}
