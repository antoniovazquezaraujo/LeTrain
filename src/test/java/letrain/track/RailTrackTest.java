package letrain.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Vehicle;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Wagon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RailTrackTest {

    RailTrack track;

    @BeforeEach
    void setUp() {
        track = new RailTrack();
        track.addRoute(Dir.N, Dir.S);
        RailTrack connected = new RailTrack();
        track.connect(Dir.S, connected);
    }

    @AfterEach
    void tearDown() {
        track.clear();
    }

    @Test
    void testGetAnyDir() {
        track.clear();
        track.addRoute(Dir.E, Dir.SE);
        Dir result = track.getAnyDir();
        assertTrue(result == Dir.E || result == Dir.SE);
    }

    @Test
    void testGetDir() {
        track.clear();
        track.addRoute(Dir.N, Dir.SE);
        track.addRoute(Dir.SE, Dir.N);
        assertEquals(Dir.SE, track.getDir(Dir.N));
        assertEquals(Dir.N, track.getDir(Dir.SE));
        assertNull(track.getDir(Dir.E));
    }

    @Test
    void testGetNumOpenDirs() {
        track.addRoute(Dir.W, Dir.SE);
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.S, Dir.N);
        assertEquals(5, track.getNumRoutes());
    }

    @Test
    void testAddRoute() {
        track.addRoute(Dir.E, Dir.W);
        assertEquals(Dir.W, track.getDir(Dir.E));
        assertEquals(Dir.E, track.getDir(Dir.W));
    }

    @Test
    void testRemoveRoute() {
        track.addRoute(Dir.E, Dir.W);
        track.removeRoute(Dir.W, Dir.E);
        assertNull(track.getDir(Dir.E));
        assertNull(track.getDir(Dir.W));
    }

    @Test
    void testLink() {
        RailTrack other = new RailTrack();
        track.connect(Dir.S, other);
        assertEquals(other, track.getConnected(Dir.S));
    }

    @Test
    void testUnlink() {
        RailTrack other = new RailTrack();
        track.connect(Dir.S, other);
        track.disconnect(Dir.S);
        assertNull(track.getConnected(Dir.S));
    }

    @Test
    void testAddVehicle() {
        Vehicle<RailTrack> v = new Wagon();
        track.enterLinkerFromDir(Dir.S, (Linker) v);
        assertEquals(v, track.getLinker());
    }

    @Test
    void testAddTwoVehicles() {
        Vehicle<RailTrack> v = new Wagon();
        Vehicle<RailTrack> v2 = new Wagon();
        track.enterLinkerFromDir(Dir.S, (Linker) v);
        track.enterLinkerFromDir(Dir.N, (Linker) v2);
        // TODO fix this
        // assertFalse(added);
    }

    @Test
    void testRemoveVehicle() {
        Vehicle<RailTrack> v = new Wagon();
        track.enterLinkerFromDir(Dir.N, (Linker) v);
        track.removeLinker();
        assertNull(track.getLinker());
    }

    @Test
    void testRemoveVehicleWhenEmpty() {
        track.removeLinker();
        assertNull(track.getLinker());
    }

}
