package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.Pair;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.TrainSafetyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrainSafetyManager Reactive Events")
class TrainSafetyManagerTest {

    private Model model;
    private BlockManager blockManager;
    private RailwayGraph graph;

    @BeforeEach
    void setUp() {
        model = mock(Model.class);
        blockManager = mock(BlockManager.class);
        graph = mock(RailwayGraph.class);
        when(model.getBlockManager()).thenReturn(blockManager);
        when(model.getRailwayGraph()).thenReturn(graph);
    }

    @Test
    @DisplayName("should transition currentSegment to nextSegment onForkEntered")
    void forkEnteredTransitionsSegment() {
        Train train = new Train(1);
        train.setModel(model);

        Locomotive loco = new Locomotive(101, 'L');
        RailTrack track = mock(RailTrack.class);
        loco.setTrack(track);
        train.pushBack(loco);
        train.rebind();

        TrainSafetyManager safety = (TrainSafetyManager) train.getSafetyManager();
        Segment current = mock(Segment.class, "current");
        Segment next = mock(Segment.class, "next");

        RailTrack nextTrack = mock(RailTrack.class);
        when(track.getConnected(any())).thenReturn(nextTrack);

        when(graph.getSegment(track)).thenReturn(current);
        when(graph.getSegment(nextTrack)).thenReturn(next);

        when(blockManager.tryLock(train, current)).thenReturn(true);
        when(blockManager.tryLock(train, next)).thenReturn(true);

        safety.acquireInitialLocks();

        assertEquals(current, safety.getCurrentSegment());
        assertEquals(next, safety.getNextSegment());

        ForkRailTrack fork = mock(ForkRailTrack.class);
        safety.onForkEntered(fork);

        assertEquals(next, safety.getCurrentSegment());
    }

    @Test
    @DisplayName("should release abandoned segment onForkExited using Port mapping")
    void forkExitedReleasesAbandonedSegment() {
        Train train = new Train(1);
        train.setModel(model);

        Locomotive loco = new Locomotive(101, 'L');
        RailTrack track = mock(RailTrack.class);
        loco.setTrack(track);
        train.pushBack(loco);
        train.rebind();

        TrainSafetyManager safety = (TrainSafetyManager) train.getSafetyManager();
        Segment current = mock(Segment.class, "current");
        Segment next = mock(Segment.class, "next");

        ForkRailTrack fork = mock(ForkRailTrack.class);
        RailNode node = mock(RailNode.class);

        Port trunkPort = mock(Port.class, "trunk");
        Port aPort = mock(Port.class, "a");
        Port bPort = mock(Port.class, "b");

        when(node.getTrack()).thenReturn(fork);
        when(node.getPorts()).thenReturn(List.of(trunkPort, aPort, bPort));
        when(trunkPort.getNode()).thenReturn(node);
        when(aPort.getNode()).thenReturn(node);
        when(bPort.getNode()).thenReturn(node);

        Port currentOtherPort = mock(Port.class);
        RailNode dummyNode1 = mock(RailNode.class);
        when(currentOtherPort.getNode()).thenReturn(dummyNode1);
        when(current.getPorts()).thenReturn(new Pair<>(currentOtherPort, trunkPort));

        Port nextOtherPort = mock(Port.class);
        RailNode dummyNode2 = mock(RailNode.class);
        when(nextOtherPort.getNode()).thenReturn(dummyNode2);
        when(next.getPorts()).thenReturn(new Pair<>(aPort, nextOtherPort));

        when(graph.getSegment(track)).thenReturn(next);

        when(blockManager.getOwnedSegments(train)).thenReturn(List.of(current, next));

        safety.onForkExited(fork);

        verify(blockManager).release(train, current);
        verify(blockManager, never()).release(train, next);
    }

    @Test
    @DisplayName("should release segment on onSegmentExited when not current or next")
    void segmentExitedReleasesSegment() {
        Train train = new Train(1);
        train.setModel(model);

        Locomotive loco = new Locomotive(101, 'L');
        train.pushBack(loco);
        train.rebind();

        TrainSafetyManager safety = (TrainSafetyManager) train.getSafetyManager();
        Segment oldSeg = mock(Segment.class, "oldSeg");
        Segment currentSeg = mock(Segment.class, "currentSeg");

        when(blockManager.getOwnedSegments(train)).thenReturn(List.of(oldSeg, currentSeg));

        safety.onSegmentExited(oldSeg);
        verify(blockManager).release(train, oldSeg);
    }
}
