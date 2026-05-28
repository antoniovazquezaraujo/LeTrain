package letrain.vehicle.rail.rail2;

import static org.mockito.Mockito.when;

import java.util.Collections;

import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.Segment;
import letrain.segments.impl.BlockManagerImpl;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

class ShuntingAutomationOperationsTest {
    private BlockManager blockManager;
    private Model model;
    private letrain.segments.RailwayGraph graph;
    private Segment segment;
    private Train trainA;
    private Train trainB;

    @BeforeEach
    void setUp() {
        blockManager = new BlockManagerImpl();
        model = Mockito.mock(Model.class);
        graph = Mockito.mock(letrain.segments.RailwayGraph.class);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(model.getRailwayGraph()).thenReturn(graph);

        segment = Mockito.mock(Segment.class);
        when(segment.getId()).thenReturn("S1");
        
        // Mock getSteps to avoid NPE in updateForkLocks
        letrain.segments.PathStep ps1 = Mockito.mock(letrain.segments.PathStep.class);
        letrain.segments.PathStep ps2 = Mockito.mock(letrain.segments.PathStep.class);
        letrain.segments.RailNode rn1 = Mockito.mock(letrain.segments.RailNode.class);
        letrain.segments.RailNode rn2 = Mockito.mock(letrain.segments.RailNode.class);
        when(ps1.getRailNode()).thenReturn(rn1);
        when(ps2.getRailNode()).thenReturn(rn2);
        when(rn1.getOutSteps()).thenReturn(Collections.emptyList());
        when(rn2.getOutSteps()).thenReturn(Collections.emptyList());
        when(segment.getSteps()).thenReturn(new letrain.utils.Pair<>(ps1, ps2));

        trainA = new Train(1);
        trainA.setModel(model);
        
        trainB = new Train(2);
        trainB.setModel(model);

        // Configure graph to return segment for any RailTrack
        when(graph.getSegment(Mockito.any(letrain.track.rail.RailTrack.class))).thenReturn(segment);
    }

}
