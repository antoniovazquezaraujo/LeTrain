package letrain.track.road;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.render.Renderer;

public class RoadTrack extends Track {

    public RoadTrack() {
        this.connections = new RoadTrack[Dir.NUM_DIRS];
    }


    @Override
    public void enterLinkerFromDir(Dir d, Linker vehicle) {
        getTrackDirector().enterLinkerFromDir(this, d, vehicle);
    }

    @Override
    public void accept(Renderer renderer) {

    }
}
