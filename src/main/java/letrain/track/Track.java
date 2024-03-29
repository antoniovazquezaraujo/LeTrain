package letrain.track;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.map.Mapeable;
import letrain.map.Point;
import letrain.map.Router;
import letrain.utils.Pair;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Renderable;

public abstract class Track implements
        Serializable,
        Router,
        Connectable,
        LinkerCompartment,
        Mapeable,
        LinkerCompartmentListener,
        Renderable {
    private static final long serialVersionUID = 1L;
    private TrackDirector trackDirector;
    private Linker linker = null;
    private Sensor sensor = null;
    private Point pos = new Point(0, 0);
    protected Track[] connections;
    List<Pair<Dir, Point>> connectedPositions = new ArrayList<>();
    private final List<LinkerCompartmentListener> trackeableCompartmentListeners = new ArrayList<>();

    protected Track() {
        trackeableCompartmentListeners.add(this);
    }

    @Override
    public String toString() {
        return "{" + pos + " Connections:(" + getConnectedPositions().toString() + ")}";
    }

    public List<LinkerCompartmentListener> getTrackeableCompartmentListeners() {
        return trackeableCompartmentListeners;
    }

    public abstract Router getRouter();

    protected TrackDirector getTrackDirector() {
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
    public Dir getDir(Dir dir) {
        return getRouter().getDir(dir);
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
        resolveConnectedPositions();
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        getRouter().removeRoute(from, to);
        resolveConnectedPositions();
    }

    @Override
    public void clear() {
        getRouter().clear();
        resolveConnectedPositions();
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        getRouter().forEach(routeConsumer);
    }

    /**************************************************************
     * Connectable implementation
     **************************************************************
     * @return
     */

    @Override
    public Track getConnected(Dir dir) {
        return connections[dir.getValue()];
    }

    @Override
    public Track disconnect(Dir dir) {
        Track ret = connections[dir.getValue()];
        connections[dir.getValue()] = null;
        resolveConnectedPositions();
        return ret;
    }

    @Override
    public boolean connect(Dir dir, Track r) {
        connections[dir.getValue()] = r;
        resolveConnectedPositions();
        return true;
    }

    @Override
    public List<Dir> getConnections() {
        List<Dir> ret = new ArrayList<>();
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null) {
                ret.add(Dir.values()[i]);
            }
        }
        return ret;
    }

    public List<Pair<Dir, Point>> getConnectedPositions() {
        return this.connectedPositions;
    }

    public void resolveConnectedPositions() {
        this.connectedPositions = new ArrayList<>();
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null) {
                Track connected = getConnected(Dir.values()[i]);
                if (!this.connectedPositions.contains(connected)) {
                    this.connectedPositions.add(new Pair<Dir, Point>(Dir.values()[i], connected.getPosition()));
                }
            }
        }
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
     * @return
     */

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

    public Sensor getSensor() {
        return sensor;
    }

    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
    }

}
