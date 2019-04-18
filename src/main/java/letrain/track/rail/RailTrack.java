package letrain.track.rail;


import letrain.map.Dir;
import letrain.track.Track;
import letrain.track.TrackDirector;
import letrain.view.Renderer;

public class RailTrack extends Track  {
    public enum TrackType {
        STRAIGHT,
        CURVE,
        CROSS,
        FORK
    }

    public RailTrack() {
        this.connections = new RailTrack[Dir.NUM_DIRS];
        setTrackDirector(TrackDirector.getInstance());
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
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderRailTrack(this);
    }
}
