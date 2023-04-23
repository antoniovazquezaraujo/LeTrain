package letrain.track.rail;

import letrain.map.Dir;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.track.Track;
import letrain.track.TrackDirector;
import letrain.visitor.Visitor;

public class RailTrack extends Track {
    protected Router router;

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

    @Override
    public Router getRouter() {
        if (router == null) {
            router = new SimpleRouter();
        }
        return this.router;
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
    public void accept(Visitor visitor) {
        visitor.visitRailTrack(this);
    }

    @Override
    public String toString() {
        return super.toString() + " Dirs:(" + getRouter().toString() + ")";
    }
}
