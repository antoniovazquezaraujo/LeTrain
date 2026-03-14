package letrain.mvp.impl;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Se encarga de gestionar la cámara 3D (modos ORBIT, CAB, MAP) a partir del estado del modelo.
 * No toca audio ni renderizado; solo actualiza una {@link PerspectiveCamera}.
 */
public class CameraController {

    public enum CameraMode {
        ORBIT,
        CAB,
        MAP
    }

    private final Model model;
    private PerspectiveCamera cam;

    private CameraMode cameraMode = CameraMode.ORBIT;

    // Estado de cámara ORBIT/MAP
    private final Vector3 camTarget = new Vector3();
    private float cameraAngle = 45f;
    private float cameraDistance = 8.5f;
    private float targetCameraAngle = 45f;
    private float targetCameraDistance = 8.5f;
    private float mapCameraHeight = 15f;

    // Estado de cámara CAB
    private final Vector2 currentCabDirection = new Vector2(0, 1);

    public CameraController(Model model) {
        this.model = model;
    }

    public PerspectiveCamera init(int viewportWidth, int viewportHeight) {
        if (cam != null) {
            return cam;
        }
        cam = new PerspectiveCamera(67, viewportWidth, viewportHeight);
        letrain.map.Point startPos = model.getCursor().getPosition();
        cam.position.set(startPos.getX() + 20f, 20f, startPos.getY() + 20f);
        cam.lookAt(startPos.getX() + 0.5f, 0, startPos.getY() + 0.5f);
        cam.near = 1f;
        cam.far = 1000f;
        cam.update();
        return cam;
    }

    public PerspectiveCamera getCamera() {
        return cam;
    }

    public CameraMode getMode() {
        return cameraMode;
    }

    public void setMode(CameraMode mode) {
        this.cameraMode = mode;
    }

    public void cycleMode(boolean hasLocomotives) {
        if (cameraMode == CameraMode.ORBIT) {
            cameraMode = hasLocomotives ? CameraMode.CAB : CameraMode.MAP;
        } else if (cameraMode == CameraMode.CAB) {
            cameraMode = CameraMode.MAP;
        } else {
            cameraMode = CameraMode.ORBIT;
        }
    }

    public void rotateOrbit(float deltaDegrees) {
        targetCameraAngle += deltaDegrees;
    }

    public void zoom(float delta) {
        if (cameraMode == CameraMode.MAP) {
            mapCameraHeight = MathUtils.clamp(mapCameraHeight + delta * 2f, 3f, 100f);
        } else if (cameraMode == CameraMode.ORBIT) {
            targetCameraDistance = MathUtils.clamp(targetCameraDistance + delta, 3f, 40f);
        }
    }

    public void zoomStep(float deltaStep) {
        if (cameraMode == CameraMode.MAP) {
            mapCameraHeight = MathUtils.clamp(mapCameraHeight + deltaStep, 3f, 100f);
        } else {
            targetCameraDistance = MathUtils.clamp(targetCameraDistance + deltaStep, 3f, 40f);
        }
    }

    public float getListenerAngle() {
        if (cam == null) {
            return 0f;
        }
        return (float) Math.atan2(cam.direction.z, cam.direction.x);
    }

    public void resize(int width, int height) {
        if (cam == null) {
            return;
        }
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }

