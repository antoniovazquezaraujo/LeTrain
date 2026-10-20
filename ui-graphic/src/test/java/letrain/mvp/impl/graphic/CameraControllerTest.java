package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CameraController - 3D camera controls and CAB mode behavior")
class CameraControllerTest {

    @BeforeAll
    static void initNatives() {
        new com.badlogic.gdx.utils.SharedLibraryLoader().load("gdx");
    }

    private Model model;
    private CameraController cameraController;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.updateGroundMap(new Point(-50, -50), 100, 100);
        model.getCursor().setPosition(new Point(10, 10));
        model.getCursor().setDir(Dir.E);

        cameraController = new CameraController(model);
    }

    @Test
    @DisplayName("init: sets camera near clipping plane to 0.2 to prevent near-clipping in CAB view")
    void should_InitializeCameraWithNearPlane_When_InitCalled() {
        // Arrange & Act
        PerspectiveCamera cam = cameraController.init(800, 600);

        // Assert
        assertNotNull(cam);
        assertEquals(0.2f, cam.near, 0.001f);
        assertEquals(1000f, cam.far, 0.001f);
        assertEquals(67f, cam.fieldOfView, 0.001f);
    }

    @Test
    @DisplayName("cycleMode: cycles through ORBIT -> CAB -> MAP -> ORBIT when locomotives exist")
    void should_CycleThroughAllModes_When_LocomotivesPresent() {
        // Arrange
        cameraController.init(800, 600);
        assertEquals(CameraController.CameraMode.ORBIT, cameraController.getMode());

        // Act & Assert
        cameraController.cycleMode(true);
        assertEquals(CameraController.CameraMode.CAB, cameraController.getMode());

        cameraController.cycleMode(true);
        assertEquals(CameraController.CameraMode.MAP, cameraController.getMode());

        cameraController.cycleMode(true);
        assertEquals(CameraController.CameraMode.ORBIT, cameraController.getMode());
    }

    @Test
    @DisplayName("cycleMode: skips CAB mode when no locomotives are on the map")
    void should_SkipCabMode_When_NoLocomotivesExist() {
        // Arrange
        cameraController.init(800, 600);
        assertEquals(CameraController.CameraMode.ORBIT, cameraController.getMode());

        // Act & Assert
        cameraController.cycleMode(false);
        assertEquals(CameraController.CameraMode.MAP, cameraController.getMode());

        cameraController.cycleMode(false);
        assertEquals(CameraController.CameraMode.ORBIT, cameraController.getMode());
    }

    @Test
    @DisplayName("update: in CAB mode, camera is positioned behind locomotive and oriented forward")
    void should_PositionCameraBehindLocomotive_When_CabModeActive() {
        // Arrange
        RailTrack track = new RailTrack();
        track.setPosition(new Point(10, 10));
        track.getRouter().addRoute(Dir.W, Dir.E);
        model.getRailMap().addTrack(new Point(10, 10), track);

        Locomotive loco = new Locomotive(1, "A");
        loco.setPosition(new Point(10, 10));
        loco.setDir(Dir.E);
        loco.setTrack(track);
        model.addLocomotive(loco);
        model.setSelectedLocomotive(loco);

        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.CAB);

        // Act
        cameraController.update(1.0f);

        // Assert: Camera should be elevated (Y=2.0) and placed behind locomotive moving East
        assertEquals(2.0f, cam.position.y, 0.001f);
        assertTrue(cam.position.x < 10.5f, "Camera X should be behind the locomotive position");
        assertTrue(cam.direction.x > 0,
                "Camera should be looking forward along the movement axis (East)");

        // The locomotive model (box centered at 10.5, 0.61, 10.5 with half-extents 0.4) intersects
        // the camera frustum
        assertTrue(cam.frustum.boundsInFrustum(10.5f, 0.61f, 10.5f, 0.4f, 0.4f, 0.4f),
                "Locomotive model bounding box must intersect camera frustum in CAB mode");
    }

    @Test
    @DisplayName("CAB mode: camera direction immediately aligns with locomotive heading on mode change")
    void should_SnapDirectionImmediately_When_SwitchingToCabMode() {
        // Arrange
        RailTrack track = new RailTrack();
        track.setPosition(new Point(10, 10));
        track.getRouter().addRoute(Dir.S, Dir.N);
        model.getRailMap().addTrack(new Point(10, 10), track);

        Locomotive loco = new Locomotive(1, "A");
        loco.setPosition(new Point(10, 10));
        loco.setDir(Dir.N);
        loco.setTrack(track);
        model.addLocomotive(loco);
        model.setSelectedLocomotive(loco);

        PerspectiveCamera cam = cameraController.init(800, 600);

        // Act: Switch to CAB mode and update
        cameraController.setMode(CameraController.CameraMode.CAB);
        cameraController.update(1.0f);

        // Assert: Direction should be pointing North (Z negative in LibGDX world coordinates)
        assertTrue(cam.direction.z < 0,
                "Camera should be looking North immediately without lerp lag");
        assertEquals(0.0f, cam.direction.x, 0.05f,
                "Camera should not have X component when facing North");

        // Turn locomotive to West and force snap
        loco.setDir(Dir.W);
        cameraController.forceSnap();
        cameraController.update(1.0f);

        // Assert: Direction should be pointing West (X negative)
        assertTrue(cam.direction.x < 0, "Camera should look West immediately after snap");
        assertEquals(0.0f, cam.direction.z, 0.05f,
                "Camera should not have Z component when facing West");
    }

    @Test
    @DisplayName("opening the console over DRIVE does not move the camera and keeps it on the loco")
    void should_KeepFollowingLocomotive_When_ConsoleOpensOverDrive() {
        // Arrange: a locomotive selected in DRIVE.
        RailTrack track = new RailTrack();
        track.setPosition(new Point(10, 10));
        track.getRouter().addRoute(Dir.W, Dir.E);
        model.getRailMap().addTrack(new Point(10, 10), track);

        Locomotive loco = new Locomotive(1, "A");
        loco.setPosition(new Point(10, 10));
        loco.setDir(Dir.E);
        loco.setTrack(track);
        model.addLocomotive(loco);
        model.setSelectedLocomotive(loco);
        model.setMode(Model.GameMode.DRIVE);

        PerspectiveCamera cam = cameraController.init(800, 600);
        settle();
        float beforeX = cam.position.x;
        float beforeZ = cam.position.z;

        // Act: open the console while the cursor sits on another screen.
        model.getCursor().setPosition(new Point(60, 60));
        model.setMode(Model.GameMode.COMMAND);
        cameraController.update(1.0f);

        // Assert: the transition does not move the camera...
        assertEquals(beforeX, cam.position.x, 0.2f);
        assertEquals(beforeZ, cam.position.z, 0.2f);
        // ...and it keeps tracking the locomotive instead of drifting to the cursor.
        settle();
        float toLoco = distance(cam.position.x, cam.position.z, 10.5f, 10.5f);
        float toCursor = distance(cam.position.x, cam.position.z, 60.5f, 60.5f);
        assertTrue(toLoco < toCursor,
                "camera=" + cam.position + " toLoco=" + toLoco + " toCursor=" + toCursor);
    }

    private void settle() {
        for (int i = 0; i < 200; i++) {
            cameraController.update(1.0f);
        }
    }

    private static float distance(float x1, float z1, float x2, float z2) {
        return (float) Math.hypot(x1 - x2, z1 - z2);
    }

    @Test
    @DisplayName("orbit zoom: the camera looks ahead when zoomed all the way to the ground")
    void should_LookAhead_When_ZoomedToTheGround() {
        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.ORBIT);

        // zoom all the way in: horizontal view (sky above the horizon)
        cameraController.zoomStep(-1000f);
        for (int i = 0; i < 300; i++) {
            cameraController.update(1f);
        }
        assertEquals(0f, cam.direction.y, 0.02f, "should look straight ahead at the ground");

        // zoom all the way out: classic pitch
        cameraController.zoomStep(1000f);
        for (int i = 0; i < 600; i++) {
            cameraController.update(1f);
        }
        assertEquals(-Math.sin(Math.toRadians(35.0)), cam.direction.y, 0.01f);
    }

    @Test
    @DisplayName("orbit zoom: the camera drops to ground level at maximum zoom-in")
    void should_ReachGroundLevel_When_ZoomedAllTheWayIn() {
        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.ORBIT);

        // zoom all the way in (minimum orbit distance)
        cameraController.zoomStep(-1000f);
        for (int i = 0; i < 300; i++) {
            cameraController.update(1f);
        }

        // The camera sits at ground level (still above rail/ballast) and looks straight ahead
        assertEquals(0.6f, cam.position.y, 0.02f, "should reach ground level at max zoom-in");
        assertEquals(0f, cam.direction.y, 0.02f, "should look straight ahead at the ground");
    }

    @Test
    @DisplayName("orbit zoom: the classic elevated height is preserved at maximum zoom-out")
    void should_KeepClassicHeight_When_ZoomedAllTheWayOut() {
        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.ORBIT);

        // zoom all the way out (maximum orbit distance)
        cameraController.zoomStep(1000f);
        for (int i = 0; i < 600; i++) {
            cameraController.update(1f);
        }

        // Far away the camera keeps the historical 0.7 * maxDistance (28 m) height
        assertEquals(28f, cam.position.y, 0.1f, "should preserve the classic far height");
    }

    @Test
    @DisplayName("orbit zoom: height follows the ground-to-classic lerp across the whole zoom range")
    void should_FollowHeightLerp_When_ZoomedToIntermediateDistances() {
        // Arrange
        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.ORBIT);

        // Act: settle at the closest zoom, then climb to the farthest in equal steps
        cameraController.zoomStep(-1000f);
        updateFrames(300);
        float previous = cam.position.y;

        // Assert: the closest zoom already sits at ground level, and every intermediate step keeps
        // the camera inside the band while following the zoom lerp monotonically upwards.
        assertEquals(0.6f, previous, 0.02f, "minimum zoom must start at ground level");
        for (int i = 0; i < 10; i++) {
            cameraController.zoomStep(3.7f);
            updateFrames(300);

            float y = cam.position.y;
            float expected = MathUtils.lerp(0.6f, 28f, cameraController.getZoomFactor());

            assertFalse(Float.isNaN(y), "height must never become NaN at step " + i);
            assertTrue(y >= 0.6f - 0.02f, "height " + y + " fell below ground level");
            assertTrue(y <= 28f + 0.02f, "height " + y + " exceeded the classic far height");
            assertEquals(expected, y, 0.02f, "height must track the zoom lerp at step " + i);
            assertTrue(y > previous - 1e-3f, "height must not decrease while zooming out");
            previous = y;
        }
        assertTrue(previous > 27f, "zooming all the way out must reach the classic height");
    }

    @Test
    @DisplayName("orbit zoom: random zoom steps keep the camera inside [0.6, 28] with no NaN")
    void should_StayWithinHeightBand_When_ZoomingRandomly() {
        // Arrange
        PerspectiveCamera cam = cameraController.init(800, 600);
        cameraController.setMode(CameraController.CameraMode.ORBIT);
        java.util.Random random = new java.util.Random(2026L);

        // Act & Assert: many random zoom steps, each settled, must never break the safety band.
        for (int i = 0; i < 200; i++) {
            cameraController.zoomStep(random.nextFloat() * 200f - 100f);
            updateFrames(200);

            float y = cam.position.y;
            assertFalse(Float.isNaN(y), "height must never be NaN at iteration " + i);
            assertFalse(Float.isInfinite(y), "height must never be infinite at iteration " + i);
            assertTrue(y >= 0.6f - 0.02f && y <= 28f + 0.02f,
                    "height " + y + " left [0.6, 28] at iteration " + i);
            assertFalse(Float.isNaN(cam.direction.y), "direction.y must never be NaN");
        }
    }

    private void updateFrames(int frames) {
        for (int i = 0; i < frames; i++) {
            cameraController.update(1f);
        }
    }

    @Test
    @DisplayName("the controller exposes the ground target it orbits (terrain materialization)")
    void should_ExposeGroundTarget() {
        cameraController.init(800, 600);

        assertEquals(10.5f, cameraController.getTargetX(), 0.001f);
        assertEquals(10.5f, cameraController.getTargetZ(), 0.001f);
    }

    @Test
    @DisplayName("#820: smoothing is normalised to 60 fps and frame-rate independent")
    void smoothing_isFrameRateIndependent() {
        assertEquals(0.05f, CameraController.smoothing(0.05f, 1f / 60f), 1e-6f,
                "at 60 fps the factor is exactly the historical per-frame value");

        float remainingAt60 = 1f;
        for (int i = 0; i < 60; i++) {
            remainingAt60 *= 1f - CameraController.smoothing(0.05f, 1f / 60f);
        }
        float remainingAt144 = 1f;
        for (int i = 0; i < 144; i++) {
            remainingAt144 *= 1f - CameraController.smoothing(0.05f, 1f / 144f);
        }
        assertEquals(remainingAt60, remainingAt144, 1e-4f,
                "one second of smoothing must converge the same at 60 and 144 Hz");
    }
}
