package letrain.track.road;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;

public class RoadTrack extends Track<RoadTrack> {

    public RoadTrack() {
        this.connections = new RoadTrack[Dir.NUM_DIRS];
    }


    @Override
    public void enterLinker(Dir d, Linker<Track<RoadTrack>> vehicle) {
        getTrackDirector().enterLinker(this, d, vehicle);
    }
}
