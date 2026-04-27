package letrain.core.segments;

import letrain.core.segments.impl.*;
import letrain.map.Dir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        
        // Nodos
        nodeA = new RailNodeImpl(); // Tope
        nodeB = new RailNodeImpl(); // Fork (decisión)
        nodeC = new RailNodeImpl(); // Tope
        
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
        segmentAB = new SegmentImpl(stepA_to_B, stepB_to_A);
        segmentBC = new SegmentImpl(stepB_to_C, stepC_to_B);
        
        // Registrar en el grafo
        graph.registerSegment(stepA_to_B, segmentAB);
        graph.registerSegment(stepB_to_A, segmentAB);
        graph.registerSegment(stepB_to_C, segmentBC);
        graph.registerSegment(stepC_to_B, segmentBC);
    }

    @Test
    void testGetNextSteps() {
        // Pregunta 1: De A voy a B. ¿Qué opciones tengo en B?
        List<PathStep> nextSteps = graph.getNextSteps(stepA_to_B);
        
        assertNotNull(nextSteps);
        assertEquals(1, nextSteps.size(), "Debería haber solo 1 paso siguiente (B->C), no B->A porque es de donde venimos");
        assertEquals(stepB_to_C, nextSteps.get(0));
    }

    @Test
    void testEndOfTrackReturnsEmpty() {
        // Pregunta 2: De B voy a C (tope). ¿Qué opciones hay en C?
        List<PathStep> nextSteps = graph.getNextSteps(stepB_to_C);
        
        // El nodo C solo tiene el paso C->B, pero como venimos del segmento BC, se filtra.
        assertTrue(nextSteps.isEmpty(), "En un tope de vía no debería haber más pasos de salida");
    }

    @Test
    void testMultipleExitsFromFork() {
        // Escenario: Un Fork (B) con 3 salidas (A, C, D)
        RailNodeImpl nodeD = new RailNodeImpl();
        PathStep stepB_to_D = new PathStepImpl(nodeB, Dir.S);
        PathStep stepD_to_B = new PathStepImpl(nodeD, Dir.N);
        nodeB.addOutStep(stepB_to_D);
        nodeD.addOutStep(stepD_to_B);
        
        Segment segmentBD = new SegmentImpl(stepB_to_D, stepD_to_B);
        graph.registerSegment(stepB_to_D, segmentBD);
        graph.registerSegment(stepD_to_B, segmentBD);
        
        // Si vengo de A -> B, debería poder ir a C O a D, pero NO volver a A
        List<PathStep> nextSteps = graph.getNextSteps(stepA_to_B);
        
        assertEquals(2, nextSteps.size());
        assertTrue(nextSteps.contains(stepB_to_C));
        assertTrue(nextSteps.contains(stepB_to_D));
        assertFalse(nextSteps.contains(stepB_to_A), "No debería permitir volver al segmento de origen");
    }

    @Test
    void testCircularSegment() {
        // Escenario: Un nodo que se conecta a sí mismo (Bucle)
        RailNodeImpl circularNode = new RailNodeImpl();
        PathStep exit1 = new PathStepImpl(circularNode, Dir.N);
        PathStep exit2 = new PathStepImpl(circularNode, Dir.S);
        circularNode.addOutStep(exit1);
        circularNode.addOutStep(exit2);
        
        Segment circularSegment = new SegmentImpl(exit1, exit2);
        graph.registerSegment(exit1, circularSegment);
        graph.registerSegment(exit2, circularSegment);
        
        // Si entro por el Norte, salgo por el mismo nodo por el Sur. 
        // ¿Qué hay después? Solo el camino de vuelta, que debe ser filtrado.
        List<PathStep> nextSteps = graph.getNextSteps(exit1);
        
        assertNotNull(nextSteps);
        assertTrue(nextSteps.isEmpty(), "En un bucle cerrado sin más salidas, no debería haber pasos siguientes válidos tras el primero");
    }

    @Test
    void testFindPath() {
        // Camino de A a C a través de B
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
        Segment isolatedSegment = new SegmentImpl(
            new PathStepImpl(new RailNodeImpl(), Dir.N),
            new PathStepImpl(new RailNodeImpl(), Dir.S)
        );
        
        List<Segment> path = graph.findPath(segmentAB, isolatedSegment);
        assertTrue(path.isEmpty(), "Si no hay conexión, el camino debe estar vacío");
    }

    @Test
    void testNonExistentPathStep() {
        PathStep ghostStep = new PathStepImpl(new RailNodeImpl(), Dir.NE);
        assertNull(graph.getSegment(ghostStep));
        assertNull(graph.getNextSteps(ghostStep), "Un paso no registrado debe devolver null de forma segura");
    }
}