    public void update(float alpha) {
        if (cam == null) {
            return;
        }
        float targetX;
        float targetZ;

        if ((model.getMode() == letrain.mvp.Model.GameMode.DRIVE
                || model.getMode() == letrain.mvp.Model.GameMode.LINK
                || model.getMode() == letrain.mvp.Model.GameMode.UNLINK)
                && model.getSelectedLocomotive() != null) {
            letrain.vehicle.impl.rail.Locomotive selected = model.getSelectedLocomotive();
            Vector2 interpPos = getInterpolatedPosition(selected, alpha);
            targetX = interpPos.x + 0.5f;
            targetZ = interpPos.y + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.FORKS && model.getSelectedFork() != null) {
            letrain.track.rail.ForkRailTrack selected = model.getSelectedFork();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.SEMAPHORES && model.getSelectedSemaphore() != null) {
            letrain.track.RailSemaphore selected = model.getSelectedSemaphore();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.STATIONS && model.getSelectedStation() != null) {
            letrain.track.Station selected = model.getSelectedStation();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else {
            letrain.map.Point cursorState = model.getCursor().getPosition();
            targetX = cursorState.getX() + 0.5f;
            targetZ = cursorState.getY() + 0.5f;
        }

        if (cameraMode == CameraMode.CAB) {
            updateCabCamera(alpha);
        }

        if (cameraMode == CameraMode.ORBIT) {
            updateOrbitCamera(targetX, targetZ);
        }

        if (cameraMode == CameraMode.MAP) {
            updateMapCamera(targetX, targetZ);
        }

        cam.update();
    }

    private void updateCabCamera(float alpha) {
        letrain.vehicle.impl.rail.Locomotive loco = model.getSelectedLocomotive();
        if (loco == null && !model.getLocomotives().isEmpty()) {
            loco = model.getLocomotives().get(0);
        }

        if (loco != null) {
            Vector2 interpPos = getInterpolatedPosition(loco, alpha);
            float x = interpPos.x + 0.5f;
            float z = interpPos.y + 0.5f;

            letrain.map.Dir d = loco.getDir();
            float dx = letrain.visitor.Gdx3DRenderer.getDirX(d);
            float dz = letrain.visitor.Gdx3DRenderer.getDirZ(d);

            Vector2 targetDir = new Vector2(dx, dz);
            currentCabDirection.lerp(targetDir, 0.05f).nor();

            float smoothDx = currentCabDirection.x;
            float smoothDz = currentCabDirection.y;

            float camX = x - smoothDx * 1.2f;
            float camY = 2.0f;
            float camZ = z - smoothDz * 1.2f;

            cam.position.set(camX, camY, camZ);
            cam.lookAt(x + smoothDx * 5f, 0.5f, z + smoothDz * 5f);
            cam.up.set(0, 1, 0);
        }
    }

    private void updateOrbitCamera(float targetX, float targetZ) {
        camTarget.lerp(new Vector3(targetX, 0, targetZ), 0.05f);

        cameraAngle = MathUtils.lerp(cameraAngle, targetCameraAngle, 0.1f);
        cameraDistance = MathUtils.lerp(cameraDistance, targetCameraDistance, 0.1f);

        float angleRad = cameraAngle * MathUtils.degreesToRadians;
        float camX = camTarget.x + cameraDistance * MathUtils.sin(angleRad);
        float camZ = camTarget.z + cameraDistance * MathUtils.cos(angleRad);
        float camY = Math.max(2.0f, cameraDistance * 0.7f);

        cam.position.set(camX, camY, camZ);
        cam.lookAt(camTarget);
        cam.up.set(0, 1, 0);
    }

    private void updateMapCamera(float targetX, float targetZ) {
        camTarget.lerp(new Vector3(targetX, 0, targetZ), 0.05f);
        cam.position.set(camTarget.x, mapCameraHeight, camTarget.z);
        cam.lookAt(camTarget.x, 0, camTarget.z);
        cam.up.set(0, 0, -1);
    }

    private Vector2 getInterpolatedPosition(letrain.vehicle.impl.rail.Locomotive locomotive, float alpha) {
        // Copiado desde la lógica original de Gdx3DView para mantener el mismo comportamiento visual.
        float x = locomotive.getPosition().getX();
        float y = locomotive.getPosition().getY();

        if (locomotive.getTotalTurns() >= 0) {
            float totalDelay = (float) locomotive.getTotalTurns() + 1.0f;
            float currentDelay = (float) locomotive.getTurns() + 1.0f - alpha;
            float progress = 1.0f - (currentDelay / totalDelay);

            if (progress < 0)
                progress = 0;
            if (progress > 1)
                progress = 1;

            letrain.track.Track currentTrack = locomotive.getTrack();
            if (currentTrack != null) {
                letrain.track.Track nextTrack = currentTrack.getConnected(locomotive.getDir());
                if (nextTrack != null) {
                    float nextX = nextTrack.getPosition().getX();
                    float nextY = nextTrack.getPosition().getY();

                    // Si la distancia es mayor a 1 (teletransporte/wrap), no interpolar
                    if (Math.abs(nextX - x) <= 1 && Math.abs(nextY - y) <= 1) {
                        x = x + (nextX - x) * progress;
                        y = y + (nextY - y) * progress;
                    }
                }
            }
        }
        return new Vector2(x, y);
    }
}

