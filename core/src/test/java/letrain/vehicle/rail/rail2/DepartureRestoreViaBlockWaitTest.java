package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.itinerary.AutoPilot;
import letrain.itinerary.impl.AutoPilotImpl;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #650: the speed deferred by a block wait must be restored when the wait is resolved by any
 * path, including {@code acquireInitialLocks} locking the parallel alternative segment. Also, a
 * reverse ordered while moving with target 0 must not clobber a speed restored before the train
 * finished stopping.
 */
@DisplayName("Issue #650: departure restore through a transient block wait")
class DepartureRestoreViaBlockWaitTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Alternative segment resolves the wait
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("locking the alternative segment restores the deferred speed")
    void alternativeSegmentLocks_restoresTheDeferredSpeed() {
        // Main line (blocked) plus a parallel bypass between the two junctions.
        List<RailTrack> westTail = line(-10, 8, 0);
        List<RailTrack> main = line(0, 8, 0);
        List<RailTrack> bypass = line(0, 8, 1);
        ForkRailTrack westJunction = fork(-1, 0, Dir.W, Dir.E);
        westJunction.addRoute(Dir.W, Dir.S);
        westJunction.setNormalRoute();
        ForkRailTrack eastJunction = fork(8, 0, Dir.E, Dir.W);
        eastJunction.addRoute(Dir.E, Dir.S);
        eastJunction.setNormalRoute();
        RailTrack bypassWest = corner(-1, 1, Dir.E, Dir.N);
        RailTrack bypassEast = corner(8, 1, Dir.N, Dir.W);

        connect(westTail.get(7), Dir.E, westJunction, Dir.W);
        connect(westJunction, Dir.E, main.get(0), Dir.W);
        connect(main.get(7), Dir.E, eastJunction, Dir.W);
        westJunction.connect(Dir.S, bypassWest);
        bypassWest.connect(Dir.N, westJunction);
        connect(bypassWest, Dir.E, bypass.get(0), Dir.W);
        for (int i = 0; i + 1 < bypass.size(); i++) {
            connect(bypass.get(i), Dir.E, bypass.get(i + 1), Dir.W);
        }
        connect(bypass.get(7), Dir.E, bypassEast, Dir.W);
        bypassEast.connect(Dir.N, eastJunction);
        eastJunction.connect(Dir.S, bypassEast);

        Train subject = placeTrain(westTail.get(4), Dir.W);
        // The safety layer needs the plan following to attempt the bypass.
        ((AutoPilotImpl) subject.getAutopilot()).setMode(AutoPilot.Mode.FOLLOWING);
        Locomotive loco = (Locomotive) subject.getDirectorLinker();

        // Both the main and the bypass are occupied: the subject must plan a boundary stop.
        Train mainBlocker = parkedTrain(main.get(3));
        Train bypassBlocker = parkedTrain(bypass.get(3));
        Segment mainSegment = model.getRailwayGraph().getSegment(main.get(0));
        Segment bypassSegment = model.getRailwayGraph().getSegment(bypass.get(0));

        // The train is rolling towards the junction when the blocked canton appears: the wait is
        // planned while it still has a target (a stopped train with target 0 would not lock ahead).
        loco.setCurrentSpeed(1);
        loco.setTargetSpeed(1);
        subject.getSafetyManager().acquireInitialLocks();
        assertSame(mainSegment, subject.getSafetyManager().getNextSegment(),
                "the topological next segment must be the blocked main");
        assertTrue(subject.getSafetyManager().isWaitingForBlock(),
                "the blocked main must set the wait");

        // A departure/order asks for speed while the wait is active: the gate defers it.
        loco.setTargetSpeed(3);
        assertEquals(0, loco.getTargetSpeed(), "the wait gate must defer the requested speed");
        assertEquals(3, subject.getSavedTargetSpeed(), "the deferred speed must be saved");

        // The bypass is freed (not the next segment: onBlockReleased must not fire) and a later
        // acquireInitialLocks resolves the wait by locking the alternative.
        model.getBlockManager().release(bypassBlocker,
                model.getRailwayGraph().getSegment(bypass.get(3)));

        subject.getSafetyManager().acquireInitialLocks();

        assertFalse(subject.getSafetyManager().isWaitingForBlock(),
                "locking the alternative must resolve the wait");
        assertSame(bypassSegment, subject.getSafetyManager().getNextSegment(),
                "the alternative segment must be locked");
        assertEquals(3, loco.getTargetSpeed(),
                "the deferred departure speed must be restored by the alternative lock");
        assertFalse(subject.hasSavedTargetSpeed(), "the restored speed must be consumed");
    }

    // ═══════════════════════════════════════════════════════════════════
    // reverse() while moving with target 0
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("a reverse ordered while braking with target 0 does not clobber a restored speed")
    void reverseWhileMovingWithTargetZero_keepsTheRestoredSpeed() {
        List<RailTrack> rails = line(0, 8, 0);
        Train train = placeTrain(rails.get(3), Dir.W);
        Locomotive loco = (Locomotive) train.getDirectorLinker();

        // The train is rolling with target 0 (braking for a mission stop) when a `reverse` action
        // arrives: it brakes to a stop first and reverses there.
        loco.setCurrentSpeed(2);
        loco.setTargetSpeedDirect(0);
        train.reverse();
        assertTrue(train.isPendingReverse(), "the reversal must be pending until the train stops");

        // The departure releases in the meantime and restores its speed (Train.restoreSpeed).
        train.setSavedTargetSpeed(3);
        train.restoreSpeed();
        assertEquals(3, loco.getTargetSpeed(), "the departure speed must be applied");

        // The train finally stops and the pending reversal completes.
        loco.setCurrentSpeed(0);
        train.notifySpeedChanged(0);

        assertFalse(train.isPendingReverse());
        assertEquals(3, loco.getTargetSpeed(),
                "the completion of the reversal must not clobber the restored departure speed");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fixture
    // ═══════════════════════════════════════════════════════════════════

    private RailMap railMap() {
        return model.getRailMap();
    }

    private RailTrack track(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        railMap().addTrack(new Point(x, y), track);
        return track;
    }

    private RailTrack corner(int x, int y, Dir a, Dir b) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(a, b);
        track.addRoute(b, a);
        railMap().addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack fork(int x, int y, Dir in, Dir out) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.addRoute(in, out);
        railMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        return fork;
    }

    private List<RailTrack> line(int x0, int count, int y) {
        List<RailTrack> rails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rails.add(track(x0 + i, y));
        }
        for (int i = 0; i + 1 < rails.size(); i++) {
            connect(rails.get(i), Dir.E, rails.get(i + 1), Dir.W);
        }
        return rails;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    private Train placeTrain(RailTrack start, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        start.enterLinkerFromDir(entryFrom, loco);
        train.getSafetyManager().acquireInitialLocks();
        return train;
    }

    /** A parked train (engine off) that owns its segment, blocking it for other trains. */
    private Train parkedTrain(RailTrack rail) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "B");
        loco.setEngineOn(false);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        rail.enterLinkerFromDir(Dir.W, loco);
        train.getSafetyManager().claimOccupiedSegments();
        return train;
    }
}
