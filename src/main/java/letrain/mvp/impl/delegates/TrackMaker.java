package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.track.ForkTrack;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;

public class TrackMaker extends GamePresenterDelegate {
    enum NewTrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    NewTrackType newTrackType;
    Track oldTrack;
    Dir oldDir;
    boolean reversed;
    private int degreesOfRotation = 0;
    private Dir dir;

    public TrackMaker(GameModel model, GameView view) {
        super(model, view);
        this.newTrackType = NewTrackType.NORMAL_TRACK;
        this.dir = Dir.N;
    }

    @Override
    public void onUp() {
        degreesOfRotation = 0;
        RailTrack newTrack = makeTrack();
//        if (newTrack != null) {
//            model.getCursor().setPosition(newTrack.getPosition());
//        }
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    @Override
    public void onDown() {
        super.onDown();
    }

    @Override
    public void onLeft() {
        if (degreesOfRotation <= 0) {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
            degreesOfRotation += 1;
        }
    }

    @Override
    public void onRight() {
        if (degreesOfRotation >= 0) {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
            degreesOfRotation -= 1;
        }
    }

    @Override
    public void onChar(String c) {
        super.onChar(c);
    }

    public void selectNewTrackType(NewTrackType type) {
        this.newTrackType = type;
    }

    public NewTrackType getNewTrackType() {
        return this.newTrackType;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STOP_TRACK:
                return new StopRailTrack();
            case TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case TUNNEL_GATE:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    private RailTrack makeTrack() {
        Point cursorPosition = model.getCursor().getPosition();
        Dir dir = model.getCursor().getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

        RailTrack track = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            track = createTrackOfSelectedType();
        }
        track.addRoute(oldDir, dir);
        if (oldTrack != null) {
            track.connect(oldDir, oldTrack);
        }
        track.setPosition(cursorPosition);
        getModel().getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final RailTrack myNewTrack = new ForkTrack();
            final Router router = track.getRouter();
            track.getRouter().forEach(t -> router.addRoute(t.getKey(), t.getValue()));
            getModel().getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            getModel().getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        oldTrack = track;
        return track;
    }

    private RailTrack makeTrack2() {
        // Si no hay un track aquí, creamos uno
        Point cursorPosition = model.getCursor().getPosition();
        RailTrack actualTrack = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (actualTrack == null) {
//            actualTrack = createTrackOfSelectedType();
//            actualTrack.setPosition(cursorPosition);
//            model.getRailMap().addTrack(cursorPosition, actualTrack);
        }
        //De dónde veníamos
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

//        actualTrack.addRoute(inverseRoute);


        //Creamos o localizamos el nuevo track
        RailTrack newTrack = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (newTrack == null) {
            newTrack = createTrackOfSelectedType();
        } else {
            //Había un track. Veamos si se necesita un fork:
            if (canBeAFork(newTrack, oldDir, model.getCursor().getDir())) {
                final RailTrack myNewTrack = new ForkTrack();
                final Router router = newTrack.getRouter();
                newTrack.getRouter().forEach(t -> router.addRoute(t.getKey(), t.getValue()));
                getModel().getRailMap().removeTrack(newTrack.getPosition().getX(), newTrack.getPosition().getY());
                getModel().getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
                myNewTrack.setPosition(model.getCursor().getPosition());
            }
        }
        //Calculamos la posición del nuevo track
        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }

        //Al track actual le agregamos la ruta entre de dónde veníamos y el nuevo
        Dir dirToNewTrack = cursorPosition.locate(newPos);
//        actualTrack.addRoute(oldDir, dirToNewTrack);
//        actualTrack.connect(dirToNewTrack, newTrack);

        Dir dirToActual = newTrack.getPosition().locate(actualTrack.getPosition());
        newTrack.connect(dirToActual, actualTrack);

        oldTrack = actualTrack;
        return newTrack;
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
}
