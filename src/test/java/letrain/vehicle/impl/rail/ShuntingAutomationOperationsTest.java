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
    private Segment segment;
    private Train trainA;
    private Train trainB;

    @BeforeEach
    void setUp() {
        blockManager = new BlockManagerImpl();
        model = Mockito.mock(Model.class);
        when(model.getBlockManager()).thenReturn(blockManager);

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

        // Simulate the link operation: Transfer linker to trainA
        trainA.getLinkers().add(locoB);
        locoB.setTrain(trainA);
        
        // Simulate Train B becoming empty and releasing blocks
        trainB.getLinkers().clear();
        trainB.assignDefaultDirectorLinker();
        if (trainB.getDirectorLinker() == null) {
            model.getBlockManager().releaseAll(trainB);
        }

        // THEN: Train A should be alone in the segment and EXIT Shunting
        assertFalse(trainA.isShuntingMode(), "Train A should have exited Shunting after link");
        assertEquals(1, blockManager.getOwners(segment).size());
        assertTrue(blockManager.getOwners(segment).contains(trainA));
    }

    @Test
    void testUnlinkLocomotiveEntersShunting() {
        // GIVEN: A single train in a segment
        Locomotive loco1 = Mockito.mock(Locomotive.class);
        Locomotive loco2 = Mockito.mock(Locomotive.class);
        trainA.getLinkers().add(loco1);
        trainA.getLinkers().add(loco2);
        trainA.setDirectorLinker(loco1);
        
        blockManager.tryLock(trainA, segment);
        assertFalse(trainA.isShuntingMode(), "Initially not in Shunting");

        // WHEN: Unlink happens, creating Train B
        trainA.prepareUnlink(false, 1);
        trainA.divideTrain(() -> 2); // Creates Train B with loco2

        // THEN: Both trains share the segment and enter Shunting
        // (Note: In this test, we need to manually simulate that the new train also owns the segment
        // since divideTrain doesn't automatically lock blocks, that's done by the safety logic in the next tick)
        blockManager.tryShuntingLock(trainB, segment);

        assertTrue(trainA.isShuntingMode(), "Train A should now be in Shunting");
        assertTrue(trainB.isShuntingMode(), "Train B should now be in Shunting");
    }

    @Test
    void testUnlinkWagonsExitsShunting() {
        // GIVEN: A train with wagons
        Locomotive loco = Mockito.mock(Locomotive.class);
        Wagon wagon = new Wagon("W");
        trainA.getLinkers().add(loco);
        trainA.getLinkers().add(wagon);
        trainA.setDirectorLinker(loco);
        
        blockManager.tryLock(trainA, segment);

        // WHEN: Unlink wagons (not locomotives)
        trainA.prepareUnlink(false, 1);
        trainA.divideTrain(() -> 2); // Wagons are just removed and have null train

        // THEN: The wagons have no train, so Train A is alone in the BlockManager
        assertNull(wagon.getTrain());
        assertFalse(trainA.isShuntingMode(), "Train A should NOT be in Shunting with loose wagons");
        assertEquals(1, blockManager.getOwners(segment).size());
    }
}
