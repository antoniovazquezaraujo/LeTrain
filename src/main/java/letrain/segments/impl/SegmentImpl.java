package letrain.segments.impl;

import letrain.segments.Port;
import letrain.segments.Segment;
import letrain.utils.Pair;

public class SegmentImpl implements Segment {
    private final String id;
    private final Pair<Port, Port> ports;

    public SegmentImpl(String id, Port p1, Port p2) {
        this.id = id;
        this.ports = new Pair<>(p1, p2);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Pair<Port, Port> getPorts() {
        return ports;
    }

    @Override
    public String toString() {
        return "Segment[" + id + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SegmentImpl segment = (SegmentImpl) o;
        // Same ID → same logical segment
        if (id != null && id.equals(segment.id)) {
            return true;
        }
        // Compare by ports (order-independent)
        if (ports == null || segment.ports == null) {
            return false;
        }
        return (java.util.Objects.equals(ports.getFirst(), segment.ports.getFirst())
                        && java.util.Objects.equals(ports.getSecond(), segment.ports.getSecond()))
                || (java.util.Objects.equals(ports.getFirst(), segment.ports.getSecond())
                        && java.util.Objects.equals(ports.getSecond(), segment.ports.getFirst()));
    }

    @Override
    public int hashCode() {
        if (ports == null) {
            return 0;
        }
        int h1 = ports.getFirst() != null ? ports.getFirst().hashCode() : 0;
        int h2 = ports.getSecond() != null ? ports.getSecond().hashCode() : 0;
        // Hash independent of ports order
        return h1 + h2;
    }
}
