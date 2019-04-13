package letrain.track.road;

import letrain.map.Dir;
import letrain.track.Track;

public class RoadTrack extends Track<RoadTrack> {

    public RoadTrack() {
        this.connections = new RoadTrack[Dir.NUM_DIRS];
    }


}
