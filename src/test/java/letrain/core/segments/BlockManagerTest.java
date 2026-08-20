package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import letrain.segments.BlockManager;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.Segment;
import letrain.segments.impl.BlockManagerImpl;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BlockManagerTest {
    private BlockManager blockManager;
    private Segment segment;
    private Train trainA;
    private Train trainB;

    @BeforeEach
    void setUp() {
        blockManager = new BlockManagerImpl();
        segment = Mockito.mock(Segment.class);
        when(segment.getId()).thenReturn("S1");
        
        Port p1 = Mockito.mock(Port.class);
        Port p2 = Mockito.mock(Port.class);
        
        when(segment.getPorts()).thenReturn(new letrain.utils.Pair<>(p1, p2));
        
        trainA = Mockito.mock(Train.class);
        trainB = Mockito.mock(Train.class);
    }

    @Test
    void testNormalExclusion() {
        // Tren A bloquea el segmento
        assertTrue(blockManager.tryLock(trainA, segment));
        
        // Tren B (Normal) intenta bloquear y falla
        assertFalse(blockManager.tryLock(trainB, segment));
        
        assertEquals(1, blockManager.getOwners(segment).size());
        assertTrue(blockManager.getOwners(segment).contains(trainA));
    }


    @Test
    void testTabulaRasa() {
        blockManager.tryLock(trainA, segment);
        blockManager.clearAll();
        
        // Tras Tabula Rasa, el segmento está libre
        assertTrue(blockManager.getOwners(segment).isEmpty());
        assertTrue(blockManager.tryLock(trainB, segment));
    }
}
