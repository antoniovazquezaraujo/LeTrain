package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.TrainSafetyManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #633, braking-curve regression matrix (from the empirical diagnosis of the "accelerating
 * case"): B x speed x entry state on a real model, asserting the boundary stop never crosses and
 * lands on the last rail of the segment.
 *
 * <p>
 * Layout: segment S0 = rails x0..x12 + fork x13; segment S1 = x14..x16, owned by a parked blocker
 * so locking it fails and the plan is built. The subject head is placed at x = 13 - B so that
 * {@code railsToBoundary() == B}. States:
 *
 * <ul>
 * <li>{@code cruise}: target == speed, inertia counter 0;
 * <li>{@code accel}: target = min(5, speed + 2), counter one rail before the next notch;
 * <li>{@code decel}: target = max(1, speed - 2), counter one rail before the next notch.
 * </ul>
 *
 * Rules asserted:
 *
 * <ul>
 * <li>If the plan was scheduled (target still positive), the train must never cross the fork and
 * must halt with {@code railsToBoundary() == 1} (last rail before the node).
 * <li>If it braked immediately (the "no time" frontier: B <= brakingRails(speed)), it ends on or
 * past the node - the designed "se joda" outcome, no artificial wall.
 * </ul>
 */
@DisplayName("Braking curve regression matrix (issue #633)")
class BrakingCurveMatrixTest {

    /** Long enough for the planned branch at MAX_SPEED (brakingRails(10) = 55). */
    private static final int MAX_B = 60;

    @Test
    @DisplayName("B x speed x state: no crossing when planned, stop on the last rail before the node")
    void matrix() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String state : List.of("cruise", "accel", "decel")) {
            for (int speed = 1; speed <= Locomotive.MAX_SPEED; speed++) {
                int target = state.equals("cruise") ? speed
                        : state.equals("accel") ? Math.min(Locomotive.MAX_SPEED, speed + 2)
                                : Math.max(1, speed - 2);
                int counter = state.equals("cruise") ? 0
                        : Math.max(1, speed * (state.equals("accel") ? 2 : 1)) - 1;
                for (int b = 1; b <= MAX_B; b++) {
                    Row row = run(state, speed, target, counter, b);
                    if (row.planned) {
                        if (row.crossed || row.finalB != 1) {
                            failures.add(row + " -> planned but crossed=" + row.crossed + " finalB="
                                    + row.finalB + " headX=" + row.headX);
                        }
                    } else if (state.equals("cruise") && !row.crossed && !row.onFork) {
                        // At cruise the counter is 0 and the formula is exact: the "no time"
                        // frontier must end on or past the node (se joda). With a partway counter
                        // the real distance can be shorter, so stopping short is fine there.
                        failures.add(row + " -> immediate brake must end on/past the node (se joda)"
                                + " crossed=" + row.crossed + " onFork=" + row.onFork + " headX="
                                + row.headX);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "braking-curve matrix failures:\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("accelerating entry (1 -> 3): global no-cross and finalB==1 for B=3..60")
    void acceleratingEntry_neverCrosses() throws Exception {
        for (int b = 3; b <= MAX_B; b++) {
            Row row = run("accel", 1, 3, 1, b);
            assertTrue(row.planned, "B=" + b + " must schedule the plan: " + row);
            assertTrue(!row.crossed, "B=" + b + " must not cross: " + row);
            assertTrue(row.finalB == 1, "B=" + b + " must halt on the last rail: " + row);
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private record Row(String state, int speed, int target, int counter, int b, boolean planned,
            int headX, int finalB, boolean onFork, boolean crossed) {
        @Override
        public String toString() {
            return state + "(s=" + speed + ",t=" + target + ",c=" + counter + ",B=" + b + ")";
        }
    }

    private Row run(String state, int speed, int target, int counter, int b) throws Exception {
        Model model = new Model(1);
        model.postLoadInit();

        List<RailTrack> s0 = new ArrayList<>();
        for (int x = 0; x < MAX_B; x++) {
            s0.add(track(model, x, 0));
        }
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(MAX_B, 0));
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        model.getRailMap().addTrack(new Point(MAX_B, 0), fork);
        model.addFork(fork);
        List<RailTrack> s1 = new ArrayList<>();
        for (int x = MAX_B + 1; x <= MAX_B + 3; x++) {
            s1.add(track(model, x, 0));
        }
        for (int i = 0; i + 1 < s0.size(); i++) {
            connect(s0.get(i), s0.get(i + 1));
        }
        connect(s0.get(MAX_B - 1), fork);
        connect(fork, s1.get(0));
        connect(s1.get(0), s1.get(1));
        connect(s1.get(1), s1.get(2));

        Train blocker = place(model, 2, s1.get(0));
        ((Locomotive) blocker.getDirectorLinker()).setEngineOn(false);
        blocker.getSafetyManager().claimOccupiedSegments();

        Train subject = place(model, 1, s0.get(MAX_B - b));
        Locomotive loco = (Locomotive) subject.getDirectorLinker();
        ((letrain.itinerary.impl.AutoPilotImpl) subject.getAutopilot())
                .setMode(letrain.itinerary.AutoPilot.Mode.FOLLOWING);

        loco.setEngineOn(true);
        loco.setCurrentSpeed(speed);
        loco.setTargetSpeed(target);
        setCounter(loco, counter);

        Segment s0Segment = model.getRailwayGraph().getSegment(s0.get(0));
        TrainSafetyManager safety = subject.getSafetyManager();
        safety.acquireInitialLocks();
        boolean planned = loco.getTargetSpeed() > 0;

        boolean crossed = false;
        int ticks = 0;
        int still = 0;
        while (ticks < 6000 && still < 30) {
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            ticks++;
            if (safety.getCurrentSegment() != null
                    && !safety.getCurrentSegment().equals(s0Segment)) {
                crossed = true;
            }
            if (subject.getSpeed() == 0) {
                still++;
            } else {
                still = 0;
            }
        }

        RailTrack headTrack = (RailTrack) subject.getPhysicalFront().getTrack();
        int headX = headTrack.getPosition().getX();
        boolean onFork = headTrack == fork;
        int finalB = crossed || safety.railsToBoundary().isEmpty() ? -1
                : safety.railsToBoundary().getAsInt();
        return new Row(state, speed, target, counter, b, planned, headX, finalB, onFork, crossed);
    }

    /**
     * Test-only setup: places the inertia rail counter at {@code counter} to reproduce the "entered
     * the segment while still notching" state without driving extra rails.
     */
    private static void setCounter(Locomotive loco, int counter) throws Exception {
        Field field = Locomotive.class.getDeclaredField("railsSinceLastSpeedChange");
        field.setAccessible(true);
        field.setInt(loco, counter);
    }

    private RailTrack track(Model model, int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        model.getRailMap().addTrack(new Point(x, y), track);
        return track;
    }

    private void connect(RailTrack from, RailTrack to) {
        from.connect(Dir.E, to);
        to.connect(Dir.W, from);
    }

    private Train place(Model model, int id, RailTrack track) {
        Train train = new Train(id);
        train.setModel(model);
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "L" + id, "RED");
        loco.setEngineOn(true);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        track.enterLinkerFromDir(Dir.W, loco);
        assertNotNull(train.getDirectorLinker());
        return train;
    }
}
