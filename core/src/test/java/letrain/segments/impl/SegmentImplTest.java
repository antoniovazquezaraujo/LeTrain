package letrain.segments.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import letrain.segments.Port;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #810: {@link SegmentImpl} is the key of a {@code ConcurrentHashMap} in {@link BlockManagerImpl},
 * so its {@code equals}/{@code hashCode} must honour the map-key contract. Identity is id-only:
 * ports are deliberately not compared because two distinct segments (parallel tracks) can share the
 * same port pair.
 */
@DisplayName("SegmentImpl identity contract (#810)")
class SegmentImplTest {

    private final Port p1 = mock(Port.class);
    private final Port p2 = mock(Port.class);
    private final Port p3 = mock(Port.class);

    @Test
    @DisplayName("equals is reflexive, symmetric and consistent")
    void should_HonourTheEqualsContract() {
        SegmentImpl a = new SegmentImpl("S1", p1, p2);
        SegmentImpl b = new SegmentImpl("S1", p1, p2);

        assertTrue(a.equals(a), "reflexive");
        assertTrue(a.equals(b) && b.equals(a), "symmetric for the same id");
        assertTrue(a.equals(b));
        assertTrue(a.equals(b), "consistent across repeated calls");
        assertEquals(a.hashCode(), b.hashCode(), "equal objects must share the hash");
    }

    @Test
    @DisplayName("equals is transitive across three segments with the same id")
    void should_BeTransitive() {
        SegmentImpl a = new SegmentImpl("S1", p1, p2);
        SegmentImpl b = new SegmentImpl("S1", p2, p3);
        SegmentImpl c = new SegmentImpl("S1", p1, p3);

        assertTrue(a.equals(b));
        assertTrue(b.equals(c));
        assertTrue(a.equals(c), "same id must be transitive even with different ports");
    }

    @Test
    @DisplayName("two different ids sharing the very same ports are NOT equal")
    void should_DistinguishParallelSegmentsWithTheSamePorts() {
        SegmentImpl first = new SegmentImpl("S1", p1, p2);
        SegmentImpl parallel = new SegmentImpl("S2", p1, p2);

        assertNotEquals(first, parallel,
                "parallel tracks between the same nodes are distinct cantons");
        assertNotEquals(parallel, first, "and the inequality is symmetric");
    }

    @Test
    @DisplayName("identity is id-only: the same id equals regardless of its ports")
    void should_IgnorePortsForTheSameId() {
        SegmentImpl a = new SegmentImpl("S1", p1, p2);
        SegmentImpl sameIdOtherPorts = new SegmentImpl("S1", p2, p3);

        assertEquals(a, sameIdOtherPorts);
        assertEquals(a.hashCode(), sameIdOtherPorts.hashCode(),
                "hashCode must follow the id-only equals");
    }

    @Test
    @DisplayName("null and other types are never equal to a segment")
    void should_RejectNullAndOtherTypes() {
        SegmentImpl segment = new SegmentImpl("S1", p1, p2);

        assertFalse(segment.equals(null));
        assertFalse(segment.equals("S1"));
    }
}
