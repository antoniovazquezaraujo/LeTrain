package letrain.track;

import javafx.util.Pair;
import letrain.map.*;
import letrain.vehicle.impl.Linker;
import letrain.render.Renderable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class Track implements
        Router,
        Connectable,
        LinkerCompartment,
        Mapeable,
        LinkerCompartmentListener,
        Renderable {

    private final Router router = new SimpleRouter();
    private TrackDirector trackDirector;
    private Linker linker = null;
    private Point pos = new Point(0,0);
    protected Track[] connections;
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

    public Router getRouter() {
        return router;
    }

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
     **************************************************************
     * @return*/

    @Override
    public Track getConnected(Dir dir) {
        return connections[dir.getValue()];
    }

    @Override
    public Track disconnect(Dir dir) {
        Track ret = connections[dir.getValue()];
        connections[dir.getValue()] = null;
        return ret;
    }

    @Override
    public boolean connect(Dir dir, Track r) {
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
    public void enterLinkerFromDir(Dir d, Linker vehicle) {
        getTrackDirector().enterLinkerFromDir(this, d, vehicle);
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
    public boolean canEnter(Dir d, Linker v) {
        return getTrackDirector().canEnter(this, d, v);
    }

    @Override
    public boolean canExit(Dir d) {
        return getTrackDirector().canExit(this, d);
    }


}
