package letrain.visitor;

import java.util.List;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import letrain.mvp.Model;

/**
 * Base class for specialized 3D sub-renderers.
 * Holds shared state and common utilities used by the Gdx3D rendering system.
 */
public abstract class BaseSubRenderer implements Visitor {
    protected final Gdx3DResourceContext resourceContext;
    protected final List<ModelInstance> instances;
    protected final List<ModelInstance> transparentInstances;
    protected final List<Gdx3DRenderer.VehicleLabel> labels;
    
    protected Model modelRef;
    protected Camera camera;
    protected float animationAlpha = 1.0f;
    protected boolean isXRayActive = false;

    public BaseSubRenderer(Gdx3DResourceContext resourceContext, 
                          List<ModelInstance> instances, 
                          List<ModelInstance> transparentInstances,
                          List<Gdx3DRenderer.VehicleLabel> labels) {
        this.resourceContext = resourceContext;
        this.instances = instances;
        this.transparentInstances = transparentInstances;
        this.labels = labels;
    }

    public void updateState(Model modelRef, Camera camera, float animationAlpha, boolean isXRayActive) {
        this.modelRef = modelRef;
        this.camera = camera;
        this.animationAlpha = animationAlpha;
        this.isXRayActive = isXRayActive;
    }

    // Default empty implementations for Visitor methods
    @Override public void visitEconomyManager(letrain.economy.EconomyManager economyManager) {}
    @Override public void visitModel(Model model) {}
    @Override public void visitRailMap(letrain.map.impl.RailMap map) {}
    @Override public void visitRailTrack(letrain.track.rail.RailTrack track) {}
    @Override public void visitForkRailTrack(letrain.track.rail.ForkRailTrack track) {}
    @Override public void visitTunnelRailTrack(letrain.track.rail.TunnelRailTrack track) {}
    @Override public void visitLocomotive(letrain.vehicle.impl.rail.Locomotive locomotive) {}
    @Override public void visitWagon(letrain.vehicle.impl.rail.Wagon wagon) {}
    @Override public void visitCursor(letrain.vehicle.impl.Cursor cursor) {}
    @Override public void visitSensor(letrain.track.Sensor sensor) {}
    @Override public void visitSemaphore(letrain.track.RailSemaphore semaphore) {}
    @Override public void visitStation(letrain.track.Station Station) {}
    @Override public void visitGroundMap(letrain.ground.GroundMap groundMap) {}
    @Override public void visitGround(letrain.ground.Ground ground) {}
    @Override public void visitBridgeGateRailTrack(letrain.track.rail.BridgeGateRailTrack bridgeGateRailTrack) {}
    @Override public void visitBridgeRailTrack(letrain.track.rail.BridgeRailTrack bridgeRailTrack) {}
    @Override public void visitTunnelGateRailTrack(letrain.track.rail.TunnelGateRailTrack tunnelGateRailTrack) {}

    protected boolean isVisible(letrain.map.Point pos) {
        if (camera == null) return true;
        return camera.frustum.boundsInFrustum(pos.getX() + 0.5f, 0.5f, pos.getY() + 0.5f, 0.5f, 0.5f, 0.5f);
    }

    protected boolean isConnected(letrain.track.Track track, letrain.map.Dir dir) {
        letrain.track.Track neighbor = track.getConnected(dir);
        if (neighbor == null)
            return false;
        return neighbor.getRouter().getDir(dir.inverse()) != null;
    }

    protected letrain.map.Dir getValidOrientation(letrain.track.rail.RailTrack track) {
        letrain.map.Dir dir = track.getFirstOpenDir();
        if (dir == null)
            return letrain.map.Dir.N;
        return dir;
    }
}
