package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.Segment;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
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
        assertFalse(
                bm.getOwners(segment).contains(train),
                "Segment should be released after train destruction");
    }
}
