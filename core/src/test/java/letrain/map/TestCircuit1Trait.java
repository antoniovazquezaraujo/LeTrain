package letrain.map;

import letrain.map.impl.RailMap;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Presenter;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.Cursor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class TestCircuit1TraitTest {

    private RailMap railMap;
    private Cursor cursor;
    private RailTrack oldTrack;
    private Dir oldDir;
    private boolean reversed;
    private Presenter.TrackType newTrackType = Presenter.TrackType.NORMAL_TRACK;

    @BeforeEach
    void setupTestRailMap() {
        railMap = new RailMap();
        cursor = new Cursor();

        cursor.setDir(Dir.E);
        cursor.setPosition(new Point(0, 0));
        for (int n = 0; n < 20; n++) {
            makeTrack();
        }
        oldTrack = railMap.getTrackAt(3, 0);
        cursor.setPosition(new Point(4, 0));
        cursor.setDir(Dir.E);
        cursor.rotateLeft();
        makeTrack();
        makeTrack();
        makeTrack();
        cursor.rotateRight();
        makeTrack();
        makeTrack();
        cursor.rotateRight();
        makeTrack();
        makeTrack();
        cursor.rotateLeft();
        makeTrack();
    }

    @AfterEach
    void cleanupTestRailMap() {
        railMap = null;
        cursor = null;
        oldTrack = null;
        oldDir = null;
        reversed = false;
        newTrackType = Presenter.TrackType.NORMAL_TRACK;
    }

    private boolean makeTrack() {
        Point cursorPosition = cursor.getPosition();
        Dir cursorDir = cursor.getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = cursor.getDir().inverse();
            } else {
                oldDir = cursor.getDir();
            }
        }

        RailTrack track = railMap.getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            track = createTrackOfSelectedType();
        } else {
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        track.addRoute(oldDir, cursorDir);

        oldTrack.connect(track.getDir(cursorDir).inverse(), track);

        if (canBeAFork(track, oldDir, cursorDir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack(1);
            // Modmodel.addFork(myNewTrack)
            final Router router = track.getRouter();
            router.forEach(t -> myNewTrack.getRouter().addRoute(t.getKey(), t.getValue()));
            myNewTrack.setNormalRoute();
            railMap.removeTrack(track.getPosition());
            railMap.addTrack(cursor.getPosition(), myNewTrack);
            for (Dir dir : Dir.values()) {
                if (track.getConnected(dir) != null) {
                    myNewTrack.connect(dir, track.getConnected(dir));
                }
            }
            myNewTrack.setAlternativeRoute();
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(cursor.getDir(), 1);
        } else {
            newPos.move(cursor.getDir().inverse());
        }
        cursor.setPosition(newPos);
        oldTrack = track;
        return true;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case TUNNEL_GATE_TRACK:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
}
