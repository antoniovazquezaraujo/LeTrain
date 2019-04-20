package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.track.Track;
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
        }
    }

    @Override
    public void onUp() {
        RailTrack newTrack = makeTrack();
        if (newTrack != null) {
            model.getCursor().setPosition(newTrack.getPosition());
        }
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    private RailTrack makeTrack() {
        // Si no hay un track aquí, creamos uno
        Point cursorPosition = model.getCursor().getPosition();
        RailTrack actualTrack = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (actualTrack == null) {
            actualTrack = createTrackOfSelectedType();
            actualTrack.setPosition(cursorPosition);
            model.getRailMap().addTrack(cursorPosition, actualTrack);
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

        //Calculamos la posición del nuevo track
        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }

        //Creamos o localizamos el nuevo track
        RailTrack newTrack = model.getRailMap().getTrackAt(newPos);
        if (newTrack == null) {
            newTrack = createTrackOfSelectedType();
        }else{
            //TODO
            // aquí tenemos que detenernos!!!!
        }

        newTrack.setPosition(newPos);
        model.getRailMap().addTrack(newPos, newTrack);

        //Al track actual le agregamos la ruta entre de dónde veníamos y el nuevo
        Dir dirToNewTrack = cursorPosition.locate(newPos);
        actualTrack.addRoute(oldDir, dirToNewTrack);
        actualTrack.connect(dirToNewTrack, newTrack);

        Dir dirToActual = newTrack.getPosition().locate(actualTrack.getPosition());
        newTrack.connect(dirToActual, actualTrack);

        oldTrack = actualTrack;
        return newTrack;
    }
    public RailTrack createTrackOfSelectedType() {
        return new TrainFactoryRailTrack();
    }

}
