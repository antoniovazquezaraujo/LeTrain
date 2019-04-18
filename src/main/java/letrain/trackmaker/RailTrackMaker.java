package letrain.trackmaker;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.TerrainMap;
import letrain.mvp.GameModel;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.view.Renderable;
import letrain.view.Renderer;

public class RailTrackMaker implements TrackMaker<RailTrack>, Renderable {

    private TerrainMap<RailTrack> map;
    private final Point position = new Point(0, 0);
    private Dir dir;
    private RailTrack oldTrack;
    private Dir oldDir;
    private boolean reversed;
    private GameModel.Mode mode;
    private int degreesOfRotation= 0;
    private NewTrackType newTrackType = NewTrackType.NORMAL_TRACK;

    @Override
    public void setMap(TerrainMap<RailTrack> map) {
        this.map = map;
    }

    @Override
    public void setPosition(int x, int y) {
        this.position.setX(x);
        this.position.setY(y);
    }

    @Override
    public Point getPosition() {
        return this.position;
    }

    @Override
    public void advance(int times) {
        for (int n = 0; n < times; n++) {
            advance();
        }
    }

    @Override
    public void advance() {
        degreesOfRotation=0;
        switch (mode) {
            case MAP_WALK:
                Point newPos = new Point(getPosition());
                if(!reversed) {
                    newPos.move(getDirection(), 1);
                }else{
                    newPos.move(getDirection().inverse());
                }
                setPosition(newPos.getX(), newPos.getY());
                break;
            case TRACK_WALK:
                break;
            case MAKE_TRACK:
                RailTrack newTrack = makeTrack();
                if (newTrack != null) {
                    setPosition(newTrack.getPosition().getX(), newTrack.getPosition().getY());
                }
                break;
            case REMOVE_TRACK:
                RailTrack removedTrack = removeTrack();
                if (removedTrack != null) {
                    Point newPosition = new Point(removedTrack.getPosition());
                    newPosition.move(getDirection());
                    setPosition(newPosition.getX(), newPosition.getY());
                }
                break;
        }
    }

    @Override
    public void selectNewTrackType(NewTrackType type){
        this.newTrackType = type;
    }
    public RailTrack createTrackOfSelectedType(){
        switch(newTrackType){
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
        // Si no hay un track aquí, creamos uno
        RailTrack actualTrack = map.getTrackAt(this.position.getX(), this.position.getY());
        if (actualTrack == null) {
            actualTrack = createTrackOfSelectedType();
            actualTrack.setPosition(getPosition());
            map.addTrack(getPosition(), actualTrack);
        }
        //De dónde veníamos
        if (oldTrack != null) {
            oldDir = getPosition().locate(oldTrack.getPosition());
        } else {
            if(!reversed) {
                oldDir = getDirection().inverse();
            }else{
                oldDir = getDirection();
            }
        }

//        actualTrack.addRoute(inverseRoute);

        //Calculamos la posición del nuevo track
        Point newPos = new Point(getPosition());
        if(!reversed) {
            newPos.move(getDirection(), 1);
        }else{
            newPos.move(getDirection().inverse());
        }

        //Creamos o localizamos el nuevo track
        RailTrack newTrack = map.getTrackAt(newPos);
        if (newTrack == null) {
            newTrack = createTrackOfSelectedType();
        }

        newTrack.setPosition(newPos);
        map.addTrack(newPos, newTrack);

        //Al track actual le agregamos la ruta entre de dónde veníamos y el nuevo
        Dir dirToNewTrack = getPosition().locate(newPos);
        actualTrack.addRoute(oldDir, dirToNewTrack);
        actualTrack.connect(dirToNewTrack, newTrack);

        Dir dirToActual = newTrack.getPosition().locate(actualTrack.getPosition());
        newTrack.connect(dirToActual, actualTrack);

        oldTrack = actualTrack;
        return newTrack;
    }

    private RailTrack removeTrack() {
        RailTrack actualTrack = map.getTrackAt(this.position.getX(), this.position.getY());
        if(actualTrack != null) {
            map.removeTrack(actualTrack.getPosition().getX(), actualTrack.getPosition().getY());
        }
        return actualTrack;
    }

    @Override
    public Dir getDirection() {
        return this.dir;
    }

    @Override
    public void setDirection(Dir d) {
        this.dir = d;
    }

    @Override
    public void reverse() {
        reversed = !reversed;
    }

    @Override
    public void rotateLeft() {
        if(degreesOfRotation <= 0 || getMode()== GameModel.Mode.MAP_WALK) {
            setDirection(this.dir.turnLeft());
            degreesOfRotation +=1;
        }
    }

    @Override
    public void rotateRight() {
        if(degreesOfRotation>=0|| getMode()== GameModel.Mode.MAP_WALK) {
            setDirection(this.dir.turnRight());
            degreesOfRotation-=1;
        }
    }

    @Override

    public boolean isReversed() {
        return reversed;
    }

    @Override
    public GameModel.Mode getMode() {
        return this.mode;
    }

    @Override
    public void setMode(GameModel.Mode mode) {
        if (mode != this.mode) {
            this.oldTrack = null;
        }
        this.mode = mode;
    }
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderRailTrackMaker(this);
    }
}
