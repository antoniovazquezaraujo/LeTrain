package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import letrain.map.Dir;
import letrain.segments.PathStep;
import letrain.segments.Port;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.impl.RailNodeImpl;
import letrain.utils.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("should choose route with lower physical track cost")
    void physicalTrackCost() {
        RailwayGraph graph = mock(RailwayGraph.class);
        SegmentPathfinder pf = new AStarPathfinder(graph);

        Segment from = mock(Segment.class, "from");
        Segment b = mock(Segment.class, "b");
        Segment c = mock(Segment.class, "c");
        Segment to = mock(Segment.class, "to");

        PathStep fromStep1 = mock(PathStep.class);
        PathStep fromStep2 = mock(PathStep.class);
        PathStep bStep1 = mock(PathStep.class);
        PathStep bStep2 = mock(PathStep.class);
        PathStep cStep1 = mock(PathStep.class);
        PathStep cStep2 = mock(PathStep.class);
        PathStep toStep1 = mock(PathStep.class);
        PathStep toStep2 = mock(PathStep.class);

        when(from.getSteps()).thenReturn(new Pair<>(fromStep1, fromStep2));
        when(b.getSteps()).thenReturn(new Pair<>(bStep1, bStep2));
        when(c.getSteps()).thenReturn(new Pair<>(cStep1, cStep2));
        when(to.getSteps()).thenReturn(new Pair<>(toStep1, toStep2));

        when(graph.getSegment(fromStep1)).thenReturn(from);
        when(graph.getSegment(fromStep2)).thenReturn(from);
        when(graph.getSegment(bStep1)).thenReturn(b);
        when(graph.getSegment(bStep2)).thenReturn(b);
        when(graph.getSegment(cStep1)).thenReturn(c);
        when(graph.getSegment(cStep2)).thenReturn(c);
        when(graph.getSegment(toStep1)).thenReturn(to);
        when(graph.getSegment(toStep2)).thenReturn(to);

        // from connects to b and c
        when(graph.getNextSteps(fromStep2)).thenReturn(List.of(bStep1, cStep1));
        // b connects to to
        when(graph.getNextSteps(bStep2)).thenReturn(List.of(toStep1));
        // c connects to to
        when(graph.getNextSteps(cStep2)).thenReturn(List.of(toStep2));

        // b has high track count, c has low track count
        when(graph.getTrackCount(from)).thenReturn(1);
        when(graph.getTrackCount(b)).thenReturn(10);
        when(graph.getTrackCount(c)).thenReturn(2);
        when(graph.getTrackCount(to)).thenReturn(1);

        // Find path
        List<Segment> path = pf.find(from, to, Optional.empty());
        assertEquals(3, path.size());
        assertEquals(from, path.get(0));
        assertEquals(c, path.get(1)); // Should choose the cheaper segment c!
        assertEquals(to, path.get(2));
    }

    @Test
    @DisplayName("should enforce entryDir constraint")
    void entryDirConstraint() {
        RailwayGraph graph = mock(RailwayGraph.class);
        SegmentPathfinder pf = new AStarPathfinder(graph);

        Segment from = mock(Segment.class, "from");
        Segment to = mock(Segment.class, "to");

        PathStep fromStep1 = mock(PathStep.class);
        PathStep fromStep2 = mock(PathStep.class);
        PathStep toStep1 = mock(PathStep.class);
        PathStep toStep2 = mock(PathStep.class);

        when(from.getSteps()).thenReturn(new Pair<>(fromStep1, fromStep2));
        when(to.getSteps()).thenReturn(new Pair<>(toStep1, toStep2));

        when(graph.getSegment(fromStep1)).thenReturn(from);
        when(graph.getSegment(fromStep2)).thenReturn(from);
        when(graph.getSegment(toStep1)).thenReturn(to);
        when(graph.getSegment(toStep2)).thenReturn(to);

        // from connects to to via toStep1
        when(graph.getNextSteps(fromStep2)).thenReturn(List.of(toStep1));
        when(toStep1.getDir()).thenReturn(Dir.E); // Entry direction is East

        // If we request entryDir as West, path should not be found
        List<Segment> pathNo = pf.find(from, to, Optional.of(Dir.W));
        assertTrue(pathNo.isEmpty());

        // If we request entryDir as East, path should be found
        List<Segment> pathYes = pf.find(from, to, Optional.of(Dir.E));
        assertEquals(2, pathYes.size());
        assertEquals(from, pathYes.get(0));
        assertEquals(to, pathYes.get(1));

        // If no entryDir constraint, path should be found
        List<Segment> pathAny = pf.find(from, to, Optional.empty());
        assertEquals(2, pathAny.size());
        assertEquals(from, pathAny.get(0));
        assertEquals(to, pathAny.get(1));
    }

    @Test
    @DisplayName("should choose route with lower physical track cost using ports")
    void physicalTrackCostUsingPorts() {
        RailwayGraph graph = mock(RailwayGraph.class);
        SegmentPathfinder pf = new AStarPathfinder(graph);

        Segment from = mock(Segment.class, "from");
        Segment b = mock(Segment.class, "b");
        Segment c = mock(Segment.class, "c");
        Segment to = mock(Segment.class, "to");

        Port fromPort1 = mock(Port.class);
        Port fromPort2 = mock(Port.class);
        Port bPort1 = mock(Port.class);
        Port bPort2 = mock(Port.class);
        Port cPort1 = mock(Port.class);
        Port cPort2 = mock(Port.class);
        Port toPort1 = mock(Port.class);
        Port toPort2 = mock(Port.class);

        when(from.getPorts()).thenReturn(new Pair<>(fromPort1, fromPort2));
        when(b.getPorts()).thenReturn(new Pair<>(bPort1, bPort2));
        when(c.getPorts()).thenReturn(new Pair<>(cPort1, cPort2));
        when(to.getPorts()).thenReturn(new Pair<>(toPort1, toPort2));

        when(graph.getSegment(fromPort1)).thenReturn(from);
        when(graph.getSegment(fromPort2)).thenReturn(from);
        when(graph.getSegment(bPort1)).thenReturn(b);
        when(graph.getSegment(bPort2)).thenReturn(b);
        when(graph.getSegment(cPort1)).thenReturn(c);
        when(graph.getSegment(cPort2)).thenReturn(c);
        when(graph.getSegment(toPort1)).thenReturn(to);
        when(graph.getSegment(toPort2)).thenReturn(to);

        // from connects to b and c
        when(graph.getNextPorts(fromPort2)).thenReturn(List.of(bPort1, cPort1));
        // b connects to to
        when(graph.getNextPorts(bPort2)).thenReturn(List.of(toPort1));
        // c connects to to
        when(graph.getNextPorts(cPort2)).thenReturn(List.of(toPort2));

        // b has high track count, c has low track count
        when(graph.getTrackCount(from)).thenReturn(1);
        when(graph.getTrackCount(b)).thenReturn(10);
        when(graph.getTrackCount(c)).thenReturn(2);
        when(graph.getTrackCount(to)).thenReturn(1);

        // Find path
        List<Segment> path = pf.find(from, to, Optional.empty());
        assertEquals(3, path.size());
        assertEquals(from, path.get(0));
        assertEquals(c, path.get(1)); // Should choose the cheaper segment c!
        assertEquals(to, path.get(2));
    }

    @Test
    @DisplayName("should enforce entryDir constraint using ports")
    void entryDirConstraintUsingPorts() {
        RailwayGraph graph = mock(RailwayGraph.class);
        SegmentPathfinder pf = new AStarPathfinder(graph);

        Segment from = mock(Segment.class, "from");
        Segment to = mock(Segment.class, "to");

        Port fromPort1 = mock(Port.class);
        Port fromPort2 = mock(Port.class);
        Port toPort1 = mock(Port.class);
        Port toPort2 = mock(Port.class);

        when(from.getPorts()).thenReturn(new Pair<>(fromPort1, fromPort2));
        when(to.getPorts()).thenReturn(new Pair<>(toPort1, toPort2));

        when(graph.getSegment(fromPort1)).thenReturn(from);
        when(graph.getSegment(fromPort2)).thenReturn(from);
        when(graph.getSegment(toPort1)).thenReturn(to);
        when(graph.getSegment(toPort2)).thenReturn(to);

        // from connects to to via toPort1
        when(graph.getNextPorts(fromPort2)).thenReturn(List.of(toPort1));
        
        letrain.segments.impl.RailNodeImpl toNode = mock(letrain.segments.impl.RailNodeImpl.class);
        when(toPort1.getNode()).thenReturn(toNode);
        
        letrain.segments.PortType portType = letrain.segments.PortType.A;
        when(toPort1.getType()).thenReturn(portType);
        when(toNode.getDirForPort(portType)).thenReturn(Dir.E); // Entry direction is East

        // If we request entryDir as West, path should not be found
        List<Segment> pathNo = pf.find(from, to, Optional.of(Dir.W));
        assertTrue(pathNo.isEmpty());

        // If we request entryDir as East, path should be found
        List<Segment> pathYes = pf.find(from, to, Optional.of(Dir.E));
        assertEquals(2, pathYes.size());
        assertEquals(from, pathYes.get(0));
        assertEquals(to, pathYes.get(1));
    }
}
