package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #625 (ADR-022 phase 2e): two trains meet on a single track with a siding. The train that
 * cannot lock the next canton takes the free parallel segment (the siding) and rewrites its route;
 * the other train passes through the main line and the meeting resolves without deadlock.
 *
 * <p>
 * Layout (x from -6 to 6; the siding runs parallel one row south):
 *
 * <pre>
 *   approach (A) -6..1   f1   tMain   f2   tB1   tB (B)
 *                              s1  s2  s3
 * </pre>
 */
@DisplayName("Automatic sidings: two trains cross on single track (#625)")
class AutomaticSidingCrossingIntegrationTest {

    private Model model;
    private World world;
    private Train east; // eastbound A -> B, the one that must divert into the siding
    private Train west; // westbound B -> A, the one that passes through the main line

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        buildWorld();
    }

    @Test
    @DisplayName("the blocked train diverts into the siding and the other passes without deadlock")
    void blockedTrainDivertsIntoTheSiding_andTheOtherPasses() {
        List<String> contacts = new ArrayList<>();
        east.addScriptTrainEventListener(contactListener(contacts));
        west.addScriptTrainEventListener(contactListener(contacts));

        // (1) The eastbound train plans its route while the main line is free: no speed is ordered
        // yet, so it only claims its current canton and the planned next segment is the main.
        programEastPlan();
        assertTrue(east.getAutopilot().currentRoute().contains(main()),
                "the free main line must be in the planned route");

        // (2) The westbound train starts and claims its canton plus the single-track main, then is
        // held at B so it does not move until the eastbound train has taken the siding. The
        // eastbound plan is re-applied (a new program resets automation) while the main is still
        // free, and only then is its speed ordered: the planned next segment is now blocked, so the
        // safety layer locks the free parallel siding and rewrites the route.
        programCrossingSetup();

        Segment siding = siding();
        assertSame(siding, east.getSafetyManager().getNextSegment(),
                "the blocked train must divert to the parallel siding");
        assertTrue(model.getBlockManager().getOwners(siding).contains(east),
                "the siding must be locked by the diverted train");
        assertTrue(east.getAutopilot().currentRoute().contains(siding),
                "the route must be rewritten through the siding");
        assertFalse(east.getAutopilot().currentRoute().contains(main()),
                "the blocked main line must leave the route");
        assertTrue(model.getBlockManager().getOwners(main()).contains(west),
                "the main line stays with the westbound train");

        // (3) The eastbound train rolls into the siding and stops at its end, before the blocked
        // boundary. It must be fully clear of the main line.
        boolean[] eastOnMain = {false};
        runUntil(() -> east.getSafetyManager().isWaitingForBlock() && east.getSpeed() == 0, 2000,
                eastOnMain);
        assertTrue(east.getSafetyManager().isWaitingForBlock(),
                "the diverted train must wait for the next canton");
        assertSame(siding, east.getSafetyManager().getCurrentSegment(),
                "the waiting train must be inside the siding, not on the main line");
        assertTrue(isSidingRail(east.getPhysicalFront().getTrack()),
                "the head must halt on a siding rail: " + east.getPhysicalFront().getTrack());
        assertEquals(0, east.getSpeed());

        // (4) The westbound train passes through the main line; the meeting resolves and both
        // trains reach their stations.
        orderSpeed(west, 1);
        runUntil(() -> east.getStationId() == world.b.getId()
                && west.getStationId() == world.a.getId(), 3000, eastOnMain);

        assertFalse(eastOnMain[0], "the diverted train must never use the main line");
        assertEquals(world.b.getId(), east.getStationId(), "the eastbound train must reach B");
        assertEquals(world.a.getId(), west.getStationId(),
                "the westbound train must pass the siding and reach A");
        assertTrue(contacts.isEmpty(),
                "the crossing must resolve without contact or crash: " + contacts);
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

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

        world.a = station(world.approach.get(2), "A");
        world.b = station(world.tB, "B");

        // The eastbound train is created first on purpose: Model.moveLocomotives() processes
        // locomotives in creation order, and the crossing must resolve safely regardless of which
        // train moves first (the canton is released only when the tail clears the shared fork).
        east = placeTrain(List.of(world.approach.get(2)), List.of(true), List.of(Dir.W));
        east.setStationId(world.a.getId());
        west = placeTrain(List.of(world.tB), List.of(true), List.of(Dir.E));
        west.setStationId(world.b.getId());
    }

    /**
     * Plans the eastbound route while the main line is free; no speed, so only the current canton.
     */
    private void programEastPlan() {
        int locoId = directorId(east);
        List<String> errors = model.setProgram("""
                create itinerary "AB" {
                    add station %d
                    add station %d
                }
                assign itinerary "AB" to train %d;
                train %d set autopilot true;
                """.formatted(world.a.getId(), world.b.getId(), locoId, locoId));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
    }

    /**
     * Re-plans the eastbound route (still free), starts the westbound train so it claims the main,
     * holds it at B, and finally orders the eastbound speed. Every step runs inside one program so
     * the automation reset of {@code setProgram} does not deactivate a half-configured train.
     */
    private void programCrossingSetup() {
        int eastId = directorId(east);
        int westId = directorId(west);
        List<String> errors = model.setProgram("""
                create itinerary "BA" {
                    add station %d
                    add station %d
                }
                assign itinerary "AB" to train %d;
                train %d set autopilot true;
                assign itinerary "BA" to train %d;
                train %d set autopilot true;
                train %d set speed 1;
                train %d set speed 0;
                train %d set speed 1;
                """.formatted(world.b.getId(), world.a.getId(), eastId, eastId, westId, westId,
                westId, westId, eastId));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
    }

    /** Orders a speed through the DSL (console path), as a user or a trigger would. */
    private void orderSpeed(Train train, int speed) {
        int locoId = directorId(train);
        String error = letrain.command.PlayerCommandExecutor
                .execute("train " + locoId + " set speed " + speed + ";", model);
        assertTrue(error == null, "unexpected error: " + error);
    }

    private int directorId(Train train) {
        return ((Locomotive) train.getDirectorLinker()).getId();
    }

    private boolean isSidingRail(Track track) {
        return track == world.s1 || track == world.s2 || track == world.s3;
    }

    private void trackEastPosition(boolean[] eastOnMain) {
        Track head = east.getPhysicalFront().getTrack();
        if (head == world.tMain) {
            eastOnMain[0] = true;
        }
    }

    private ScriptTrainEventListener contactListener(List<String> events) {
        return new ScriptTrainEventListener() {
            @Override
            public void onContact(Train train, Point pos, int speed) {
                events.add("contact train=" + train.getId() + " @" + pos + " speed=" + speed);
            }

            @Override
            public void onCrash(Train train, Point pos, int speed) {
                events.add("crash train=" + train.getId() + " @" + pos + " speed=" + speed);
            }
        };
    }

    private void runUntil(java.util.function.BooleanSupplier condition, int maxTicks,
            boolean[] eastOnMain) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (model.getScheduler() != null) {
                model.getScheduler().tick();
            }
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            trackEastPosition(eastOnMain);
        }
    }

    private Train placeTrain(List<RailTrack> tracks, List<Boolean> locoFlags, List<Dir> entryDirs) {
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        List<Linker> linkers = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            if (locoFlags.get(i)) {
                Locomotive loco = new Locomotive(model.nextLocomotiveId(), "L" + i, "RED");
                loco.setEngineOn(true);
                linkers.add(loco);
                model.addLocomotive(loco);
            } else {
                linkers.add(new letrain.vehicle.rail.impl.Wagon("w" + i));
            }
        }
        for (Linker linker : linkers) {
            train.pushBack(linker);
        }
        train.setDirectorLinker((letrain.vehicle.Tractor) linkers.get(0));
        for (int i = 0; i < tracks.size(); i++) {
            tracks.get(i).enterLinkerFromDir(entryDirs.get(i), linkers.get(i));
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

    /** East fork: both the main (W-E) and the siding (S-E) reach the exit; normal is E-W. */
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

    private Station station(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    private void connect(Track from, Track to) {
        from.connect(Dir.E, to);
        to.connect(Dir.W, from);
    }

    private Segment main() {
        return model.getRailwayGraph().getSegment(world.tMain);
    }

    private Segment siding() {
        return model.getRailwayGraph().getSegment(world.s2);
    }
}
