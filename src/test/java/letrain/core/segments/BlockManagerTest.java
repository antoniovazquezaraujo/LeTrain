package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import letrain.segments.BlockManager;
import letrain.segments.PathStep;
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
        
        PathStep ps1 = Mockito.mock(PathStep.class);
        PathStep ps2 = Mockito.mock(PathStep.class);
        RailNode rn1 = Mockito.mock(RailNode.class);
        RailNode rn2 = Mockito.mock(RailNode.class);
        
        when(ps1.getRailNode()).thenReturn(rn1);
        when(ps2.getRailNode()).thenReturn(rn2);
        when(rn1.getOutSteps()).thenReturn(Collections.emptyList());
        when(rn2.getOutSteps()).thenReturn(Collections.emptyList());
        
        when(segment.getSteps()).thenReturn(new letrain.utils.Pair<>(ps1, ps2));
        
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
    void testShuntingCoexistence() {
        // Tren A bloquea el segmento (Normal)
        assertTrue(blockManager.tryLock(trainA, segment));
        
        // Tren A está detenido (Velocidad 0 por defecto en mock)
        when(trainA.getSpeed()).thenReturn(0);
        
        // Tren B entra en modo Shunting y tiene éxito
        assertTrue(blockManager.tryShuntingLock(trainB, segment));
        
        List<Train> owners = blockManager.getOwners(segment);
        assertEquals(2, owners.size());
        assertTrue(owners.contains(trainA));
        assertTrue(owners.contains(trainB));
    }

    @Test
    void testShuntingDenialWhenMoving() {
        // Tren A bloquea el segmento
        assertTrue(blockManager.tryLock(trainA, segment));
        
        // Tren A se está moviendo
        when(trainA.getSpeed()).thenReturn(5);
        
        // Tren B intenta entrar en Shunting y FALLA (Regla de Parada Total)
        assertFalse(blockManager.tryShuntingLock(trainB, segment));
        
        assertEquals(1, blockManager.getOwners(segment).size());
    }

    @Test
    void testExitShuntingRule() {
        // Tren A y B comparten segmento
        blockManager.tryLock(trainA, segment);
        blockManager.tryShuntingLock(trainB, segment);
        
        // Tren B no puede salir de modo Shunting porque comparte segmento
        assertFalse(blockManager.canExitShunting(trainB));
        
        // Tren A se marcha (libera segmento)
        blockManager.release(trainA, segment);
        
        // Ahora Tren B está solo, debería poder salir de Shunting
        assertTrue(blockManager.canExitShunting(trainB));
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
