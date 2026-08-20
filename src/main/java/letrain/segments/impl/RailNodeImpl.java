package letrain.segments.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.segments.RailNode;
import letrain.segments.TransitionType;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;

public class RailNodeImpl implements RailNode {
    private final Track track;

    private final List<Port> ports = new ArrayList<>();
    private final Map<Dir, Port> dirToPort = new HashMap<>();
    private final Map<PortType, Dir> portToDir = new HashMap<>();

    public RailNodeImpl(Track track) {
        this.track = track;
        initializePorts();
    }

    private void initializePorts() {
        if (track instanceof ForkRailTrack fork) {
            Dir trunkDir = Dir.W;
            Dir normalDir = Dir.E;
            Dir alternativeDir = Dir.S;

            if (fork.getOriginalRoute() != null) {
                trunkDir = fork.getOriginalRoute().getKey();
                normalDir = fork.getOriginalRoute().getValue();
            }
            if (fork.getAlternativeRoute() != null) {
                alternativeDir = fork.getAlternativeRoute().getValue();
            }

            addPortMapping(PortType.TRUNK, trunkDir);
            addPortMapping(PortType.A, normalDir);
            addPortMapping(PortType.B, alternativeDir);
        } else {
            List<Dir> connections = new ArrayList<>(track.getConnections());
            if (!connections.isEmpty()) {
                addPortMapping(PortType.TRUNK, connections.get(0));
            }
        }
    }

    private void addPortMapping(PortType type, Dir dir) {
        Port port = new PortImpl(this, type);
        ports.add(port);
        dirToPort.put(dir, port);
        portToDir.put(type, dir);
    }

    public Port getPortForDir(Dir dir) {
        return dirToPort.get(dir);
    }

    public Dir getDirForPort(PortType type) {
        return portToDir.get(type);
    }

    @Override
    public Track getTrack() {
        return track;
    }

    @Override
    public List<Port> getPorts() {
        return ports;
    }

    @Override
    public TransitionType getTransitionType(Port entry, Port exit) {
        if (entry.getNode() != this || exit.getNode() != this) {
            return TransitionType.BLOCKED;
        }
        if (!(track instanceof ForkRailTrack)) {
            return TransitionType.BLOCKED;
        }

        PortType in = entry.getType();
        PortType out = exit.getType();

        if (in == PortType.TRUNK && (out == PortType.A || out == PortType.B)) {
            return TransitionType.DIVERGING;
        }
        if ((in == PortType.A || in == PortType.B) && out == PortType.TRUNK) {
            return TransitionType.CONVERGING;
        }
        return TransitionType.BLOCKED;
    }

    @Override
    public boolean setRoute(Port entry, Port exit) {
        if (getTransitionType(entry, exit) != TransitionType.DIVERGING) {
            return false;
        }

        ForkRailTrack fork = (ForkRailTrack) track;
        if (exit.getType() == PortType.A) {
            if (fork.isUsingAlternativeRoute()) {
                fork.setNormalRoute();
                return true;
            }
        } else if (exit.getType() == PortType.B) {
            if (!fork.isUsingAlternativeRoute()) {
                fork.setAlternativeRoute();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRouteActive(Port entry, Port exit) {
        TransitionType type = getTransitionType(entry, exit);
        if (type == TransitionType.BLOCKED) return false;
        if (type == TransitionType.CONVERGING) return true;

        ForkRailTrack fork = (ForkRailTrack) track;
        boolean usingAlt = fork.isUsingAlternativeRoute();
        return (exit.getType() == PortType.B && usingAlt) 
            || (exit.getType() == PortType.A && !usingAlt);
    }

    @Override
    public Port getActiveExit(Port entry) {
        if (!(track instanceof ForkRailTrack)) return null;
        if (entry.getType() == PortType.A || entry.getType() == PortType.B) {
            return getPortByType(PortType.TRUNK);
        }
        
        ForkRailTrack fork = (ForkRailTrack) track;
        PortType activeType = fork.isUsingAlternativeRoute() ? PortType.B : PortType.A;
        return getPortByType(activeType);
    }

    public Port getPortByType(PortType type) {
        return ports.stream()
                .filter(p -> p.getType() == type)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        if (track instanceof ForkRailTrack) {
            ForkRailTrack fork = (ForkRailTrack) track;
            return "Fork(" + fork.getId() + ")@" + track.getPosition();
        }
        return "DeadEnd@" + track.getPosition();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RailNodeImpl railNode = (RailNodeImpl) o;
        return Objects.equals(track.getPosition(), railNode.track.getPosition());
    }

    @Override
    public int hashCode() {
        return Objects.hash(track.getPosition());
    }
}
