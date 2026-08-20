package letrain.visitor.gdx3d;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import letrain.visitor.Visitor;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.impl.RailMap;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Wagon;

public class Gdx3DRenderer implements Visitor {
    private final List<ModelInstance> instances = new ArrayList<>();
    private final List<ModelInstance> transparentInstances = new ArrayList<>();
    private final List<VehicleLabel> labels = new ArrayList<>();
    private final com.badlogic.gdx.utils.Pool<VehicleLabel> labelPool = new com.badlogic.gdx.utils.Pool<VehicleLabel>() {
        @Override
        protected VehicleLabel newObject() {
            return new VehicleLabel(new com.badlogic.gdx.math.Vector3(), "", new com.badlogic.gdx.math.Vector3());
        }
    };

    public void addLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal, com.badlogic.gdx.math.Vector3 up, com.badlogic.gdx.graphics.Color color, float scale) {
        VehicleLabel l = labelPool.obtain();
        l.pos.set(pos);
        l.text = text;
        l.normal.set(normal);
        if (up != null) {
            if (l.up == null) l.up = new com.badlogic.gdx.math.Vector3();
            l.up.set(up);
        } else {
            l.up = null;
        }
        l.color = color;
        l.scale = scale;
        labels.add(l);
    }

    private final Gdx3DResourceContext resourceContext;
    private final TrackRenderer trackRenderer;
    private final VehicleRenderer vehicleRenderer;
    private final InfrastructureRenderer infrastructureRenderer;
    private final GroundRenderer groundRenderer;

    private float animationAlpha = 1.0f;
    private boolean isXRayActive = false;

    public Gdx3DRenderer(Gdx3DResourceContext resourceContext) {
        this.resourceContext = resourceContext;
        this.trackRenderer = new TrackRenderer(resourceContext, instances, transparentInstances, labels);
        this.vehicleRenderer = new VehicleRenderer(resourceContext, instances, transparentInstances, labels);
        this.infrastructureRenderer = new InfrastructureRenderer(resourceContext, instances, transparentInstances, labels, trackRenderer);
        this.groundRenderer = new GroundRenderer(resourceContext, instances, transparentInstances, labels);
        
        this.trackRenderer.setParentRenderer(this);
        this.vehicleRenderer.setParentRenderer(this);
        this.infrastructureRenderer.setParentRenderer(this);
        this.groundRenderer.setParentRenderer(this);
    }

    public static class VehicleLabel {
        public com.badlogic.gdx.math.Vector3 pos;
        public String text;
        public com.badlogic.gdx.math.Vector3 normal;
        public com.badlogic.gdx.math.Vector3 up;
        public com.badlogic.gdx.graphics.Color color;
        public float scale = 1.0f;

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal) {
            this(pos, text, normal, com.badlogic.gdx.graphics.Color.WHITE);
        }

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal,
                com.badlogic.gdx.graphics.Color color) {
            this(pos, text, normal, null, color);
        }

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal,
                com.badlogic.gdx.math.Vector3 up,
                com.badlogic.gdx.graphics.Color color) {
            this(pos, text, normal, up, color, 1.0f);
        }

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal,
                com.badlogic.gdx.math.Vector3 up,
                com.badlogic.gdx.graphics.Color color,
                float scale) {
            this.pos = pos;
            this.text = text;
            this.normal = normal;
            this.up = up;
            this.color = color;
            this.scale = scale;
        }
    }

    public Gdx3DResourceContext getResourceContext() {
        return resourceContext;
    }

    public List<ModelInstance> getInstances() {
        return instances;
    }

    public List<ModelInstance> getTransparentInstances() {
        return transparentInstances;
    }

    public List<VehicleLabel> getLabels() {
        return labels;
    }

    public void setAnimationAlpha(float alpha) {
        this.animationAlpha = alpha;
    }

    public void init() {
    }

    public void clear() {
        instances.clear();
        transparentInstances.clear();
        for (VehicleLabel l : labels) {
            labelPool.free(l);
        }
        labels.clear();
        resourceContext.freeAllInstances();
    }

    public void visitGroundPlane(ModelInstance ground) {
        instances.add(ground);
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
    }

    private com.badlogic.gdx.graphics.Camera camera;

    @Override
    public void visitModel(Model model) {
        if (this.camera != null) {
            visitModel(model, this.camera);
        }
    }

    public void visitModel(Model model, com.badlogic.gdx.graphics.Camera camera) {
        this.camera = camera;
        this.isXRayActive = model.isXRayActive();
        clear();

        // X-RAY Detection: Are we inside any mountain or tunnel?
        if (model.getMode() == Model.GameMode.RAILS) {
            letrain.map.Point cp = model.getCursor().getPosition();
            Integer terrain = model.getGroundMap().getValueAt(cp);
            if (terrain != null && terrain == GroundMap.ROCK) {
                isXRayActive = true;
            } else {
                RailTrack rt = model.getCursorRailTrack();
                if (rt instanceof TunnelGateRailTrack) {
                    isXRayActive = true;
                }
            }
        }

        trackRenderer.updateState(model, camera, animationAlpha, isXRayActive);
        vehicleRenderer.updateState(model, camera, animationAlpha, isXRayActive);
        infrastructureRenderer.updateState(model, camera, animationAlpha, isXRayActive);
        groundRenderer.updateState(model, camera, animationAlpha, isXRayActive);

        // DYNAMIC SPATIAL OPTIMIZATION: Calculate the bounding box of the camera frustum on the ground plane (Y=0)
        // We take the 8 corners of the frustum and find the min/max X and Z.
        float minX_f = Float.MAX_VALUE;
        float maxX_f = -Float.MAX_VALUE;
        float minZ_f = Float.MAX_VALUE;
        float maxZ_f = -Float.MAX_VALUE;

        for (com.badlogic.gdx.math.Vector3 v : camera.frustum.planePoints) {
            minX_f = Math.min(minX_f, v.x);
            maxX_f = Math.max(maxX_f, v.x);
            minZ_f = Math.min(minZ_f, v.z);
            maxZ_f = Math.max(maxZ_f, v.z);
        }

        // Add a small margin to avoid popping at the edges
        int margin = 2;
        int minX = (int) Math.floor(minX_f) - margin;
        int maxX = (int) Math.ceil(maxX_f) + margin;
        int minY = (int) Math.floor(minZ_f) - margin;
        int maxY = (int) Math.ceil(maxZ_f) + margin;

        // Cap the range to avoid extreme values if camera is looking at horizon
        int maxRange = 150; 
        int camX = (int) camera.position.x;
        int camZ = (int) camera.position.z;
        minX = Math.max(minX, camX - maxRange);
        maxX = Math.min(maxX, camX + maxRange);
        minY = Math.max(minY, camZ - maxRange);
        maxY = Math.min(maxY, camZ + maxRange);

        model.getGroundMap().forEachInRange(minX, minY, maxX, maxY, groundRenderer::visitGround);
        model.getRailMap().forEachInRange(minX, minY, maxX, maxY, track -> track.accept(this));
        
        model.getSensors().forEach(t -> {
            if (isVisible(t.getPosition())) t.accept(infrastructureRenderer);
        });
        model.getSemaphores().forEach(t -> {
            if (isVisible(t.getPosition())) t.accept(infrastructureRenderer);
        });
        model.getWagons().forEach(t -> {
            if (isVisible(t.getPosition())) t.accept(vehicleRenderer);
        });
        model.getLocomotives().forEach(t -> {
            if (isVisible(t.getPosition())) t.accept(vehicleRenderer);
        });
        model.getStations().forEach(t -> {
            if (isVisible(t.getPosition())) t.accept(infrastructureRenderer);
        });
        visitCursor(model.getCursor());
    }

    private boolean isVisible(letrain.map.Point pos) {
        if (camera == null) return true;
        return camera.frustum.boundsInFrustum(pos.getX() + 0.5f, 0.5f, pos.getY() + 0.5f, 0.5f, 0.5f, 0.5f);
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        vehicleRenderer.visitLocomotive(locomotive);
    }

    @Override
    public void visitWagon(Wagon wagon) {
        vehicleRenderer.visitWagon(wagon);
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.accept(trackRenderer);
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        trackRenderer.visitRailTrack(track);
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        infrastructureRenderer.visitForkRailTrack(track);
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        trackRenderer.visitTunnelRailTrack(track);
    }

    @Override
    public void visitSensor(Sensor sensor) {
        infrastructureRenderer.visitSensor(sensor);
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        infrastructureRenderer.visitSemaphore(semaphore);
    }

    @Override
    public void visitStation(Station station) {
        infrastructureRenderer.visitStation(station);
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
        groundMap.accept(groundRenderer);
    }

    @Override
    public void visitGround(Ground ground) {
        groundRenderer.visitGround(ground);
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        trackRenderer.visitRailTrack(bridgeGateRailTrack);
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        trackRenderer.visitRailTrack(bridgeRailTrack);
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        infrastructureRenderer.visitTunnelGateRailTrack(tunnelGateRailTrack);
    }

    @Override
    public void visitCursor(Cursor cursor) {
        infrastructureRenderer.visitCursor(cursor);
    }

    public void dispose() {
        // resourceContext is disposed by GraphicPresenter
    }
}
