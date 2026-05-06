package letrain.vehicle.impl.rail;

import letrain.core.segments.BlockManager;
import letrain.core.segments.Segment;
import letrain.core.segments.impl.BlockManagerImpl;
import letrain.mvp.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ShuntingAutomationOperationsTest {
    private BlockManager blockManager;
    private Model model;
    private letrain.core.segments.RailwayGraph graph;
    private Segment segment;
    private Train trainA;
    private Train trainB;

    @BeforeEach
    void setUp() {
        blockManager = new BlockManagerImpl();
        model = Mockito.mock(Model.class);
        graph = Mockito.mock(letrain.core.segments.RailwayGraph.class);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(model.getRailwayGraph()).thenReturn(graph);

        segment = Mockito.mock(Segment.class);
        when(segment.getId()).thenReturn("S1");
        
        // Mock getSteps to avoid NPE in updateForkLocks
        letrain.core.segments.PathStep ps1 = Mockito.mock(letrain.core.segments.PathStep.class);
        letrain.core.segments.PathStep ps2 = Mockito.mock(letrain.core.segments.PathStep.class);
        letrain.core.segments.RailNode rn1 = Mockito.mock(letrain.core.segments.RailNode.class);
        letrain.core.segments.RailNode rn2 = Mockito.mock(letrain.core.segments.RailNode.class);
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

    @Test
    void testLinkExitsShunting() {
        // GIVEN: Two trains sharing a segment (Shunting Mode active)
        blockManager.tryLock(trainA, segment);
        blockManager.tryShuntingLock(trainB, segment);

        assertTrue(trainA.isShuntingMode(), "Train A should be in Shunting");
        assertTrue(trainB.isShuntingMode(), "Train B should be in Shunting");

        // WHEN: Train B links into Train A
        Locomotive locoB = new Locomotive(101, "B");
        locoB.setTrain(trainB);
        trainB.getLinkers().add(locoB);
        trainB.setDirectorLinker(locoB);

        // Setup the linking state in Train A
        trainA.getLinkersToJoin().add(locoB);
        try {
            java.lang.reflect.Field f = Train.class.getDeclaredField("numLinkersToJoin");
            f.setAccessible(true);
            f.set(trainA, 1);
            java.lang.reflect.Field f2 = Train.class.getDeclaredField("linkerJoinSense");
            f2.setAccessible(true);
            f2.set(trainA, Train.LinkersSense.BACK);
        } catch (Exception e) {
            fail(e);
        }
        
        // Execute the REAL method
        trainA.joinLinkers();

        // THEN: Train A should be alone in the segment and EXIT Shunting
        assertFalse(trainA.isShuntingMode(), "Train A should have exited Shunting after link");
        assertEquals(1, blockManager.getOwners(segment).size());
        assertTrue(blockManager.getOwners(segment).contains(trainA));
        assertFalse(blockManager.getOwners(segment).contains(trainB), "Train B should no longer own anything");
        assertEquals(0, trainB.getLinkers().size(), "Train B should be empty");
    }

    @Test
    void testUnlinkLocomotiveEntersShunting() {
        // GIVEN: A single train in a segment
        Locomotive loco1 = new Locomotive(101, "L1");
        Locomotive loco2 = new Locomotive(102, "L2");
        letrain.track.rail.RailTrack track1 = Mockito.mock(letrain.track.rail.RailTrack.class);
        letrain.track.rail.RailTrack track2 = Mockito.mock(letrain.track.rail.RailTrack.class);
        loco1.setTrack(track1);
        loco2.setTrack(track2);

        trainA.getLinkers().add(loco1);
        trainA.getLinkers().add(loco2);
        trainA.setDirectorLinker(loco1);
        loco1.setTrain(trainA);
        loco2.setTrain(trainA);
        
        blockManager.tryLock(trainA, segment);
        assertFalse(trainA.isShuntingMode(), "Initially not in Shunting");

        // WHEN: Unlink happens, creating a new Train
        trainA.prepareUnlink(false, 1);
        trainA.divideTrain(() -> 2); 

        // THEN: Both trains share the segment and enter Shunting
        Train newTrain = loco2.getTrain();
        assertNotNull(newTrain, "New train should have been created");
        assertNotEquals(trainA, newTrain);

        assertTrue(trainA.isShuntingMode(), "Train A should now be in Shunting");
        assertTrue(newTrain.isShuntingMode(), "New train should now be in Shunting");
    }

    @Test
    void testUnlinkWagonsExitsShunting() {
        // GIVEN: A train with wagons
        Locomotive loco = Mockito.mock(Locomotive.class);
        letrain.track.rail.RailTrack track1 = Mockito.mock(letrain.track.rail.RailTrack.class);
        when(loco.getTrack()).thenReturn(track1);

        Wagon wagon = new Wagon("W");
        letrain.track.rail.RailTrack track2 = Mockito.mock(letrain.track.rail.RailTrack.class);
        wagon.setTrack(track2);

        trainA.getLinkers().add(loco);
        trainA.getLinkers().add(wagon);
        trainA.setDirectorLinker(loco);
        
        blockManager.tryLock(trainA, segment);

        // WHEN: Unlink wagons (not locomotives)
        trainA.prepareUnlink(false, 1);
        trainA.divideTrain(() -> 2); // Wagons are just removed and have null train

        // THEN: The wagons belong to a new Train (ID 2), and both are in Shunting
        assertNotNull(wagon.getTrain());
        assertEquals(2, wagon.getTrain().getId());
        assertTrue(trainA.isShuntingMode(), "Train A should be in Shunting because Train 2 now co-owns the segment");
        assertEquals(2, blockManager.getOwners(segment).size());
    }
}
