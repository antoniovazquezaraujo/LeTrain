package letrain.track;

import javafx.util.Pair;
import letrain.map.*;
import letrain.vehicle.impl.Linker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Track<T extends Track> implements
        Router,
        Connectable<T>,
        LinkerCompartment<T>,
        Mapeable,
        LinkerCompartmentListener<T> {

    protected final Router router = new SimpleRouter();
    private TrackDirector<T> trackDirector;
    protected Linker<Track<T>> linker = null;
    protected Point pos = null;
    protected Connectable<T>[] connections;
    final List<LinkerCompartmentListener> trackeableCompartmentListeners = new ArrayList<>();

    public Track() {
        trackeableCompartmentListeners.add(this);
        trackDirector = TrackDirector.getInstance();
    }

    public List<LinkerCompartmentListener> getTrackeableCompartmentListeners() {
        return trackeableCompartmentListeners;
    }

    public Router getRouter() {
        return router;
    }

    public TrackDirector<T> getTrackDirector() {
        return trackDirector;
    }

    public void setTrackDirector(TrackDirector<T> trackDirector) {
        this.trackDirector = trackDirector;
    }


    /***********************************************************
     * Router implementation
     **********************************************************/
    @Override
    public Dir getAnyDir() {
        return router.getAnyDir();
    }

    @Override
    public boolean isStraight() {
        return router.isStraight();
    }

    @Override
    public boolean isCurve() {
        return router.isCurve();
    }

    @Override
    public boolean isCross() {
        return router.isCross();
    }

    @Override
    public boolean isFork() {
        return router.isFork();
    }

    @Override
    public Dir getDir(Dir dir) {
        return router.getDir(dir);
    }

    @Override
    public Dir getFirstOpenDir() {
        return router.getFirstOpenDir();
    }

    @Override
    public int getNumRoutes() {
        return router.getNumRoutes();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        router.addRoute(from, to);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        router.removeRoute(from, to);
    }

    @Override
    public void clear() {
        router.clear();
    }

    @Override
    public void setAlternativeRoute() {
        router.setAlternativeRoute();
    }

    @Override
    public void setNormalRoute() {
        router.setNormalRoute();
    }

    @Override
    public boolean flipRoute() {
        return router.flipRoute();
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        router.forEach(routeConsumer);
    }

    @Override
    public boolean isUsingAlternativeRoute() {
        return router.isUsingAlternativeRoute();
    }


    /**************************************************************
     * Connectable implementation
     ***************************************************************/

    @Override
    public Connectable<T> getConnected(Dir dir) {
        return connections[dir.getValue()];
    }

    @Override
    public Connectable<T> disconnect(Dir dir) {
        Connectable<T> ret = connections[dir.getValue()];
        connections[dir.getValue()] = null;
        return ret;
    }

    @Override
    public boolean connect(Dir dir, Connectable<T> r) {
        connections[dir.getValue()] = r;
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
        this.pos = pos;
    }


    /**************************************************************
     * LinkerCompartment implementation
     **************************************************************
     * @return*/

    @Override
    public Linker<Track<T>> getLinker() {
        return linker;
    }

    @Override
    public void enterLinker(Dir d, Linker<Track<T>> vehicle) {
        getTrackDirector().enterLinker(this, d, vehicle);
    }

    @Override
    public Linker<Track<T>> exitLinker(Dir d) {
        return getTrackDirector().exitLinker(this, d);
    }

    @Override
    public void setLinker(Linker<Track<T>> linker) {
        this.linker = linker;
    }

    @Override
    public void addLinkerCompartmentListener(LinkerCompartmentListener<T> listener) {
        trackeableCompartmentListeners.add(listener);
    }

    @Override
    public void removeLinkerCompartmentListener(LinkerCompartmentListener<T> listener) {
        trackeableCompartmentListeners.remove(listener);
    }

    /**************************************************************
     * LinkerCompartmentListener implementation
     ***************************************************************/
    @Override
    public boolean canEnter(Dir d, Linker<T> v) {
        return getTrackDirector().canEnter(this, d, v);
    }

    @Override
    public boolean canExit(Dir d) {
        return getTrackDirector().canExit(this, d);
    }


}
