package letrain.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the new track-element movement API of issue #468:
 * {@code Model.moveSensor(Sensor, Dir)} and {@code Model.moveSemaphore(RailSemaphore, Dir)}.
 *
 * <p>
 * These tests are RED by design: the API does not exist yet.
 *
 * <p>
 * Movement semantics assumed by these tests: a move request advances the element along the rail
 * (like a vehicle) to the next free resting cell in the requested direction. Track cells occupied by
 * another {@link TrackComponent} are skipped, but a cell occupied by a train linker ({@link
 * Track#getLinker()}) is never skipped. A {@link ForkRailTrack} cell is a routing node that is
 * crossed (not a resting place); the exit branch is the fork's active branch (normal vs
 * alternative).
 */
@DisplayName("Model.moveSensor/moveSemaphore (issue #468)")
class TrackElementMoveTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RailTrack straightAt(int x, int y, Dir from, Dir to) {
        RailTrack track = new RailTrack();
        track.addRoute(from, to);
        track.addRoute(to, from);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    private void connect(RailTrack a, Dir dirA, RailTrack b, Dir dirB) {
        a.connect(dirA, b);
        b.connect(dirB, a);
    }

    private Sensor placeSensor(RailTrack track, int id) {
        Sensor sensor = new Sensor(id);
        sensor.setTrack(track);
        sensor.setCreationDir(Dir.E);
        track.setComponent(sensor);
        model.addSensor(sensor);
        return sensor;
    }

    private RailSemaphore placeSemaphore(RailTrack track, int id, Dir creationDir) {
        RailSemaphore semaphore = new RailSemaphore(id, track.getPosition());
        semaphore.setCreationDir(creationDir);
        model.addSemaphore(semaphore);
        return semaphore;
    }

    private ForkRailTrack forkAt(int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        model.getRailMap().addTrack(fork.getPosition(), fork);
        return fork;
    }

    /** Configures a fork split from West into East (normal) and South (alternative). */
    private void addForkWestEastSouth(ForkRailTrack fork) {
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.S);
        fork.addRoute(Dir.S, Dir.W);
    }

    // ------------------------------------------------------------------
    // 1. Straight line
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1.1 Sensor moves one track toward E, keeps identity and creationDir")
    void sensor_movesEast_oneTrack_keepsIdentity() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        RailTrack t2 = straightAt(2, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);
        Sensor sensor = placeSensor(t0, 42);
        sensor.setCreationDir(Dir.W);
        int originalId = sensor.getId();
        Dir originalCreationDir = sensor.getCreationDir();

        // Act
        boolean moved = model.moveSensor(sensor, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t1, sensor.getTrack());
        assertNull(t0.getComponent());
        assertSame(sensor, t1.getComponent());
        assertEquals(originalId, sensor.getId());
        assertEquals(originalCreationDir, sensor.getCreationDir());
        assertTrue(model.getSensors().contains(sensor));

        // Move again to prove one-track granularity.
        assertTrue(model.moveSensor(sensor, Dir.E));
        assertSame(t2, sensor.getTrack());
        assertNull(t1.getComponent());
        assertSame(sensor, t2.getComponent());
    }

    @Test
    @DisplayName("1.2 Sensor moves one track toward W")
    void sensor_movesWest_oneTrack() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        RailTrack t2 = straightAt(2, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);
        Sensor sensor = placeSensor(t2, 42);

        // Act
        boolean moved = model.moveSensor(sensor, Dir.W);

        // Assert
        assertTrue(moved);
        assertSame(t1, sensor.getTrack());
        assertNull(t2.getComponent());
        assertSame(sensor, t1.getComponent());
    }

    @Test
    @DisplayName("1.3 RailSemaphore moves one track toward E updating position and track component")
    void semaphore_movesEast_updatesPosition() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        RailSemaphore semaphore = placeSemaphore(t0, 7, Dir.W);
        int originalId = semaphore.getId();
        Dir originalCreationDir = semaphore.getCreationDir();

        // Act
        boolean moved = model.moveSemaphore(semaphore, Dir.E);

        // Assert
        assertTrue(moved);
        assertEquals(t1.getPosition(), semaphore.getPosition());
        assertNull(t0.getComponent());
        assertSame(semaphore, t1.getComponent());
        assertSame(semaphore, model.getSemaphoreAt(t1.getPosition()));
        assertNull(model.getSemaphoreAt(t0.getPosition()));
        assertEquals(originalId, semaphore.getId());
        assertEquals(originalCreationDir, semaphore.getCreationDir());
    }

    @Test
    @DisplayName("1.4 SpeedSignal (a Sensor subclass) moves one track toward E")
    void speedSignal_movesEast_oneTrack() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        SpeedSignal signal = new SpeedSignal(55, Dir.E, 60, true);
        signal.setTrack(t0);
        t0.setComponent(signal);
        model.addSensor(signal);

        // Act
        boolean moved = model.moveSensor(signal, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t1, signal.getTrack());
        assertNull(t0.getComponent());
        assertSame(signal, t1.getComponent());
    }

    // ------------------------------------------------------------------
    // 2. Skipping cells occupied by other elements
    // ------------------------------------------------------------------

    @Test
    @DisplayName("2.1 Sensor jumps over one occupied track and lands on next free track")
    void sensor_skipsOccupiedTrackAndLandsOnNextFree() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        RailTrack t2 = straightAt(2, 0, Dir.W, Dir.E);
        RailTrack t3 = straightAt(3, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);
        connect(t2, Dir.E, t3, Dir.W);
        Sensor moving = placeSensor(t0, 1);
        Sensor blocker = placeSensor(t1, 2);

        // Act
        boolean moved = model.moveSensor(moving, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t2, moving.getTrack());
        assertNull(t0.getComponent());
        assertSame(blocker, t1.getComponent());
        assertSame(moving, t2.getComponent());
        assertTrue(model.getSensors().contains(moving));
        assertTrue(model.getSensors().contains(blocker));
    }

    @Test
    @DisplayName("2.2 Sensor jumps over several occupied tracks and lands on next free track")
    void sensor_skipsSeveralOccupiedTracksAndLandsOnNextFree() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        RailTrack t2 = straightAt(2, 0, Dir.W, Dir.E);
        RailTrack t3 = straightAt(3, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);
        connect(t2, Dir.E, t3, Dir.W);
        Sensor moving = placeSensor(t0, 1);
        Sensor blocker1 = placeSensor(t1, 2);
        Sensor blocker2 = placeSensor(t2, 3);

        // Act
        boolean moved = model.moveSensor(moving, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(t3, moving.getTrack());
        assertSame(blocker1, t1.getComponent());
        assertSame(blocker2, t2.getComponent());
        assertSame(moving, t3.getComponent());
    }

    // ------------------------------------------------------------------
    // 3. Trains are never jumped over
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3.1 Sensor does NOT move when the next track is occupied by a train linker")
    void sensor_doesNotMove_whenNextTrackHasTrain() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        RailTrack t2 = straightAt(2, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);
        Sensor sensor = placeSensor(t0, 1);
        Locomotive blocker = new Locomotive(900, "Blocker");
        assertTrue(t1.enterLinkerFromDir(Dir.W, blocker));
        assertNotNull(t1.getLinker());

        // Act
        boolean moved = model.moveSensor(sensor, Dir.E);

        // Assert
        assertFalse(moved);
        assertSame(t0, sensor.getTrack());
        assertSame(sensor, t0.getComponent());
        assertNotNull(t1.getLinker());
        assertNull(t2.getComponent());
    }

    // ------------------------------------------------------------------
    // 4. End of the line
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4.1 Sensor at the end of the line returns false and does not move")
    void sensor_endOfLine_returnsFalse() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        Sensor sensor = placeSensor(t1, 1);

        // Act
        boolean moved = model.moveSensor(sensor, Dir.E);

        // Assert
        assertFalse(moved);
        assertSame(t1, sensor.getTrack());
        assertSame(sensor, t1.getComponent());
        assertNull(t0.getComponent());
    }

    @Test
    @DisplayName("4.2 RailSemaphore at the end of the line returns false and does not move")
    void semaphore_endOfLine_returnsFalse() {
        // Arrange
        RailTrack t0 = straightAt(0, 0, Dir.E, Dir.W);
        RailTrack t1 = straightAt(1, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        RailSemaphore semaphore = placeSemaphore(t1, 1, Dir.E);
        Point originalPosition = new Point(t1.getPosition());

        // Act
        boolean moved = model.moveSemaphore(semaphore, Dir.E);

        // Assert
        assertFalse(moved);
        assertEquals(originalPosition, semaphore.getPosition());
        assertSame(semaphore, t1.getComponent());
        assertNull(t0.getComponent());
    }

    // ------------------------------------------------------------------
    // 5. Crossing a ForkRailTrack through its active branch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5.1 Sensor crossing a fork in NORMAL state continues on the straight branch")
    void sensor_crossesForkNormal_landsOnStraightBranch() {
        // Arrange
        RailTrack tWest = straightAt(0, 0, Dir.E, Dir.W);
        ForkRailTrack fork = forkAt(1, 0);
        addForkWestEastSouth(fork);
        fork.setNormalRoute();
        RailTrack tStraight = straightAt(2, 0, Dir.W, Dir.E);
        RailTrack tBranch = straightAt(1, 1, Dir.S, Dir.N);
        connect(tWest, Dir.E, fork, Dir.W);
        connect(fork, Dir.E, tStraight, Dir.W);
        connect(fork, Dir.S, tBranch, Dir.N);
        Sensor sensor = placeSensor(tWest, 1);
        assertFalse(fork.isUsingAlternativeRoute());

        // Act
        boolean moved = model.moveSensor(sensor, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(tStraight, sensor.getTrack());
        assertNull(tWest.getComponent());
        assertNull(fork.getComponent());
        assertSame(sensor, tStraight.getComponent());
        assertNull(tBranch.getComponent());
    }

    @Test
    @DisplayName("5.2 Sensor crossing a fork in ALTERNATIVE state continues on the diverging branch")
    void sensor_crossesForkAlternative_landsOnDivergingBranch() {
        // Arrange
        RailTrack tWest = straightAt(0, 0, Dir.E, Dir.W);
        ForkRailTrack fork = forkAt(1, 0);
        addForkWestEastSouth(fork);
        fork.setAlternativeRoute();
        RailTrack tStraight = straightAt(2, 0, Dir.W, Dir.E);
        RailTrack tBranch = straightAt(1, 1, Dir.S, Dir.N);
        connect(tWest, Dir.E, fork, Dir.W);
        connect(fork, Dir.E, tStraight, Dir.W);
        connect(fork, Dir.S, tBranch, Dir.N);
        Sensor sensor = placeSensor(tWest, 1);
        assertTrue(fork.isUsingAlternativeRoute());

        // Act
        boolean moved = model.moveSensor(sensor, Dir.E);

        // Assert
        assertTrue(moved);
        assertSame(tBranch, sensor.getTrack());
        assertNull(tWest.getComponent());
        assertNull(fork.getComponent());
        assertSame(sensor, tBranch.getComponent());
        assertNull(tStraight.getComponent());
    }
}
