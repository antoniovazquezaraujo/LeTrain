package letrain.vehicle.impl.rail;

import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;

public class Wagon extends Linker<Track<RailTrack>> {

    @Override
    public float getFrictionCoefficient() {
        return 0.3F;
    }
}
