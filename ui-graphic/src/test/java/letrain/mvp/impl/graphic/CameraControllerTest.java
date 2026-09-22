package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.PerspectiveCamera;
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
}
