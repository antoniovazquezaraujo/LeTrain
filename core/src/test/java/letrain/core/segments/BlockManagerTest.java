package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import letrain.segments.BlockManager;
import letrain.segments.Port;
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

    @Test
    void waitTurnsAreMonotonicAndStableWhileWaiting() {
        long first = blockManager.requestWaitTurn(trainA);
        long second = blockManager.requestWaitTurn(trainB);

        assertTrue(second > first, "turns must be assigned in request order");
        assertEquals(first, blockManager.requestWaitTurn(trainA),
                "a train that is already waiting keeps its turn");
        assertEquals(first, blockManager.getWaitTurn(trainA).orElseThrow());
        assertEquals(second, blockManager.getWaitTurn(trainB).orElseThrow());
    }

    @Test
    void clearWaitTurnRemovesTheTrainWithoutReusingTurns() {
        long first = blockManager.requestWaitTurn(trainA);
        blockManager.clearWaitTurn(trainA);
        assertTrue(blockManager.getWaitTurn(trainA).isEmpty(), "the turn must be gone");

        long second = blockManager.requestWaitTurn(trainB);
        long third = blockManager.requestWaitTurn(trainA);

        assertTrue(second > first, "the counter must not go backwards");
        assertTrue(third > second, "a new wait of the same train goes to the back of the queue");
    }

    @Test
    void releaseAllAlsoClearsTheWaitTurn() {
        blockManager.requestWaitTurn(trainA);

        blockManager.releaseAll(trainA);

        assertTrue(blockManager.getWaitTurn(trainA).isEmpty(),
                "a train that released everything is no longer queued");
    }

    @Test
    void tabulaRasaClearsWaitTurnsAndRestartsTheCounter() {
        blockManager.requestWaitTurn(trainA);

        blockManager.clearAll();

        assertTrue(blockManager.getWaitTurn(trainA).isEmpty());
        assertEquals(0, blockManager.requestWaitTurn(trainB),
                "tabula rasa restarts the deterministic turn counter");
    }
}
