package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #645 follow-up: the contact paths must not write a speed after notifying the contact. The
 * contact chain (mission completion -> waypoint actions -> couple -> scheduled departure) may
 * legitimately restore a speed inside {@code notifyContact}, and a contact that couples the
 * occupant must not emergency-stop our own train through the link that just joined it.
 */
@DisplayName("Issue #645 follow-up: contact speed restore")
class ContactSpeedRestoreTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    @Test
    @DisplayName("a speed restored during the buffer contact is not overwritten afterwards")
    void bufferContact_restoreDuringNotify_isKept() {
        List<RailTrack> rails = line(0, 2, 0);
        Train subject = placeTrain(rails.get(0));
        Locomotive loco = (Locomotive) subject.getDirectorLinker();
        List<Integer> contactSpeeds = new ArrayList<>();
        subject.addScriptTrainEventListener(new ScriptTrainEventListener() {
            @Override
            public void onContact(Train train, Point pos, int speed) {
                contactSpeeds.add(speed);
                // Like a scheduled departure releasing inside the contact chain.
                train.setSpeed(3);
            }
        });
        loco.setCurrentSpeed(1);
        loco.setTargetSpeed(1);

        runUntil(() -> loco.getTargetSpeed() == 3, 200);

        assertEquals(List.of(1), contactSpeeds, "the contact must happen at the real speed");
        assertEquals(3, loco.getTargetSpeed(),
                "the speed restored during notifyContact must survive the buffer contact");
        assertFalse(subject.isStalled(), "a low-speed contact is not a crash");
    }

    @Test
    @DisplayName("coupling the occupant during the contact does not emergency-stop our own train")
    void contactDetected_couplingDuringNotify_doesNotStopSelf() {
        List<RailTrack> rails = line(0, 2, 0);
        Train subject = placeTrain(rails.get(0));
        Locomotive loco = (Locomotive) subject.getDirectorLinker();
        Wagon wagon = wagonOnlyTrain(rails.get(1));
        // Auto mode: a failed advance brakes by inertia instead of zeroing the target, so the
        // only writer that could kill the restored speed is the reviewed self-emergencyStop.
        ((letrain.itinerary.impl.AutoPilotImpl) subject.getAutopilot())
                .setMode(letrain.itinerary.AutoPilot.Mode.FOLLOWING);
        subject.addScriptTrainEventListener(new ScriptTrainEventListener() {
            @Override
            public void onContact(Train train, Point pos, int speed) {
                // Like the couple waypoint action running inside the contact chain: couple and
                // then restore the departure speed (restoreSpeed uses the target setter).
                train.getTrainCouplingManager().prepareLink(train, true, 0);
                train.getTrainCouplingManager().joinLinkers(train);
                loco.setTargetSpeed(3);
            }
        });
        loco.setCurrentSpeed(1);
        loco.setTargetSpeed(1);

        runUntil(() -> subject.getLinkers().size() == 2, 200);

        assertEquals(2, subject.getLinkers().size(), "the chain must couple the occupant");
        assertEquals(wagon, subject.getLinkers().getFirst(),
                "the touched wagon must travel with the train");
        assertEquals(3, loco.getTargetSpeed(),
                "the coupled train must not emergency-stop itself through the absorbed link");
        assertFalse(subject.isStalled(), "a low-speed contact is not a crash");
    }

    // ─────────────────────────────────────────────────────────────────
    // Fixture
    // ─────────────────────────────────────────────────────────────────

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

    private List<RailTrack> line(int x0, int count, int y) {
        List<RailTrack> rails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rails.add(track(x0 + i, y));
        }
        for (int i = 0; i + 1 < rails.size(); i++) {
            rails.get(i).connect(Dir.E, rails.get(i + 1));
            rails.get(i + 1).connect(Dir.W, rails.get(i));
        }
        return rails;
    }

    private Train placeTrain(RailTrack start) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "L");
        loco.setEngineOn(true);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        start.enterLinkerFromDir(Dir.W, loco);
        train.getSafetyManager().acquireInitialLocks();
        return train;
    }

    /** A wagon-only train placed ahead (a separate train, like a detached part). */
    private Wagon wagonOnlyTrain(RailTrack rail) {
        Wagon wagon = new Wagon("w");
        Train wagonTrain = new Train(model.nextTrainId());
        wagonTrain.setModel(model);
        wagonTrain.pushBack(wagon);
        model.addWagon(wagon);
        rail.enterLinkerFromDir(Dir.W, wagon);
        wagonTrain.getSafetyManager().claimOccupiedSegments();
        return wagon;
    }

    private void runUntil(BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            model.getScheduler().tick();
            model.getGameClock().tick();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            model.removeDestroyedTrains();
        }
    }
}
