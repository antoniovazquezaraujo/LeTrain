package letrain.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tracker;
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
        Linker v = new Wagon();
        boolean added = track.enterLinkerFromDir(Dir.S, v);
        assertTrue(added);
        assertEquals(v, track.getLinker());
    }

    @Test
    void testAddTwoVehicles() {
        Linker v = new Wagon();
        Linker v2 = new Wagon();
        assertTrue(track.enterLinkerFromDir(Dir.S, v));

        // El segundo vehículo no debe reemplazar al primero.
        boolean secondAdded = track.enterLinkerFromDir(Dir.N, v2);
        assertFalse(secondAdded);
        assertEquals(v, track.getLinker());

        // El primer vehículo sigue en el track; el segundo no debe haber ingresado.
        assertEquals(track, ((Tracker) v).getTrack());
        assertNull(((Tracker) v2).getTrack(), "El segundo vehículo no debe haberse montado en el track porque estaba ocupado");
    }

    @Test
    void testRemoveVehicle() {
        Linker v = new Wagon();
        track.enterLinkerFromDir(Dir.N, v);
        track.removeLinker();
        assertNull(track.getLinker());
    }

    @Test
    void testRemoveVehicleWhenEmpty() {
        track.removeLinker();
        assertNull(track.getLinker());
    }

}
