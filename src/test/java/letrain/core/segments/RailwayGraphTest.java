package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.segments.Port;
import letrain.segments.Segment;
import letrain.segments.impl.RailNodeImpl;
import letrain.segments.impl.RailwayGraphImpl;
import letrain.segments.impl.SegmentImpl;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RailwayGraphTest {
    private RailwayGraphImpl graph;
    private RailNodeImpl nodeA;
    private RailNodeImpl nodeB;
    private RailNodeImpl nodeC;
    private ForkRailTrack trackB;

    private Port pA_E;
    private Port pB_W;
    private Port pB_E;
    private Port pC_W;

    private Segment segmentAB;
    private Segment segmentBC;

    @BeforeEach
    void setUp() {
        graph = new RailwayGraphImpl();

        // Tracks físicos para los nodos
        RailTrack trackA = new RailTrack();
        trackA.setPosition(new Point(0, 0));
        trackB = new ForkRailTrack(1);
        trackB.setPosition(new Point(1, 0));
        RailTrack trackC = new RailTrack();
        trackC.setPosition(new Point(2, 0));

        // Conectar tracks físicamente primero
        trackA.connect(Dir.E, trackB);
        trackB.connect(Dir.W, trackA);
        trackB.connect(Dir.E, trackC);
        trackC.connect(Dir.W, trackB);

        // Configurar rutas de desvío
        trackB.addRoute(Dir.W, Dir.E);
        trackB.addRoute(Dir.W, Dir.S);

        // Nodos
        nodeA = new RailNodeImpl(trackA); // Tope Oeste
        nodeB = new RailNodeImpl(trackB); // Fork
        nodeC = new RailNodeImpl(trackC); // Tope Este

        // Obtener puertos lógicos
        pA_E = nodeA.getPortForDir(Dir.E);
        pB_W = nodeB.getPortForDir(Dir.W);
        pB_E = nodeB.getPortForDir(Dir.E);
        pC_W = nodeC.getPortForDir(Dir.W);

        assertNotNull(pA_E);
        assertNotNull(pB_W);
        assertNotNull(pB_E);
        assertNotNull(pC_W);

        // Segmentos (unión de dos puertos)
        segmentAB = new SegmentImpl("AB", pA_E, pB_W);
        segmentBC = new SegmentImpl("BC", pB_E, pC_W);

        // Registrar en el grafo
        graph.registerSegment(pA_E, segmentAB);
        graph.registerSegment(pB_W, segmentAB);
        graph.registerSegment(pB_E, segmentBC);
        graph.registerSegment(pC_W, segmentBC);
    }

    @Test
    void testGetNextPorts() {
        List<Port> nextPorts = graph.getNextPorts(pA_E);
        assertNotNull(nextPorts);
        assertEquals(1, nextPorts.size());
        assertEquals(pB_E, nextPorts.get(0));
    }

    @Test
    void testEndOfTrackReturnsEmpty() {
        List<Port> nextPorts = graph.getNextPorts(pB_E);
        assertTrue(nextPorts.isEmpty());
    }

    @Test
    void testMultipleExitsFromFork() {
        RailTrack trackD = new RailTrack();
        trackD.setPosition(new Point(1, 1));
        trackD.connect(Dir.N, trackB);
        trackB.connect(Dir.S, trackD);

        RailNodeImpl nodeD = new RailNodeImpl(trackD);
        Port pB_S = nodeB.getPortForDir(Dir.S);
        Port pD_N = nodeD.getPortForDir(Dir.N);
        assertNotNull(pB_S);
        assertNotNull(pD_N);

        Segment segmentBD = new SegmentImpl("BD", pB_S, pD_N);
        graph.registerSegment(pB_S, segmentBD);
        graph.registerSegment(pD_N, segmentBD);

        List<Port> nextPorts = graph.getNextPorts(pA_E);

        assertEquals(2, nextPorts.size());
        assertTrue(nextPorts.contains(pB_E));
        assertTrue(nextPorts.contains(pB_S));
        assertFalse(nextPorts.contains(pB_W));
    }

    @Test
    void testCircularSegment() {
        letrain.segments.RailNode circularNode = org.mockito.Mockito.mock(letrain.segments.RailNode.class);
        Port exit1 = org.mockito.Mockito.mock(Port.class);
        Port exit2 = org.mockito.Mockito.mock(Port.class);

        org.mockito.Mockito.when(exit1.getNode()).thenReturn(circularNode);
        org.mockito.Mockito.when(exit2.getNode()).thenReturn(circularNode);
        org.mockito.Mockito.when(circularNode.getPorts()).thenReturn(List.of(exit1, exit2));

        Segment circularSegment = new SegmentImpl("CIRCLE", exit1, exit2);
        graph.registerSegment(exit1, circularSegment);
        graph.registerSegment(exit2, circularSegment);

        List<Port> nextPorts = graph.getNextPorts(exit1);
        assertTrue(nextPorts.isEmpty());
    }

    @Test
    void testFindPath() {
        List<Segment> path = graph.findPath(segmentAB, segmentBC);
        assertNotNull(path);
        assertEquals(2, path.size());
        assertEquals(segmentAB, path.get(0));
        assertEquals(segmentBC, path.get(1));
    }

    @Test
    void testFindPathToSelf() {
        List<Segment> path = graph.findPath(segmentAB, segmentAB);
        assertEquals(1, path.size());
        assertEquals(segmentAB, path.get(0));
    }

    @Test
    void testFindNonExistentPath() {
        RailTrack tIso1 = new RailTrack();
        tIso1.setPosition(new Point(10, 10));
        RailTrack tIso2 = new RailTrack();
        tIso2.setPosition(new Point(10, 11));
        tIso1.connect(Dir.N, tIso2);
        tIso2.connect(Dir.S, tIso1);
        RailNodeImpl nodeIso1 = new RailNodeImpl(tIso1);
        RailNodeImpl nodeIso2 = new RailNodeImpl(tIso2);
        Segment isolatedSegment = new SegmentImpl("ISO", nodeIso1.getPortForDir(Dir.N), nodeIso2.getPortForDir(Dir.S));

        List<Segment> path = graph.findPath(segmentAB, isolatedSegment);
        assertTrue(path.isEmpty());
    }

    @Test
    void testNonExistentPort() {
        RailTrack tGhost = new RailTrack();
        tGhost.setPosition(new Point(100, 100));
        tGhost.connect(Dir.NE, tGhost);
        RailNodeImpl ghostNode = new RailNodeImpl(tGhost);
        Port ghostPort = ghostNode.getPortForDir(Dir.NE);
        assertNull(graph.getSegment(ghostPort));
        assertNull(graph.getNextPorts(ghostPort));
    }
}
