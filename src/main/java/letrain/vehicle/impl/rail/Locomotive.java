package letrain.vehicle.impl.rail;

import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker<Track<RailTrack>> implements Tractor{

    private float force;

    @Override
    public float getMass() {
        return super.getMass()+5000;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.2F;
    }

    @Override
    public float getForce() {
        return this.force;
    }

    @Override
    public void setForce(float force) {
        this.force = force;
    }
}
