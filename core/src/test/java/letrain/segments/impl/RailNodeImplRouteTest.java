package letrain.segments.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for #830: a fork node's A/B ports are a fixed physical mapping built when the node is
 * created, while {@code ForkRouter} may relabel original/alternative later. {@code setRoute} must
 * choose by the physical branch direction ({@code getDirForPort}), not blindly by A/B.
 */
@DisplayName("RailNodeImpl.setRoute: physical branch, not A/B")
class RailNodeImplRouteTest {

    @Test
    @DisplayName("setRoute follows the current original/alternative labels after an A/B cross")
    void setRoute_usesPhysicalBranchDirection() {
        ForkRailTrack fork = mock(ForkRailTrack.class);
        // At node creation A=SW (original) and B=S (alternative).
        when(fork.getOriginalRoute()).thenReturn(new Pair<>(Dir.N, Dir.SW));
        when(fork.getAlternativeRoute()).thenReturn(new Pair<>(Dir.N, Dir.S));
        RailNodeImpl node = new RailNodeImpl(fork);

        Port trunk = node.getPortByType(PortType.TRUNK);
        Port portA = node.getPortByType(PortType.A); // physical SW
        Port portB = node.getPortByType(PortType.B); // physical S

        // The router swaps the labels: SW is now the alternative, S the original. A/B ports keep
        // their physical direction, so asking for the SW branch must activate the alternative.
        when(fork.getOriginalRoute()).thenReturn(new Pair<>(Dir.N, Dir.S));
        when(fork.getAlternativeRoute()).thenReturn(new Pair<>(Dir.N, Dir.SW));
        when(fork.isUsingAlternativeRoute()).thenReturn(false);

        assertTrue(node.setRoute(trunk, portA), "SW is now the alternative branch");
        verify(fork).setAlternativeRoute();

        when(fork.isUsingAlternativeRoute()).thenReturn(true);
        assertTrue(node.setRoute(trunk, portB), "S is now the normal branch");
        verify(fork).setNormalRoute();
    }

    @Test
    @DisplayName("isRouteActive follows the physical branch after an A/B cross")
    void isRouteActive_usesPhysicalBranchDirection() {
        ForkRailTrack fork = mock(ForkRailTrack.class);
        when(fork.getOriginalRoute()).thenReturn(new Pair<>(Dir.N, Dir.SW));
        when(fork.getAlternativeRoute()).thenReturn(new Pair<>(Dir.N, Dir.S));
        RailNodeImpl node = new RailNodeImpl(fork);

        Port trunk = node.getPortByType(PortType.TRUNK);
        Port portA = node.getPortByType(PortType.A); // physical SW
        Port portB = node.getPortByType(PortType.B); // physical S

        when(fork.getOriginalRoute()).thenReturn(new Pair<>(Dir.N, Dir.S));
        when(fork.getAlternativeRoute()).thenReturn(new Pair<>(Dir.N, Dir.SW));
        when(fork.isUsingAlternativeRoute()).thenReturn(true);

        assertTrue(node.isRouteActive(trunk, portA), "SW is the active alternative branch");
        assertTrue(!node.isRouteActive(trunk, portB), "S is not active while alternative is set");
    }
}
