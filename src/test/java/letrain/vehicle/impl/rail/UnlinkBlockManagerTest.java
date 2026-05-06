package letrain.vehicle.impl.rail;

import letrain.core.segments.BlockManager;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.core.segments.impl.BlockManagerImpl;
import letrain.mvp.impl.Model;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnlinkBlockManagerTest {
    private Model model;
    private BlockManager blockManager;
    private RailwayGraph graph;
    private RailTrack track1;
    private RailTrack track2;
    private Segment segment1;

    @BeforeEach
    void setUp() {
        model = mock(Model.class);
        blockManager = new BlockManagerImpl();
        graph = mock(RailwayGraph.class);
        
        when(model.getBlockManager()).thenReturn(blockManager);
        when(model.getRailwayGraph()).thenReturn(graph);

        track1 = mock(RailTrack.class);
        track2 = mock(RailTrack.class);
        segment1 = mock(Segment.class);
        
        when(segment1.getId()).thenReturn("S1");
        // BlockManagerImpl.updateForkLocks requires segments to have steps
        letrain.core.segments.PathStep ps1 = mock(letrain.core.segments.PathStep.class);
        letrain.core.segments.PathStep ps2 = mock(letrain.core.segments.PathStep.class);
        letrain.core.segments.RailNode rn1 = mock(letrain.core.segments.RailNode.class);
        letrain.core.segments.RailNode rn2 = mock(letrain.core.segments.RailNode.class);
        when(ps1.getRailNode()).thenReturn(rn1);
        when(ps2.getRailNode()).thenReturn(rn2);
        when(rn1.getTrack()).thenReturn(mock(letrain.track.Track.class));
        when(rn2.getTrack()).thenReturn(mock(letrain.track.Track.class));
        when(segment1.getSteps()).thenReturn(new letrain.utils.Pair<>(ps1, ps2));

        when(graph.getSegment(track1)).thenReturn(segment1);
        when(graph.getSegment(track2)).thenReturn(segment1);
    }

    @Test
    void testUnlinkTriggersRebindAndMaintainsOwnership() {
        Train train = new Train(1);
        train.setModel(model);
        
        Locomotive loco = new Locomotive(101, 'L');
        loco.setTrack(track1);
        Wagon wagon = new Wagon("W");
        wagon.setTrack(track2);
        
        train.pushBack(loco);
        train.pushBack(wagon);
        
        // Initial rebind
        train.rebind();
        
        assertEquals(1, blockManager.getOwners(segment1).size());
        assertTrue(blockManager.getOwners(segment1).contains(train));

        // Prepare Unlink (Back, 1 wagon)
        train.prepareUnlink(false, 1);
        assertEquals(1, train.getNumLinkersToRemove());
        
        // Divide Train
        train.divideTrain(() -> 2);
        
        // Verification:
        // 1. We should have two trains now.
        // 2. Both should own segment1 (Shunting coexistence is allowed since they are stopped).
        // 3. The new train should have the wagon.
        
        assertEquals(1, train.size(), "Original train should have 1 linker (loco)");
        Train newTrain = wagon.getTrain();
        assertNotNull(newTrain);
        assertNotEquals(train, newTrain);
        assertEquals(1, newTrain.size(), "New train should have 1 linker (wagon)");
        
        List<Train> owners = blockManager.getOwners(segment1);
        assertEquals(2, owners.size(), "Segment should be co-owned by both trains after unlink");
        assertTrue(owners.contains(train));
        assertTrue(owners.contains(newTrain));
    }
}
