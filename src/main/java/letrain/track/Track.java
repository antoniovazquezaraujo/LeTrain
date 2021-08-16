package letrain.track;

import javafx.util.Pair;
import letrain.map.*;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Renderable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Track implements
        Serializable,
        Router,
        ConnectableTrack,
        LinkerCompartment,
        Mapeable,
        LinkerCompartmentListener,
        Renderable {

    private TrackDirector trackDirector;
    private Linker linker = null;
    private Point pos = new Point(0,0);
    protected Map<Dir, Track> connections;
    private final List<LinkerCompartmentListener> trackeableCompartmentListeners = new ArrayList<>();

    protected Track() {
        trackeableCompartmentListeners.add(this);
    }

    @Override
    public String toString() {
        return "Track{" +
                ", pos=" + pos +
                '}';
    }

    public List<LinkerCompartmentListener> getTrackeableCompartmentListeners() {
        return trackeableCompartmentListeners;
    }

    public abstract Router getRouter();

    protected TrackDirector  getTrackDirector() {
        return trackDirector;
    }

    protected void setTrackDirector(TrackDirector trackDirector) {
        this.trackDirector = trackDirector;
    }


    /***********************************************************
     * Router implementation
     **********************************************************/
    @Override
    public Dir getAnyDir() {
        return getRouter().getAnyDir();
    }

    @Override
    public boolean isStraight() {
        return getRouter().isStraight();
    }

    @Override
    public boolean isCurve() {
        return getRouter().isCurve();
    }

    @Override
    public boolean isCross() {
        return getRouter().isCross();
    }

     @Override
    public Dir getDirWhenEnteringFrom(Dir dir) {
        return getRouter().getDirWhenEnteringFrom(dir);
    }

    @Override
    public Dir getFirstOpenDir() {
        return getRouter().getFirstOpenDir();
    }

    @Override
    public int getNumRoutes() {
        return getRouter().getNumRoutes();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        getRouter().addRoute(from, to);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        getRouter().removeRoute(from, to);
    }

    @Override
    public void clear() {
        getRouter().clear();
    }


    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        getRouter().forEach(routeConsumer);
    }



    /**************************************************************
     * Connectable implementation
     **************************************************************/

    @Override
    public Track getConnectedTrack() {
        Dir outDir = getLinker().getDir();
        if(getLinker().isReversed()){
            outDir = outDir.inverse();
        }
        return getConnectedTrack(outDir);
    }

    @Override
    public Track getConnectedTrack(Dir dir) {
       return connections.get(dir);
    }

    @Override
    public Track disconnectTrack(Dir dir) {
         return connections.remove(dir);
    }

    @Override
    public boolean connectTrack(Dir dir, Track track) {
         connections.put(dir, track);
        return true;
    }

    /**************************************************************
     * Mapeable implementation
     ***************************************************************/
    @Override
    public Point getPosition() {
        return pos;
    }

    @Override
    public void setPosition(Point pos) {
        this.pos.setX(pos.getX());
        this.pos.setY(pos.getY());
    }


    /**************************************************************
     * LinkerCompartment implementation
     **************************************************************
     * @return*/

    @Override
    public Linker getLinker() {
        return linker;
    }

    @Override
    public void enter(Dir d, Linker vehicle) {
        getTrackDirector().enterLinkerFromDir(this,vehicle.getDir(), vehicle);
    }
    @Override
    public void enter(Linker vehicle) {
        getTrackDirector().enter(this, vehicle);
    }
    @Override
    public Linker removeLinker() {
        return getTrackDirector().removeLinker(this);
    }

    @Override
    public void setLinker(Linker linker) {
        this.linker = linker;
    }

    @Override
    public void addLinkerCompartmentListener(LinkerCompartmentListener listener) {
        trackeableCompartmentListeners.add(listener);
    }

    @Override
    public void removeLinkerCompartmentListener(LinkerCompartmentListener listener) {
        trackeableCompartmentListeners.remove(listener);
    }

    /**************************************************************
     * LinkerCompartmentListener implementation
     ***************************************************************/
    @Override
    public boolean canEnter(Linker v) {
        return getTrackDirector().canEnter( this, v.getDir(),  v);
    }

    @Override
    public boolean canExit(Dir d) {
        return getTrackDirector().canExit(this,  this.linker.getDir());
    }


}
