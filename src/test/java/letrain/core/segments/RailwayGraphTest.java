package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.segments.PathStep;
import letrain.segments.Segment;
import letrain.segments.impl.PathStepImpl;
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
    
    private PathStep stepA_to_B;
    private PathStep stepB_to_A;
    private PathStep stepB_to_C;
    private PathStep stepC_to_B;
    
    private Segment segmentAB;
    private Segment segmentBC;

    @BeforeEach
    void setUp() {
        graph = new RailwayGraphImpl();
        
        // Tracks físicos para los nodos
        RailTrack trackA = new RailTrack(); trackA.setPosition(new Point(0,0));
        ForkRailTrack trackB = new ForkRailTrack(1); trackB.setPosition(new Point(1,0));
        RailTrack trackC = new RailTrack(); trackC.setPosition(new Point(2,0));

        // Nodos
        nodeA = new RailNodeImpl(trackA); // Tope
        nodeB = new RailNodeImpl(trackB); // Fork (decisión)
        nodeC = new RailNodeImpl(trackC); // Tope
        
        // Pasos (Decisiones)
        stepA_to_B = new PathStepImpl(nodeA, Dir.E);
        stepB_to_A = new PathStepImpl(nodeB, Dir.W);
        stepB_to_C = new PathStepImpl(nodeB, Dir.E);
        stepC_to_B = new PathStepImpl(nodeC, Dir.W);
        
        // Registrar pasos en los nodos
        nodeA.addOutStep(stepA_to_B);
        nodeB.addOutStep(stepB_to_A);
        nodeB.addOutStep(stepB_to_C);
        nodeC.addOutStep(stepC_to_B);
        
        // Segmentos (unión de dos pasos enfrentados)
        segmentAB = new SegmentImpl("AB", stepA_to_B, stepB_to_A);
        segmentBC = new SegmentImpl("BC", stepB_to_C, stepC_to_B);
        
        // Registrar en el grafo
        graph.registerSegment(stepA_to_B, segmentAB);
        graph.registerSegment(stepB_to_A, segmentAB);
        graph.registerSegment(stepB_to_C, segmentBC);
        graph.registerSegment(stepC_to_B, segmentBC);
    }

    @Test
    void testGetNextSteps() {
        List<PathStep> nextSteps = graph.getNextSteps(stepA_to_B);
        assertNotNull(nextSteps);
        assertEquals(1, nextSteps.size());
        assertEquals(stepB_to_C, nextSteps.get(0));
    }

    @Test
    void testEndOfTrackReturnsEmpty() {
        List<PathStep> nextSteps = graph.getNextSteps(stepB_to_C);
        assertTrue(nextSteps.isEmpty());
    }

    @Test
    void testMultipleExitsFromFork() {
        RailTrack trackD = new RailTrack(); trackD.setPosition(new Point(1,1));
        RailNodeImpl nodeD = new RailNodeImpl(trackD);
        PathStep stepB_to_D = new PathStepImpl(nodeB, Dir.S);
        PathStep stepD_to_B = new PathStepImpl(nodeD, Dir.N);
        nodeB.addOutStep(stepB_to_D);
        nodeD.addOutStep(stepD_to_B);
        
        Segment segmentBD = new SegmentImpl("BD", stepB_to_D, stepD_to_B);
        graph.registerSegment(stepB_to_D, segmentBD);
        graph.registerSegment(stepD_to_B, segmentBD);
        
        List<PathStep> nextSteps = graph.getNextSteps(stepA_to_B);
        
        assertEquals(2, nextSteps.size());
        assertTrue(nextSteps.contains(stepB_to_C));
        assertTrue(nextSteps.contains(stepB_to_D));
        assertFalse(nextSteps.contains(stepB_to_A));
    }

    @Test
    void testCircularSegment() {
        RailTrack trackCircle = new RailTrack(); trackCircle.setPosition(new Point(5,5));
        RailNodeImpl circularNode = new RailNodeImpl(trackCircle);
        PathStep exit1 = new PathStepImpl(circularNode, Dir.N);
        PathStep exit2 = new PathStepImpl(circularNode, Dir.S);
        circularNode.addOutStep(exit1);
        circularNode.addOutStep(exit2);
        
        Segment circularSegment = new SegmentImpl("CIRCLE", exit1, exit2);
        graph.registerSegment(exit1, circularSegment);
        graph.registerSegment(exit2, circularSegment);
        
        List<PathStep> nextSteps = graph.getNextSteps(exit1);
        assertTrue(nextSteps.isEmpty());
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
        RailTrack tIso1 = new RailTrack(); tIso1.setPosition(new Point(10,10));
        RailTrack tIso2 = new RailTrack(); tIso2.setPosition(new Point(10,11));
        Segment isolatedSegment = new SegmentImpl("ISO", 
            new PathStepImpl(new RailNodeImpl(tIso1), Dir.N),
            new PathStepImpl(new RailNodeImpl(tIso2), Dir.S)
        );
        
        List<Segment> path = graph.findPath(segmentAB, isolatedSegment);
        assertTrue(path.isEmpty());
    }

    @Test
    void testNonExistentPathStep() {
        RailTrack tGhost = new RailTrack(); tGhost.setPosition(new Point(100,100));
        PathStep ghostStep = new PathStepImpl(new RailNodeImpl(tGhost), Dir.NE);
        assertNull(graph.getSegment(ghostStep));
        assertNull(graph.getNextSteps(ghostStep));
    }
}
