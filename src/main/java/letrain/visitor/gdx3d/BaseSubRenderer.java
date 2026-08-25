package letrain.visitor.gdx3d;

import java.util.List;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import letrain.mvp.Model;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Wagon;
import letrain.visitor.Visitor;

/**
 * Base class for specialized 3D sub-renderers.
 * Holds shared state and common utilities used by the Gdx3D rendering system.
 */
public abstract class BaseSubRenderer implements Visitor {
    protected final Gdx3DResourceContext resourceContext;
    protected final List<ModelInstance> instances;
    protected final List<ModelInstance> transparentInstances;
    protected final List<Gdx3DRenderer.VehicleLabel> labels;
    protected Gdx3DRenderer parentRenderer;
    
    protected Model modelRef;
    protected Camera camera;
    protected float animationAlpha = 1.0f;
    protected boolean isXRayActive = false;

    // Temporary objects for reuse in render loops
    protected final com.badlogic.gdx.math.Vector3 v1 = new com.badlogic.gdx.math.Vector3();
    protected final com.badlogic.gdx.math.Vector3 v2 = new com.badlogic.gdx.math.Vector3();
    protected final com.badlogic.gdx.math.Vector3 v3 = new com.badlogic.gdx.math.Vector3();
    protected final com.badlogic.gdx.graphics.Color c1 = new com.badlogic.gdx.graphics.Color();

    public BaseSubRenderer(Gdx3DResourceContext resourceContext, 
                          List<ModelInstance> instances, 
                          List<ModelInstance> transparentInstances,
                          List<Gdx3DRenderer.VehicleLabel> labels) {
        this.resourceContext = resourceContext;
        this.instances = instances;
        this.transparentInstances = transparentInstances;
        this.labels = labels;
    }

    public void setParentRenderer(Gdx3DRenderer parentRenderer) {
        this.parentRenderer = parentRenderer;
    }

    protected void addLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal, com.badlogic.gdx.math.Vector3 up, com.badlogic.gdx.graphics.Color color, float scale) {
        if (parentRenderer != null) {
            parentRenderer.addLabel(pos, text, normal, up, color, scale);
        } else {
            labels.add(new Gdx3DRenderer.VehicleLabel(pos, text, normal, up, color, scale));
        }
    }

    protected void addLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal) {
        addLabel(pos, text, normal, null, com.badlogic.gdx.graphics.Color.WHITE, 1.0f);
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
    @Override public void visitLocomotive(Locomotive locomotive) {}
    @Override public void visitWagon(Wagon wagon) {}
    @Override public void visitCursor(Cursor cursor) {}
    @Override public void visitSensor(letrain.track.Sensor sensor) {}
    @Override public void visitSemaphore(letrain.track.RailSemaphore semaphore) {}
    @Override public void visitStation(letrain.track.Station station) {}
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

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {}
}
