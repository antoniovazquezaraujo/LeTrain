package letrain.visitor;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
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
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public class Gdx3DRenderer implements Visitor {
    private List<ModelInstance> instances = new ArrayList<>();

    public List<ModelInstance> getInstances() {
        return instances;
    }

    private com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder;
    private com.badlogic.gdx.graphics.g3d.Model trackModel;
    private com.badlogic.gdx.graphics.g3d.Model cursorModel;

    private com.badlogic.gdx.graphics.g3d.Model locomotiveModel;

    public void init() {
        if (modelBuilder == null) {
            modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            // Vías más gruesas y visibles
            trackModel = modelBuilder.createBox(0.9f, 0.1f, 0.9f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.25f, 0.25f, 0.25f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
            // Cursor base
            cursorModel = modelBuilder.createBox(1.1f, 1.1f, 1.1f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
            locomotiveModel = modelBuilder.createBox(0.8f, 1.2f, 1.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.6f, 0.4f, 0.2f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
        }
    }

    public void clear() {
        instances.clear();
    }

    public void visitGroundPlane(com.badlogic.gdx.graphics.g3d.Model ground) {
        instances.add(new ModelInstance(ground));
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
    }

    @Override
    public void visitModel(Model model) {
        model.getRailMap().accept(this);
        for (Locomotive l : model.getLocomotives())
            l.accept(this);
        for (Wagon w : model.getWagons())
            w.accept(this);
        model.getCursor().accept(this);
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(this::visitRailTrack);
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        ModelInstance instance = new ModelInstance(trackModel);
        // Altura realista: la caja mide 0.1f, así que 0.05f la deja a ras de suelo
        instance.transform.setToTranslation(track.getPosition().getX(), 0.05f, track.getPosition().getY());
        instances.add(instance);
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        visitRailTrack(track);
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        visitRailTrack(track);
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        ModelInstance instance = new ModelInstance(locomotiveModel);
        instance.transform.setToTranslation(locomotive.getPosition().getX(), 0.6f, locomotive.getPosition().getY());
        // Rotar según la dirección: cada unidad de valor en Dir son 45 grados (sentido
        // antihorario)
        float angle = locomotive.getDir().getValue() * 45f;
        instance.transform.rotate(0, 1, 0, -angle); // Ajuste de signo para rotación
        instances.add(instance);
    }

    @Override
    public void visitWagon(Wagon wagon) {
        ModelInstance instance = new ModelInstance(locomotiveModel); // Reusar modelo para vagón
        instance.transform.setToTranslation(wagon.getPosition().getX(), 0.6f, wagon.getPosition().getY());
        float angle = wagon.getDir().getValue() * 45f;
        instance.transform.rotate(0, 1, 0, -angle); // Ajuste de signo para rotación
        instances.add(instance);
    }

    @Override
    public void visitCursor(Cursor cursor) {
        ModelInstance instance = new ModelInstance(cursorModel);

        // Cambiar color según el modo para dar feedback
        com.badlogic.gdx.graphics.Color color = com.badlogic.gdx.graphics.Color.YELLOW;
        switch (cursor.getMode()) {
            case DRAWING:
                color = com.badlogic.gdx.graphics.Color.GREEN;
                break;
            case ERASING:
                color = com.badlogic.gdx.graphics.Color.RED;
                break;
            case MAKING_TRACKS:
                color = com.badlogic.gdx.graphics.Color.ORANGE;
                break;
        }

        instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));
        instance.transform.setToTranslation(cursor.getPosition().getX(), 0.5f, cursor.getPosition().getY());
        instances.add(instance);
    }

    @Override
    public void visitSensor(Sensor sensor) {
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
    }

    @Override
    public void visitStation(Station station) {
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
    }

    @Override
    public void visitGround(Ground ground) {
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        visitRailTrack(bridgeGateRailTrack);
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        visitRailTrack(bridgeRailTrack);
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        visitRailTrack(tunnelGateRailTrack);
    }
}
