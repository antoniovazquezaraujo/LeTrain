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

    /**
     * Two segments are the same logical canton iff they share the same {@link #getId() id}.
     *
     * <p>
     * Identity is deliberately <b>id-only</b> and NOT derived from the ports: a canton is a
     * <em>routing</em> unit and two different segments (e.g. two parallel tracks between the same
     * two nodes, see {@code TrainSafetyManager.tryAlternativeSegment}) can expose the very same
     * pair of ports. Comparing by ports would collapse those distinct cantons into one, breaking
     * block exclusivity. Ids are unique within a graph and generated deterministically by
     * {@link letrain.segments.impl.TopologyServiceImpl}, so they stay stable across a rebuild of
     * the same world (scenario replay / savegame reload). The previous "id or ports" compromise
     * also violated the {@code equals}/{@code hashCode} contract: equal objects could hash to
     * different buckets, which is unsafe because {@link Segment} is a key of a
     * {@code ConcurrentHashMap} in {@link letrain.segments.impl.BlockManagerImpl}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SegmentImpl segment = (SegmentImpl) o;
        return java.util.Objects.equals(id, segment.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}
