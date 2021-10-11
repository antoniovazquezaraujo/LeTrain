package letrain.map


import letrain.mvp.impl.CompactPresenter
import letrain.physics.Vector2D
import letrain.track.Track
import letrain.track.rail.*
import letrain.vehicle.impl.Cursor
import org.junit.After
import org.junit.Before

trait TestCircuit1Trait {
    RailMap railMap
    Cursor cursor
    RailTrack oldTrack
    Dir oldDir
    boolean reversed
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK

    @Before
    def setupTestRailMap() {
        railMap = new RailMap()
        cursor = new Cursor()

        cursor.setDir(Dir.E)
        cursor.setPosition2D(new Vector2D(0, 0))
        for (int n = 0; n < 20; n++) {
            makeTrack()
        }
        oldTrack = railMap.getTrackAt(3, 0)
        cursor.setPosition2D(new Vector2D(4, 0))
        cursor.setDir(Dir.E)
        cursor.rotateLeft()
        makeTrack()
        makeTrack()
        makeTrack()
        cursor.rotateRight()
        makeTrack()
        makeTrack()
        cursor.rotateRight()
        makeTrack()
        makeTrack()
        cursor.rotateLeft()
        makeTrack()
    }

    @After
    def cleanupTestRailMap() {
        railMap = null
        cursor = null
        oldTrack = null
        oldDir = null
        reversed = false
        newTrackType = CompactPresenter.TrackType.NORMAL_TRACK
    }

    private boolean makeTrack() {
        Vector2D cursorPosition = cursor.getPosition2D()
        Dir dir = cursor.getDir()
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition2D())
        } else {
            if (!reversed) {
                oldDir = cursor.getDir().inverse()
            } else {
                oldDir = cursor.getDir()
            }
        }

        RailTrack track = railMap.getTrackAt(cursorPosition.getX(), cursorPosition.getY())
        if (track == null) {
            track = createTrackOfSelectedType()
        } else {
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false
            }
        }
        track.addRoute(oldDir, dir)
        track.setPosition2D(cursorPosition)
        if (oldTrack != null) {
            track.connectTrack(oldDir, oldTrack)
            oldTrack.connectTrack(track.getDirWhenEnteringFrom(dir).inverse(), track)
        }
        railMap.addTrack(cursorPosition, track)
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack()
//            Modmodel.addFork(myNewTrack)
            final Router router = track.getRouter()
            router.forEach({ t ->
                myNewTrack.getRouter().addRoute(t.getKey(), t.getValue())
            })
            myNewTrack.setNormalRoute()
            railMap.removeTrack(track.getPosition2D().getX(), track.getPosition2D().getY())
            railMap.addTrack(cursor.getPosition2D(), myNewTrack)
            for (Dir d : Dir.values()) {
                if (track.getConnectedTrack(d) != null) {
                    myNewTrack.connectTrack(d, track.getConnectedTrack(d))
                }
            }
            myNewTrack.setAlternativeRoute()
        }

        Vector2D newPos = new Vector2D(cursorPosition)
        if (!reversed) {
            newPos.move(cursor.getDir(), 1)
        } else {
            newPos.move(cursor.getDir().inverse())
        }
        cursor.setPosition2D(newPos)
        oldTrack = track
        return true
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case CompactPresenter.TrackType.STOP_TRACK:
                return new StopRailTrack();
            case CompactPresenter.TrackType.TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case CompactPresenter.TrackType.TUNNEL_GATE:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach({ t -> r.addRoute(t.getKey(), t.getValue()) })
        r.addRoute(from, to)
        return r.getNumRoutes() == 3
    }


}