package letrain.segments.impl;

import java.util.Objects;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.segments.RailNode;

public class PortImpl implements Port {
    private final RailNode node;
    private final PortType type;

    public PortImpl(RailNode node, PortType type) {
        this.node = node;
        this.type = type;
    }

    @Override
    public RailNode getNode() {
        return node;
    }

    @Override
    public PortType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortImpl port = (PortImpl) o;
        return type == port.type && Objects.equals(node, port.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, type);
    }

    @Override
    public String toString() {
        return "Port{" + node + ", type=" + type + "}";
    }
}
