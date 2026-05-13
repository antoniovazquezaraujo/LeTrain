package letrain.itinerary;

import letrain.core.segments.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SegmentPathfinder contract")
class SegmentPathfinderTest {

    @Test
    @DisplayName("should return empty list for null from")
    void nullFrom() {
        SegmentPathfinder pf = new AStarPathfinder(null);
        Segment mockSeg = mock(Segment.class);
        assertTrue(pf.find(null, mockSeg, Optional.empty()).isEmpty());
    }

    @Test
    @DisplayName("should return empty list for null to")
    void nullTo() {
        SegmentPathfinder pf = new AStarPathfinder(null);
        Segment mockSeg = mock(Segment.class);
        assertTrue(pf.find(mockSeg, null, Optional.empty()).isEmpty());
    }

    @Test
    @DisplayName("should return self when from equals to")
    void selfPath() {
        SegmentPathfinder pf = new AStarPathfinder(null);
        Segment mockSeg = mock(Segment.class);
        List<Segment> path = pf.find(mockSeg, mockSeg, Optional.empty());
        assertEquals(1, path.size());
        assertEquals(mockSeg, path.get(0));
    }

    @Test
    @DisplayName("should return empty when no graph available")
    void noGraph() {
        SegmentPathfinder pf = new AStarPathfinder(null);
        Segment a = mock(Segment.class);
        Segment b = mock(Segment.class);
        assertTrue(pf.find(a, b, Optional.empty()).isEmpty());
    }
}
