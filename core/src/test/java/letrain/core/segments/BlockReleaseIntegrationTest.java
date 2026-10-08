package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.itinerary.AutoPilot;
import letrain.itinerary.impl.AutoPilotImpl;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BlockReleaseIntegrationTest {
    @Test
    void testSegmentReleasedOnTrainDestruction() {
        Model model = new Model();
        model.setMode(letrain.mvp.Model.GameMode.RAILS);

        // Create a simple track: (0,0) - (1,0) - (2,0)
        RailTrack t1 = new RailTrack();
        RailTrack t2 = new RailTrack();
        RailTrack t3 = new RailTrack();

        t1.setPosition(new Point(0, 0));
        t2.setPosition(new Point(1, 0));
        t3.setPosition(new Point(2, 0));

        t1.addRoute(Dir.W, Dir.E);
        t2.addRoute(Dir.W, Dir.E);
        t3.addRoute(Dir.W, Dir.E);

        t1.connect(Dir.E, t2);
        t2.connect(Dir.W, t1);
        t2.connect(Dir.E, t3);
        t3.connect(Dir.W, t2);

        model.getRailMap().addTrack(t1.getPosition(), t1);
        model.getRailMap().addTrack(t2.getPosition(), t2);
        model.getRailMap().addTrack(t3.getPosition(), t3);

        // Trigger segment discovery
        model.setMode(letrain.mvp.Model.GameMode.DRIVE);

        // Create a train
        Train train = new Train(model.nextTrainId());
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), 'L');
        loco.setTrain(train);
        train.pushBack(loco);
        model.addLocomotive(loco);

        loco.setTrack(t2);
        t2.setLinker(loco);

        train.setModel(model);

        // Force rebind to claim segments
        train.rebind();

        BlockManager bm = model.getBlockManager();
        List<Segment> owned = bm.getOwnedSegments(train);
        assertFalse(owned.isEmpty(), "Train should own at least one segment");
        Segment segment = owned.get(0);

        // Verify segment is owned
        assertTrue(bm.getOwners(segment).contains(train));

        // Simulate crash/destruction
        loco.destroy();

        // Skip 200 ticks (MAX_DESTROY_TURNS in Locomotive)
        for (int i = 0; i < 201; i++) {
            loco.updateDestroyTimer();
            model.removeDestroyedTrains();
        }

        // Verify loco is removed from model
        assertFalse(model.getLocomotives().contains(loco));

        // THE FIX: The segment should be released.
        assertFalse(bm.getOwners(segment).contains(train),
                "Segment should be released after train destruction");
    }

    // ------------------------------------------------------------------
    // Issue #624: FIFO block arbitration (arrival order, not creation order)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("first waiter wins the freed block regardless of locomotive age (#624)")
    void firstWaiterWinsTheBlock_regardlessOfLocomotiveAge() {
        WaitRace race = buildWaitRace();
        BlockManager bm = race.model().getBlockManager();

        // The first waiter owns the youngest locomotive: the old creation-order arbitration would
        // have handed the block to the second waiter.
        assertTrue(directorId(race.firstWaiter()) > directorId(race.secondWaiter()),
                "the fixture must make the first waiter the youngest locomotive");

        bm.release(race.blocker(), race.contested());

        assertTrue(bm.getOwners(race.contested()).contains(race.firstWaiter()),
                "the train that arrived first must take the freed canton");
        assertFalse(bm.getOwners(race.contested()).contains(race.secondWaiter()),
                "the latecomer must not steal the canton");
        assertFalse(race.firstWaiter().getSafetyManager().isWaitingForBlock(),
                "the winner stops waiting");
        assertTrue(race.secondWaiter().getSafetyManager().isWaitingForBlock(),
                "the loser keeps waiting");
        assertTrue(bm.getWaitTurn(race.firstWaiter()).isEmpty(), "the winner's turn is cleared");
        assertTrue(bm.getWaitTurn(race.secondWaiter()).isPresent(),
                "the loser keeps its turn for the next release");

        // Freeing the canton again lets the second waiter take it: no starvation, no deadlock.
        bm.release(race.firstWaiter(), race.contested());

        assertTrue(bm.getOwners(race.contested()).contains(race.secondWaiter()),
                "the second waiter takes the canton on the next release");
        assertFalse(race.secondWaiter().getSafetyManager().isWaitingForBlock());
    }

    @Test
    @DisplayName("the FIFO arbitration is deterministic across fresh worlds (#624)")
    void waitRace_isDeterministicAcrossRuns() {
        for (int run = 0; run < 3; run++) {
            WaitRace race = buildWaitRace();
            race.model().getBlockManager().release(race.blocker(), race.contested());

            assertTrue(race.model().getBlockManager().getOwners(race.contested())
                    .contains(race.firstWaiter()), "run " + run + ": the same waiter must win");
            assertFalse(race.model().getBlockManager().getOwners(race.contested())
                    .contains(race.secondWaiter()), "run " + run + ": the order must not change");
        }
    }

    private record WaitRace(Model model, Train firstWaiter, Train secondWaiter, Train blocker,
            Segment contested) {
    }

    /**
     * Single-track layout with a third train parked in the middle canton:
     *
     * <pre>
     *   west (x 0..7)   f1(8)   contested (x 9..16)   f2(17)   east (x 18..21)
     * </pre>
     *
     * Two trains wait for the contested canton: the first waiter (heading east from the west line)
     * is created last, so it is the youngest locomotive; the second waiter (heading west from the
     * east line) was created first. Arrival order is enforced by requesting the locks in that
     * order.
     */
    private WaitRace buildWaitRace() {
        Model m = new Model(1);
        m.postLoadInit();

        List<RailTrack> west = line(m, 0, 8, 0);
        ForkRailTrack westJunction = fork(m, 8, 0, Dir.W, Dir.E);
        List<RailTrack> contestedLine = line(m, 9, 8, 0);
        ForkRailTrack eastJunction = fork(m, 17, 0, Dir.E, Dir.W);
        List<RailTrack> east = line(m, 18, 4, 0);

        connect(west.get(7), Dir.E, westJunction, Dir.W);
        connect(westJunction, Dir.E, contestedLine.get(0), Dir.W);
        connect(contestedLine.get(7), Dir.E, eastJunction, Dir.W);
        connect(eastJunction, Dir.E, east.get(0), Dir.W);

        // A parked third train owns the contested canton until the release under test.
        Train blocker = parkedTrain(m, contestedLine.get(3));
        Segment contested = m.getRailwayGraph().getSegment(contestedLine.get(3));
        assertNotNull(contested, "the contested canton must exist");
        assertEquals(List.of(blocker), m.getBlockManager().getOwners(contested),
                "the blocker must be the only owner before the race");

        Train secondWaiter = idleAutoTrain(m, east.get(2), Dir.E);
        Train firstWaiter = idleAutoTrain(m, west.get(3), Dir.W);

        waitForNextBlock(firstWaiter);
        waitForNextBlock(secondWaiter);

        assertTrue(firstWaiter.getSafetyManager().isWaitingForBlock(),
                "the first waiter must wait");
        assertTrue(secondWaiter.getSafetyManager().isWaitingForBlock(),
                "the second waiter must wait");
        assertTrue(
                m.getBlockManager().getWaitTurn(firstWaiter).orElseThrow() < m.getBlockManager()
                        .getWaitTurn(secondWaiter).orElseThrow(),
                "the first waiter must hold the earlier turn");

        return new WaitRace(m, firstWaiter, secondWaiter, blocker, contested);
    }

    /**
     * Starts the automatic lock of the way ahead; the blocked next canton leaves the train waiting.
     */
    private void waitForNextBlock(Train train) {
        Locomotive loco = (Locomotive) train.getDirectorLinker();
        loco.setCurrentSpeed(0);
        loco.setTargetSpeed(1);
        train.getSafetyManager().acquireInitialLocks();
    }

    private int directorId(Train train) {
        return ((Locomotive) train.getDirectorLinker()).getId();
    }

    private Train idleAutoTrain(Model m, RailTrack start, Dir entryFrom) {
        Locomotive loco = new Locomotive(m.nextLocomotiveId(), "L");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(m.nextTrainId());
        train.setModel(m);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        m.addLocomotive(loco);
        ((AutoPilotImpl) train.getAutopilot()).setMode(AutoPilot.Mode.FOLLOWING);
        start.enterLinkerFromDir(entryFrom, loco);
        return train;
    }

    /** A parked train (engine off) that owns its segment, blocking it for other trains. */
    private Train parkedTrain(Model m, RailTrack rail) {
        Locomotive loco = new Locomotive(m.nextLocomotiveId(), "B");
        loco.setEngineOn(false);
        Train train = new Train(m.nextTrainId());
        train.setModel(m);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        m.addLocomotive(loco);
        rail.enterLinkerFromDir(Dir.W, loco);
        train.getSafetyManager().claimOccupiedSegments();
        return train;
    }

    private RailTrack track(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        return track;
    }

    private List<RailTrack> line(Model m, int x0, int count, int y) {
        List<RailTrack> rails = new ArrayList<>();
        RailMap railMap = m.getRailMap();
        for (int i = 0; i < count; i++) {
            RailTrack rail = track(x0 + i, y);
            rails.add(rail);
            railMap.addTrack(new Point(x0 + i, y), rail);
        }
        for (int i = 0; i + 1 < rails.size(); i++) {
            connect(rails.get(i), Dir.E, rails.get(i + 1), Dir.W);
        }
        return rails;
    }

    private ForkRailTrack fork(Model m, int x, int y, Dir in, Dir out) {
        ForkRailTrack fork = new ForkRailTrack(m.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.addRoute(in, out);
        m.getRailMap().addTrack(new Point(x, y), fork);
        m.addFork(fork);
        return fork;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }
}
