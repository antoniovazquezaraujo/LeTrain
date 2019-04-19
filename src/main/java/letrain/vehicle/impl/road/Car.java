package letrain.vehicle.impl.road;

import letrain.track.road.RoadTrack;
import letrain.vehicle.Vehicle;
import letrain.render.Renderer;

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
    public void accept(Renderer renderer) {

    }
}
