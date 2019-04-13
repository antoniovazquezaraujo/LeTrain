package letrain.track.rail;


import letrain.map.Dir;
import letrain.track.Track;

public class RailTrack extends Track<RailTrack> {
    public enum TrackType {
        STRAIGHT,
        CURVE,
        CROSS,
        FORK
    }

    public RailTrack() {
        this.connections = new RailTrack[Dir.NUM_DIRS];
    }

    public TrackType getType() {
        if (getNumRoutes() == 2) {
            Dir d1 = getFirstOpenDir();
            Dir d2 = d1.inverse();
            Dir inverted = getDir(d2);
            if (inverted != null && inverted.equals(d1)) {
                if (d1.isStraight(d2)) {
                    return TrackType.STRAIGHT;
                }
            } else {
                return TrackType.CURVE;
            }
        }
        return TrackType.FORK;
    }
}
