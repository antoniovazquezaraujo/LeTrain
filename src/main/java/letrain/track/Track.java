package letrain.track;

import javafx.util.Pair;
import letrain.map.*;
import letrain.vehicle.impl.Linker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Track implements
        Router,
        Connectable,
        LinkerCompartment,
        Mapeable,
        LinkerCompartmentListener {

    protected final Router router = new SimpleRouter();
    private TrackDirector trackDirector;
    protected Linker linker = null;
    protected Point pos = null;
    protected Connectable[] connections;
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

    public TrackDirector  getTrackDirector() {
        return trackDirector;
    }

    public void setTrackDirector(TrackDirector  trackDirector) {
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
    public Connectable getConnected(Dir dir) {
        return connections[dir.getValue()];
    }

    @Override
    public Connectable disconnect(Dir dir) {
        Connectable ret = connections[dir.getValue()];
        connections[dir.getValue()] = null;
        return ret;
    }

    @Override
    public boolean connect(Dir dir, Connectable r) {
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
    public Linker getLinker() {
        return linker;
    }

    @Override
    public void enterLinker(Dir d, Linker vehicle) {
        getTrackDirector().enterLinker(this, d, vehicle);
    }

    @Override
    public Linker exitLinker(Dir d) {
        return getTrackDirector().exitLinker(this, d);
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
    public boolean canEnter(Dir d, Linker v) {
        return getTrackDirector().canEnter(this, d, v);
    }

    @Override
    public boolean canExit(Dir d) {
        return getTrackDirector().canExit(this, d);
    }


}
