package letrain.vehicle.impl.road;

import letrain.track.road.RoadTrack;
import letrain.vehicle.impl.Linker;

public class Caravan extends Linker<RoadTrack> {
    @Override
    public float getFrictionCoefficient() {
        return 0;
    }
}
