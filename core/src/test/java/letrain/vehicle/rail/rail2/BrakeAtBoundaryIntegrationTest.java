package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #633, step 2: a train that cannot lock its next block must roll to the end of its current
 * segment before braking, so the maximum train length stays inside the siding (and the main line is
 * clear). No artificial wall: if it cannot stop in time it crosses and the existing invasion/crash
 * handling plays out.
 *
 * <p>
 * Layout (x from -6 to 6; y grows south):
 *
 * <pre>
 *   approach -6..1   f1   tMain   f2   tB1  tB(B)
 *                           s1  s2  s3
 * </pre>
 *
 * Segment S0 = approach..f1 (station A on the head's start rail), S2 = the siding (f1..s1..s3..f2),
 * S3 = f2..tB. Trains are placed with the head at x=-4 so that a speed-3 command reaches exactly
 * speed 3 when the head lands on f1 (6 acceleration rails).
 */
@DisplayName("Brake at the segment boundary (issue #633, step 2)")
class BrakeAtBoundaryIntegrationTest {

    private Model model;
    private World world;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        buildWorld();
    }

    private static final class World {
        /** Straight rails x = -6..1; index 0 is x = -6. */
        List<RailTrack> approach;
        RailTrack tMain;
        RailTrack s1;
        RailTrack s2;
        RailTrack s3;
        RailTrack tB1;
        RailTrack tB;
        ForkRailTrack f1;
        ForkRailTrack f2;
        Station a;
        Station b;

        RailTrack headStart() {
            return approach.get(2); // x = -4
        }

        RailTrack second() {
            return approach.get(1); // x = -5
        }

        RailTrack third() {
            return approach.get(0); // x = -6
        }
    }

    private void buildWorld() {
        world = new World();
        world.approach = new ArrayList<>();
        for (int x = -6; x <= 1; x++) {
            world.approach.add(track(x, 0, Dir.E, Dir.W));
        }
        world.f1 = fork1(2, 0);
        world.tMain = track(3, 0, Dir.E, Dir.W);
        world.s1 = track(2, 1, Dir.N, Dir.E);
        world.s2 = track(3, 1, Dir.W, Dir.E);
        world.s3 = track(4, 1, Dir.W, Dir.N);
        world.f2 = fork2(4, 0);
        world.tB1 = track(5, 0, Dir.E, Dir.W);
        world.tB = track(6, 0, Dir.E, Dir.W);

        for (int i = 0; i + 1 < world.approach.size(); i++) {
            connect(world.approach.get(i), world.approach.get(i + 1));
        }
        connect(world.approach.get(7), world.f1);
        connect(world.f1, world.tMain);
        connect(world.tMain, world.f2);
        world.s1.connect(Dir.N, world.f1);
        world.f1.connect(Dir.S, world.s1);
        connect(world.s1, world.s2);
        connect(world.s2, world.s3);
        world.s3.connect(Dir.N, world.f2);
        world.f2.connect(Dir.S, world.s3);
        connect(world.f2, world.tB1);
        connect(world.tB1, world.tB);

        world.a = station(world.headStart(), "A");
        world.b = station(world.tB, "B");
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("long train at low speed stops on the last siding rail before the blocked fork")
    void longTrain_stopsBeforeTheBlockedFork() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        park(placeTrain(3, List.of(world.tB), List.of(true)));
        Train subject = placeTrain(1, List.of(world.headStart(), world.second(), world.third()),
                List.of(true, false, false));
        programSubject(subject, 1);

        runUntil(() -> subject.getSafetyManager().isWaitingForBlock() && subject.getSpeed() == 0,
                4000);

        assertSame(world.s3, subject.getPhysicalFront().getTrack(),
                "the head must stop on the last siding rail before the boundary fork");
        assertEquals(0, subject.getSpeed());
        assertTrue(subject.isAutoMode(), "no invasion: the autopilot must stay on");
        assertFalse(subject.isPendingManualMode());
        assertSame(siding(), subject.getSafetyManager().getCurrentSegment(),
                "the train must not have crossed into the next segment");

        // One more advance would land on the boundary fork (shared by the segment); the next one
        // would leave. The train stops one rail earlier on purpose.
        assertEquals(OptionalInt.of(1), subject.getSafetyManager().railsToBoundary());

        Linker tail = subject.getPhysicalRear();
        assertTrue(model.getRailwayGraph().containsTrack(siding(), (RailTrack) tail.getTrack()),
                "the tail must be inside the siding, clear of the main line");
    }

    @Test
    @DisplayName("a train that cannot stop in time crosses into the block (no artificial wall)")
    void cannotStopInTime_crossesIntoTheBlock() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        park(placeTrain(3, List.of(world.tB), List.of(true)));
        // 7 acceleration rails before f1: the train arrives at speed 3, so B=4 <= brakingRails(3)=6
        // and the "no time" frontier brakes immediately (se joda) instead of planning.
        relocateStationA(world.approach.get(1));
        Train subject = placeTrain(1, List.of(world.approach.get(1), world.approach.get(0)),
                List.of(true, false));
        programSubject(subject, 3);

        boolean[] stoppedOnSiding = {false};
        runUntil(() -> {
            if (subject.getPhysicalFront().getTrack() == world.s3 && subject.getSpeed() == 0) {
                stoppedOnSiding[0] = true;
            }
            return subject.getPhysicalFront().getTrack() == world.tB1;
        }, 4000);

        assertSame(world.tB1, subject.getPhysicalFront().getTrack(),
                "the train must have crossed the boundary (no wall)");
        assertSame(east(), subject.getSafetyManager().getCurrentSegment());
        assertFalse(stoppedOnSiding[0], "it must not have stopped in the siding");
    }

    @Test
    @DisplayName("accelerating entry + blocker at the next block entry: curve caps and no contact")
    void acceleratingEntry_blockerAtNextEntry_noContact() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        Train blockerEast = park(placeTrain(3, List.of(world.tB1), List.of(true)));
        // One rail before f1: the head enters the siding at speed 1 still accelerating to 2.
        relocateStationA(world.approach.get(7));
        Train subject = placeTrain(1,
                List.of(world.approach.get(7), world.approach.get(6), world.approach.get(5)),
                List.of(true, false, false));
        programSubject(subject, 2);

        runUntil(() -> subject.getSafetyManager().isWaitingForBlock() && subject.getSpeed() == 0,
                4000);

        assertSame(world.s3, subject.getPhysicalFront().getTrack(),
                "the curve must stop the accelerating train on the last siding rail");
        assertEquals(0, subject.getSpeed());
        assertEquals(OptionalInt.of(1), subject.getSafetyManager().railsToBoundary());
        assertFalse(subject.isStalled(), "no contact with the blocker parked at the next entry");
        assertSame(world.tB1, blockerEast.getPhysicalFront().getTrack(),
                "the parked blocker must not be moved");
        assertTrue(subject.isAutoMode(), "no invasion: the autopilot must stay on");
    }

    @Test
    @DisplayName("block released mid-curve restores the desired speed and keeps going")
    void blockReleasedMidCurve_restoresDesiredSpeed() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        Train blockerEast = park(placeTrain(3, List.of(world.tB), List.of(true)));
        relocateStationA(world.approach.get(7));
        Train subject = placeTrain(1,
                List.of(world.approach.get(7), world.approach.get(6), world.approach.get(5)),
                List.of(true, false, false));
        programSubject(subject, 2);

        runUntil(() -> subject.getPhysicalFront().getTrack() == world.s1, 2000);
        assertTrue(subject.getSafetyManager().isWaitingForBlock());
        assertEquals(1, ((Locomotive) subject.getDirectorLinker()).getTargetSpeed(),
                "the braking curve must have capped the target while rolling");

        model.getBlockManager().release(blockerEast, east());

        assertFalse(subject.getSafetyManager().isWaitingForBlock(), "the release must wake it up");
        assertEquals(2, ((Locomotive) subject.getDirectorLinker()).getTargetSpeed(),
                "the release must restore the desired speed, not the capped one");

        runUntil(() -> subject.getPhysicalFront().getTrack() == world.tB1, 2000);
        assertSame(world.tB1, subject.getPhysicalFront().getTrack(),
                "the train must keep going after the release");
    }

    @Test
    @DisplayName("if the block frees while rolling, the train does not brake (no stop-and-go)")
    void blockReleasedWhileRolling_keepsGoingWithoutBraking() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        Train blockerEast = park(placeTrain(3, List.of(world.tB), List.of(true)));
        Train subject = placeTrain(1, List.of(world.headStart(), world.second(), world.third()),
                List.of(true, false, false));
        programSubject(subject, 1);

        runUntil(() -> subject.getPhysicalFront().getTrack() == world.s1, 2000);
        assertTrue(subject.getSafetyManager().isWaitingForBlock(),
                "the plan must be pending when the train enters the siding");
        assertEquals(1, ((Locomotive) subject.getDirectorLinker()).getTargetSpeed(),
                "the plan must not have braked yet");

        model.getBlockManager().release(blockerEast, east());

        assertFalse(subject.getSafetyManager().isWaitingForBlock(), "the release must wake it up");
        assertEquals(1, ((Locomotive) subject.getDirectorLinker()).getTargetSpeed(),
                "a pending plan must not touch the speed");

        boolean[] stoppedEarly = {false};
        runUntil(() -> {
            if (subject.getPhysicalFront().getTrack() != world.tB1 && subject.getSpeed() == 0) {
                stoppedEarly[0] = true;
            }
            return subject.getPhysicalFront().getTrack() == world.tB1;
        }, 2000);

        assertSame(world.tB1, subject.getPhysicalFront().getTrack(),
                "the train must keep going after the release");
        assertFalse(stoppedEarly[0], "no stop-and-go: it must not stop on the way");
    }

    @Test
    @DisplayName("push-pull (two locomotives) counts one rail per advance and stops on the same rail")
    void pushPull_countsOneRailPerAdvance() {
        park(placeTrain(2, List.of(world.tMain), List.of(true)));
        park(placeTrain(3, List.of(world.tB), List.of(true)));
        // Head locomotive + wagon + rear locomotive (push-pull); only the director advances.
        Train subject = placeTrain(1, List.of(world.headStart(), world.second(), world.third()),
                List.of(true, false, true));
        programSubject(subject, 1);

        runUntil(() -> subject.getSafetyManager().isWaitingForBlock() && subject.getSpeed() == 0,
                4000);

        assertSame(world.s3, subject.getPhysicalFront().getTrack(),
                "a double count would brake one rail earlier; both trains must stop on the same rail");
        assertEquals(0, subject.getSpeed());
        assertTrue(subject.isAutoMode());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void programSubject(Train subject, int speed) {
        // The DSL trainRef is the locomotive id (CommandManager.resolveTrain).
        int locoId = ((Locomotive) subject.getDirectorLinker()).getId();
        List<String> errors = model.setProgram("""
                create itinerary "Ruta" {
                    add station %d
                    add station %d
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                train %d set speed %d;
                """.formatted(world.a.getId(), world.b.getId(), locoId, locoId, locoId, speed));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
    }

    /** Parks a train (engine off) and claims its segment so it blocks the path. */
    private Train park(Train train) {
        ((Locomotive) train.getDirectorLinker()).setEngineOn(false);
        train.getSafetyManager().claimOccupiedSegments();
        return train;
    }

    /**
     * Builds a contiguous train: {@code tracks.get(0)} is the head; {@code locoFlags} marks which
     * positions carry a locomotive (the rest are wagons).
     */
    private Train placeTrain(int id, List<RailTrack> tracks, List<Boolean> locoFlags) {
        Train train = new Train(id);
        train.setModel(model);
        List<Linker> linkers = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            if (locoFlags.get(i)) {
                Locomotive loco = new Locomotive(model.nextLocomotiveId(), "L" + i, "RED");
                loco.setEngineOn(true);
                linkers.add(loco);
                model.addLocomotive(loco);
            } else {
                Wagon wagon = new Wagon("w" + i);
                linkers.add(wagon);
                model.addWagon(wagon);
            }
        }
        for (Linker linker : linkers) {
            train.pushBack(linker);
        }
        train.setDirectorLinker((Tractor) linkers.get(0));
        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).enterLinkerFromDir(Dir.W, linkers.get(i));
        }
        if (tracks.get(0).getComponent() instanceof Station station) {
            train.setStationId(station.getId());
        }
        return train;
    }

    private RailTrack track(int x, int y, Dir from, Dir to) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(from, to);
        track.addRoute(to, from);
        model.getRailMap().addTrack(new Point(x, y), track);
        return track;
    }

    /** West fork: straight (W-E) is normal, the siding branch (W-S) is the alternative. */
    private ForkRailTrack fork1(int x, int y) {
        ForkRailTrack fork = newFork(x, y);
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.S);
        fork.addRoute(Dir.S, Dir.W);
        fork.setNormalRoute();
        return fork;
    }

    /** East fork: both the main (W-E) and the siding (S-E) reach the exit; normal keeps S->E. */
    private ForkRailTrack fork2(int x, int y) {
        ForkRailTrack fork = newFork(x, y);
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.S, Dir.E);
        fork.addRoute(Dir.E, Dir.S);
        fork.setNormalRoute();
        return fork;
    }

    private ForkRailTrack newFork(int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        model.getRailMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        return fork;
    }

    private void connect(Track from, Track to) {
        from.connect(Dir.E, to);
        to.connect(Dir.W, from);
    }

    private Station station(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    /** Moves station A to another rail (tests with a different starting position for the head). */
    private void relocateStationA(RailTrack newTrack) {
        world.headStart().setComponent(null);
        newTrack.setComponent(world.a);
        world.a.setTrack(newTrack);
    }

    private Segment segmentOf(RailTrack uniqueTrack) {
        return model.getRailwayGraph().getSegment(uniqueTrack);
    }

    private Segment siding() {
        return segmentOf(world.s2);
    }

    private Segment east() {
        return segmentOf(world.tB1);
    }

    private void runUntil(BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            model.moveLocomotives();
            model.loadAndUnloadTrains();
        }
    }
}
