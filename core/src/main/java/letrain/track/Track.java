package letrain.track;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import letrain.map.Dir;
import letrain.map.Mappable;
import letrain.map.Point;
import letrain.map.Router;
import letrain.utils.Pair;
import letrain.vehicle.rail.Linker;
import letrain.visitor.Renderable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = letrain.track.rail.RailTrack.class, name = "RailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.ForkRailTrack.class, name = "ForkRailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.BridgeRailTrack.class,
                name = "BridgeRailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.BridgeGateRailTrack.class,
                name = "BridgeGateRailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.TunnelRailTrack.class,
                name = "TunnelRailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.TunnelGateRailTrack.class,
                name = "TunnelGateRailTrack"),
        @JsonSubTypes.Type(value = letrain.track.rail.StationRailTrack.class,
                name = "StationRailTrack")})
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Track implements Router, Connectable, LinkerCompartment, Mappable,
        LinkerCompartmentListener, Renderable {
    @JsonIgnore
    private TrackDirector trackDirector;

    private Linker linker = null;
    private Linker reservation = null; // NEW: Track reservation to prevent race conditions during
                                       // multi-train ticks
    @com.fasterxml.jackson.annotation.JsonAlias({"sensor", "semaphore"})
    @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.SKIP)
    private TrackComponent component = null;
    private Point pos = new Point(0, 0);

    @com.fasterxml.jackson.annotation.JsonProperty("connectedTracks")
    protected Track[] connections;

    @JsonIgnore
    List<Pair<Dir, Point>> connectedPositions = new ArrayList<>();

    private final List<LinkerCompartmentListener> trackableCompartmentListeners = new ArrayList<>();

    protected Track() {
        trackableCompartmentListeners.add(this);
    }

    @Override
    public String toString() {
        return "{" + pos + " Connections:(" + getConnectedPositions().toString() + ")}";
    }

    @JsonIgnore
    public List<LinkerCompartmentListener> getTrackableCompartmentListeners() {
        return trackableCompartmentListeners;
    }

    public void setConnectedTracks(Track[] connectedTracks) {
        this.connections = connectedTracks;
    }

    public abstract void setRouter(Router router);

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
    @JsonIgnore
    public List<Dir> getConnections() {
        List<Dir> ret = new ArrayList<>();
        if (connections == null) {
            return ret;
        }
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null) {
                ret.add(Dir.values()[i]);
            }
        }
        return ret;
    }

    @JsonIgnore
    public List<Pair<Dir, Point>> getConnectedPositions() {
        return this.connectedPositions;
    }

    public void resolveConnectedPositions() {
        this.connectedPositions = new ArrayList<>();
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null) {
                Track connected = getConnected(Dir.values()[i]);
                boolean alreadyAdded = false;
                for (Pair<Dir, Point> pair : connectedPositions) {
                    if (pair.getSecond().equals(connected.getPosition())) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    this.connectedPositions
                            .add(new Pair<Dir, Point>(Dir.values()[i], connected.getPosition()));
                }
            }
        }
    }

    /**************************************************************
     * Mappable implementation
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
    public boolean enterLinkerFromDir(Dir dir, Linker vehicle) {
        return getTrackDirector().enterLinkerFromDir(this, dir, vehicle);
    }

    @Override
    public Linker removeLinker() {
        return getTrackDirector().removeLinker(this);
    }

    public void setReservation(Linker reservation) {
        this.reservation = reservation;
    }

    public Linker getReservation() {
        return reservation;
    }

    @Override
    public void setLinker(Linker linker) {
        this.linker = linker;
    }

    @Override
    public void addLinkerCompartmentListener(LinkerCompartmentListener listener) {
        trackableCompartmentListeners.add(listener);
    }

    @Override
    public void removeLinkerCompartmentListener(LinkerCompartmentListener listener) {
        trackableCompartmentListeners.remove(listener);
    }

    /**************************************************************
     * LinkerCompartmentListener implementation
     ***************************************************************/
    @Override
    public boolean canEnter(Dir dir, Linker v) {
        return getTrackDirector().canEnter(this, dir, v);
    }

    @Override
    public boolean canExit(Dir dir) {
        return getTrackDirector().canExit(this, dir);
    }

    public TrackComponent getComponent() {
        return component;
    }

    public void setComponent(TrackComponent component) {
        this.component = component;
    }
}
