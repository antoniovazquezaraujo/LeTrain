package letrain.map;

import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class TestRailMapTrait {

    RailMap railMap = null;

    @BeforeEach
    void setupTestRailMap() {
        railMap = new RailMap();

        for (int n = 0; n < 5; n++) {
            railMap.addTrack(new Point(n, 0), new RailTrack());
            railMap.addTrack(new Point(n, 6), new RailTrack());
        }

        for (int n = 1; n < 4; n++) {
            RailTrack actualTrack = railMap.getTrackAt(n, 0);
            RailTrack prevTrack = railMap.getTrackAt(n - 1, 0);
            if (prevTrack != null) {
                actualTrack.connect(Dir.W, prevTrack);
                actualTrack.addRoute(Dir.E, Dir.W);
                prevTrack.connect(Dir.E, actualTrack);
                prevTrack.addRoute(Dir.W, Dir.E);
            }
            actualTrack = railMap.getTrackAt(n, 6);
            prevTrack = railMap.getTrackAt(n - 1, 6);
            if (prevTrack != null) {
                actualTrack.connect(Dir.W, prevTrack);
                actualTrack.addRoute(Dir.E, Dir.W);
                prevTrack.connect(Dir.E, actualTrack);
                prevTrack.addRoute(Dir.W, Dir.E);
            }
        }
        railMap.getTrackAt(0, 0).removeRoute(Dir.W, Dir.E);
        railMap.getTrackAt(0, 6).removeRoute(Dir.W, Dir.E);

        RailTrack track = null;
        RailTrack other = null;

        track = new RailTrack();
        railMap.addTrack(new Point(-1, 1), track);
        other = railMap.getTrackAt(0, 0);
        track.connect(Dir.NE, other);
        track.addRoute(Dir.SW, Dir.NE);
        other.connect(Dir.SW, track);
        other.addRoute(Dir.E, Dir.SW);

        track = new RailTrack();
        railMap.addTrack(new Point(5, 1), track);
        other = railMap.getTrackAt(4, 0);
        track.connect(Dir.NW, other);
        track.addRoute(Dir.SE, Dir.NW);
        other.connect(Dir.SE, track);
        other.addRoute(Dir.W, Dir.SE);

        track = new RailTrack();
        railMap.addTrack(new Point(-2, 2), track);
        other = railMap.getTrackAt(-1, 1);
        track.connect(Dir.NE, other);
        track.addRoute(Dir.S, Dir.NE);
        other.connect(Dir.SW, track);
        other.addRoute(Dir.NE, Dir.SW);

        track = new RailTrack();
        railMap.addTrack(new Point(6, 2), track);
        other = railMap.getTrackAt(5, 1);
        track.connect(Dir.NW, other);
        track.addRoute(Dir.S, Dir.NW);
        other.connect(Dir.SE, track);
        other.addRoute(Dir.NW, Dir.SE);

        railMap.addTrack(new Point(5, 5), track);
        other = railMap.getTrackAt(6, 4);
        track.connect(Dir.NE, other);
        track.addRoute(Dir.SW, Dir.NE);
        other.connect(Dir.SW, track);
        other.addRoute(Dir.N, Dir.SW);

        track = railMap.getTrackAt(0, 6);
        other = railMap.getTrackAt(-1, 5);
        track.connect(Dir.NW, other);
        track.addRoute(Dir.NW, Dir.E);
        other.connect(Dir.SE, track);
        other.addRoute(Dir.NW, Dir.SE);

        track = railMap.getTrackAt(4, 6);
        other = railMap.getTrackAt(5, 5);
        track.connect(Dir.NE, other);
        track.addRoute(Dir.W, Dir.NE);
        other.connect(Dir.SW, track);
        other.addRoute(Dir.NE, Dir.SW);

        track = railMap.getTrackAt(1, 6);
        other = railMap.getTrackAt(0, 6);
        track.connect(Dir.W, other);
        other.connect(Dir.E, track);
        track.addRoute(Dir.E, Dir.W);

        track = railMap.getTrackAt(3, 6);
        other = railMap.getTrackAt(4, 6);
        track.connect(Dir.E, other);
        other.connect(Dir.W, track);
        track.addRoute(Dir.E, Dir.W);

        track = railMap.getTrackAt(1, 0);
        other = railMap.getTrackAt(0, 0);
        track.connect(Dir.W, other);
        other.connect(Dir.E, track);
        track.addRoute(Dir.E, Dir.W);

        track = railMap.getTrackAt(3, 0);
        other = railMap.getTrackAt(4, 0);
        track.connect(Dir.E, other);
        other.connect(Dir.W, track);
        track.addRoute(Dir.E, Dir.W);

        track = railMap.getTrackAt(0, 0);
        track.addRoute(Dir.SW, Dir.E);

        track = railMap.getTrackAt(4, 0);
        track.addRoute(Dir.SE, Dir.W);

        track = railMap.getTrackAt(0, 6);
        track.addRoute(Dir.E, Dir.NW);

        track = railMap.getTrackAt(4, 6);
        track.addRoute(Dir.NE, Dir.W);
    }

    @AfterEach
    void cleanupTestRailMap() {
        railMap = null;
    }
}
