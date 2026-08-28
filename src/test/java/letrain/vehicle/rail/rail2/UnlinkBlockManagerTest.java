package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.impl.BlockManagerImpl;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        letrain.segments.Port p1 = mock(letrain.segments.Port.class);
        letrain.segments.Port p2 = mock(letrain.segments.Port.class);
        letrain.segments.RailNode rn1 = mock(letrain.segments.RailNode.class);
        letrain.segments.RailNode rn2 = mock(letrain.segments.RailNode.class);
        when(p1.getNode()).thenReturn(rn1);
        when(p2.getNode()).thenReturn(rn2);
        when(rn1.getTrack()).thenReturn(mock(letrain.track.Track.class));
        when(rn2.getTrack()).thenReturn(mock(letrain.track.Track.class));
        when(segment1.getPorts()).thenReturn(new letrain.utils.Pair<>(p1, p2));

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

        train.getTrainCouplingManager().prepareUnlink(train, false, 1);
        assertEquals(1, train.getNumLinkersToRemove());

        // Divide Train

        train.getTrainCouplingManager().divideTrain(train, () -> 2);

        // 1. We should have two trains now.
        // 2. The old train should still own segment1.
        // 3. The new train should be stopped and in manual mode, and not own the segment.

        assertEquals(1, train.size(), "Original train should have 1 linker (loco)");
        Train newTrain = wagon.getTrain();
        assertNotNull(newTrain);
        assertNotEquals(train, newTrain);
        assertEquals(1, newTrain.size(), "New train should have 1 linker (wagon)");

        List<Train> owners = blockManager.getOwners(segment1);
        assertEquals(1, owners.size(), "Segment should be owned by original train after unlink");
        assertTrue(owners.contains(train));
        assertFalse(owners.contains(newTrain));
        assertFalse(newTrain.isAutoMode(), "New train should be in manual mode due to block conflict");
    }
}
