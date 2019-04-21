package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TrainFactoryRailTrack;

public class FactoryMaker extends GamePresenterDelegate {
    TrackMaker.NewTrackType newTrackType;
    Track oldTrack;
    Dir oldDir;
    boolean reversed;
    private int degreesOfRotation= 0;
    private Dir dir;
    TrainFactory factory;

    public FactoryMaker(GameModel model, GameView view) {
        super(model, view);
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {
        if(mode.equals(GameView.GameMode.CREATE_FACTORY_PLATFORM_COMMAND)){
            factory = new TrainFactory();
            factory.setPosition(model.getCursor().getPosition());
            model.addTrainFactory(factory);
            Dir cursorInverseDir = model.getCursor().getDir().inverse();
            Point lastPosition = new Point(model.getCursor().getPosition());
            lastPosition.move(cursorInverseDir);
            oldTrack = model.getRailMap().getTrackAt(lastPosition);
        }
    }

    @Override
    public void onUp() {
        makeTrack();
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    private boolean makeTrack() {
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
        }else{
            System.out.println("No se deben hacer factorías sobre otras vías!!!");
            //no podemos avanzar, hay pistas aquí
        }
        track.addRoute(oldDir, dir);
        if (oldTrack != null) {
            track.connect(oldDir, oldTrack);
        }
        track.setPosition(cursorPosition);
        getModel().getRailMap().addTrack(cursorPosition, track);

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        oldTrack = track;
        return true;

    }
    public RailTrack createTrackOfSelectedType() {
        return new TrainFactoryRailTrack();
    }

}
