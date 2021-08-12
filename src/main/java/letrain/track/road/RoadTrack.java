package letrain.track.road;

import letrain.map.Dir;
import letrain.map.Router;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Visitor;

public class RoadTrack extends Track {

    public RoadTrack() {
        this.connections = null;// new RoadTrack[Dir.NUM_DIRS];
    }

    @Override
    public Router getRouter() {
        return null;
    }


    @Override
    public void enter(  Linker vehicle) {
        getTrackDirector().enterLinkerFromDir(this, vehicle.getDir(), vehicle);
    }

    @Override
    public void accept(Visitor visitor) {

    }
}
