package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;
import letrain.itinerary.AutoPilot;
import letrain.map.Dir;
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

    // ------------------------------------------------------------------
    // Issue #633: boundary braking (step 2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("blocked next segment with an unknown boundary falls back to braking now (#633)")
    void blockedNextSegment_unknownBoundary_brakesNow() {
        Train train = new Train(1);
        train.setModel(model);

        Locomotive loco = new Locomotive(101, 'L');
        RailTrack track = mock(RailTrack.class);
        loco.setTrack(track);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        train.rebind();

        AutoPilot autopilot = mock(AutoPilot.class);
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.FOLLOWING);
        when(autopilot.currentRoute()).thenReturn(List.of());
        train.setAutopilot(autopilot);

        TrainSafetyManager safety = (TrainSafetyManager) train.getSafetyManager();
        Segment current = mock(Segment.class, "current");
        Segment next = mock(Segment.class, "next");
        RailTrack nextTrack = mock(RailTrack.class);
        when(track.getConnected(any())).thenReturn(nextTrack);
        when(graph.getSegment(track)).thenReturn(current);
        when(graph.getSegment(nextTrack)).thenReturn(next);
        when(blockManager.tryLock(train, current)).thenReturn(true);
        when(blockManager.tryLock(train, next)).thenReturn(false);

        loco.setCurrentSpeed(2);
        loco.setTargetSpeed(2);
        safety.acquireInitialLocks();

        assertTrue(safety.isWaitingForBlock(), "the train must wait for the block");
        assertEquals(0, loco.getTargetSpeed(),
                "an unknown boundary (the head cannot walk) must brake immediately");
    }

    @Test
    @DisplayName("blocked next segment schedules the brake when the boundary is reachable (#633)")
    void blockedNextSegment_schedulesBrakeAtBoundary() {
        Train train = new Train(1);
        train.setModel(model);

        Locomotive loco = new Locomotive(102, 'M');
        RailTrack headTrack = mock(RailTrack.class);
        loco.setTrack(headTrack);
        loco.setDir(Dir.E);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        train.rebind();

        AutoPilot autopilot = mock(AutoPilot.class);
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.FOLLOWING);
        when(autopilot.currentRoute()).thenReturn(List.of());
        train.setAutopilot(autopilot);

        TrainSafetyManager safety = (TrainSafetyManager) train.getSafetyManager();
        Segment current = mock(Segment.class, "current");
        Segment next = mock(Segment.class, "next");
        RailTrack m1 = mock(RailTrack.class);
        RailTrack m2 = mock(RailTrack.class);
        RailTrack m3 = mock(RailTrack.class);
        RailTrack m4 = mock(RailTrack.class);
        RailTrack out = mock(RailTrack.class);
        when(headTrack.getConnected(any())).thenReturn(m1);
        when(m1.getConnected(any())).thenReturn(m2);
        when(m2.getConnected(any())).thenReturn(m3);
        when(m3.getConnected(any())).thenReturn(m4);
        when(m4.getConnected(any())).thenReturn(out);
        when(graph.getSegment(headTrack)).thenReturn(current);
        when(graph.getSegment(out)).thenReturn(next);
        when(graph.containsTrack(current, m1)).thenReturn(true);
        when(graph.containsTrack(current, m2)).thenReturn(true);
        when(graph.containsTrack(current, m3)).thenReturn(true);
        when(graph.containsTrack(current, m4)).thenReturn(true);
        when(graph.containsTrack(current, out)).thenReturn(false);
        when(blockManager.tryLock(train, current)).thenReturn(true);
        when(blockManager.tryLock(train, next)).thenReturn(false);

        loco.setCurrentSpeed(1);
        loco.setTargetSpeed(1);
        safety.acquireInitialLocks();

        // Boundary = 4 rails inside (m1..m4), 1 braking rail at speed 1: the counter starts at 3
        // and the advance that reaches 0 is already the first braking move, so the train halts on
        // the 3rd advance (m3, the last rail before the boundary node).
        assertTrue(safety.isWaitingForBlock());
        assertEquals(1, loco.getTargetSpeed(), "it must keep rolling to the boundary");

        train.advanceSimulationTick();
        safety.onRailAdvanced();
        assertEquals(1, loco.getTargetSpeed(), "still rolling before the braking point");

        train.advanceSimulationTick();
        safety.onRailAdvanced();
        assertEquals(1, loco.getTargetSpeed(), "still rolling before the braking point");

        train.advanceSimulationTick();
        safety.onRailAdvanced();
        assertEquals(0, loco.getTargetSpeed(), "the brake must engage at the scheduled rail");
    }
}
