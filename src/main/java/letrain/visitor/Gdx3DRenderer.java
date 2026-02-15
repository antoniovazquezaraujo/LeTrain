package letrain.visitor;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
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
    private com.badlogic.gdx.graphics.g3d.Model wagonModel;
    private com.badlogic.gdx.graphics.g3d.Model highlightModel;

    public static class VehicleLabel {
        public com.badlogic.gdx.math.Vector3 pos;
        public String text;

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text) {
            this.pos = pos;
            this.text = text;
        }
    }

    private List<VehicleLabel> labels = new ArrayList<>();
    private Model modelRef;

    public List<VehicleLabel> getLabels() {
        return labels;
    }

    public void init() {
        if (modelBuilder == null) {
            modelBuilder = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            // Medio tramo de vía (perfil cuadrado/rectangular de madera, longitud base 0.7)
            trackModel = modelBuilder.createBox(0.5f, 0.2f, 0.7f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.25f, 0.25f, 0.25f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
            // Cursor en forma de bloque triangular (prisma triangular plano)
            // Altura 0.2f para que tenga el mismo grosor que la vía
            cursorModel = modelBuilder.createCylinder(0.8f, 0.2f, 0.8f, 3,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
            // Locomotora simple (Bloque Negro)
            locomotiveModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.BLACK)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Vagón simple (Bloque Azul)
            wagonModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.BLUE)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Modelo de resaltado (Bloque Amarillo)
            highlightModel = modelBuilder.createBox(0.82f, 0.82f, 0.82f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
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
        this.modelRef = model;
        labels.clear();
        // Ya no limpiamos instances aquí, lo hace la vista antes de empezar
        model.getGroundMap().accept(this);
        model.getRailMap().accept(this);
        model.getLocomotives().forEach(l -> l.accept(this));
        model.getWagons().forEach(w -> w.accept(this));
        visitCursor(model.getCursor());
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(this::visitRailTrack);
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        // Renderizamos cada ruta del tramo como dos medios segmentos
        // Esto permite que el raíl se "doble" en el centro para formar curvas
        track.forEach(route -> {
            drawHalfTrack(track.getPosition(), route.getKey());
            drawHalfTrack(track.getPosition(), route.getValue());
        });

        // Fallback: si no hay rutas, usamos la dirección abierta
        if (track.getNumRoutes() == 0) {
            letrain.map.Dir d = track.getFirstOpenDir();
            if (d != null) {
                drawHalfTrack(track.getPosition(), d);
            }
        }
    }

    private void drawHalfTrack(letrain.map.Point pos, letrain.map.Dir d) {
        float dx = getDirX(d);
        float dz = getDirZ(d);
        float magnitude = (float) Math.sqrt(dx * dx + dz * dz);

        // Ángulo hacia la cara del tile
        float angle = (float) Math.atan2(dx, dz) * MathUtils.radiansToDegrees;

        ModelInstance instance = new ModelInstance(trackModel);
        // Escala proporcional a la distancia (0.5 para rectas, ~0.707 para diagonales)
        float scale = magnitude / 0.5f;

        // Posición: Centro de la celda (pos + 0.5) + desplazamiento hacia la cara
        instance.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f),
                0.05f,
                pos.getY() + 0.5f + (dz / 2f));
        instance.transform.rotate(0, 1, 0, angle);
        instance.transform.scale(1, 1, scale);
        instances.add(instance);
    }

    @Override
    public void visitCursor(Cursor cursor) {
        // Determinamos el color según el modo
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
            default:
                break;
        }

        letrain.map.Dir d = cursor.getDir();
        letrain.map.Point pos = cursor.getPosition();
        float dx = getDirX(d);
        float dz = getDirZ(d);
        float angle = (float) Math.atan2(dx, dz) * MathUtils.radiansToDegrees;

        ModelInstance instance = new ModelInstance(cursorModel);
        instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));

        // El prisma triangular ya está "tumbado" (caras planas Ry), solo rotamos yaw
        // Aplicamos un desfase de -90 para que el vértice apunte a 'angle'
        instance.transform.setToTranslation(pos.getX() + 0.5f, 0.25f, pos.getY() + 0.5f);
        instance.transform.rotate(0, 1, 0, angle - 90f);
        instances.add(instance);
    }

    private float getDirX(letrain.map.Dir d) {
        switch (d) {
            case E:
            case NE:
            case SE:
                return 0.5f;
            case W:
            case NW:
            case SW:
                return -0.5f;
            default:
                return 0f;
        }
    }

    private float getDirZ(letrain.map.Dir d) {
        switch (d) {
            case S:
            case SE:
            case SW:
                return 0.5f;
            case N:
            case NE:
            case NW:
                return -0.5f;
            default:
                return 0f;
        }
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        visitRailTrack(track);
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        visitRailTrack(track);
    }

    public void dispose() {
        if (trackModel != null)
            trackModel.dispose();
        if (cursorModel != null)
            cursorModel.dispose();
        if (locomotiveModel != null)
            locomotiveModel.dispose();
        if (wagonModel != null)
            wagonModel.dispose();
        if (highlightModel != null)
            highlightModel.dispose();
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        // ¿Debería resaltarse? (Modo LINK)
        boolean highlight = false;
        if (modelRef != null && modelRef.getMode() == Model.GameMode.LINK) {
            Locomotive selected = modelRef.getSelectedLocomotive();
            if (selected != null && selected.getTrain() != null) {
                for (letrain.vehicle.impl.Linker l : selected.getTrain().getLinkersToJoin()) {
                    if (l == locomotive) {
                        highlight = true;
                        break;
                    }
                }
            }
        }

        ModelInstance instance = new ModelInstance(highlight ? highlightModel : locomotiveModel);
        instance.transform.setToTranslation(locomotive.getPosition().getX() + 0.5f, 0.6f,
                locomotive.getPosition().getY() + 0.5f);
        float angle = locomotive.getDir().getValue() * 45f;
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // Añadir etiqueta
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(locomotive.getPosition().getX() + 0.5f, 1.2f,
                        locomotive.getPosition().getY() + 0.5f),
                locomotive.getAspect()));
    }

    @Override
    public void visitWagon(Wagon wagon) {
        // ¿Debería resaltarse? (Modo LINK)
        boolean highlight = false;
        if (modelRef != null && modelRef.getMode() == Model.GameMode.LINK) {
            Locomotive selected = modelRef.getSelectedLocomotive();
            if (selected != null && selected.getTrain() != null) {
                for (letrain.vehicle.impl.Linker l : selected.getTrain().getLinkersToJoin()) {
                    if (l == wagon) {
                        highlight = true;
                        break;
                    }
                }
            }
        }

        ModelInstance instance = new ModelInstance(highlight ? highlightModel : wagonModel);
        // Elevamos el centro de masa (0.6f) para que se sitúe sobre las vías
        instance.transform.setToTranslation(wagon.getPosition().getX() + 0.5f, 0.6f, wagon.getPosition().getY() + 0.5f);
        // Orientación según la dirección del modelo
        float angle = wagon.getDir().getValue() * 45f;
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // Añadir etiqueta
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(wagon.getPosition().getX() + 0.5f, 1.2f,
                        wagon.getPosition().getY() + 0.5f),
                wagon.getAspect()));
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
