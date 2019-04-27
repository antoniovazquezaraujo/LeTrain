package letrain.vehicle.impl.road;

import letrain.track.road.RoadTrack;
import letrain.vehicle.Vehicle;
import letrain.render.Visitor;

public class Car extends Vehicle<RoadTrack> {

    @Override
    public boolean advance() {
        return false;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0;
    }

    @Override
    public float getAcceleration() {
        return 0;
    }

    @Override
    public float getSpeed() {
        return 0;
    }

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }

    @Override
    public void accept(Visitor visitor) {

    }
}
