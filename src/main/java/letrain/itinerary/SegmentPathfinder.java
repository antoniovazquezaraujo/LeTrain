package letrain.itinerary;

import java.util.List;
import java.util.Optional;

import letrain.map.Dir;
import letrain.segments.Segment;

/**
 * Pure function: finds a path between two segments on the railway graph.
 * Does not control speed, events, or train state.
 */
public interface SegmentPathfinder {
    /**
     * @param from      starting segment
     * @param to        destination segment
     * @param entryDir  optional required entry direction into the destination
     * @return ordered list of segments from 'from' to 'to', or empty if unreachable
     */
    List<Segment> find(Segment from, Segment to, Optional<Dir> entryDir);

    default List<Segment> find(Segment from, Optional<letrain.segments.Port> fromExitPort, Segment to, Optional<Dir> entryDir) {
        return find(from, to, entryDir);
    }
}
