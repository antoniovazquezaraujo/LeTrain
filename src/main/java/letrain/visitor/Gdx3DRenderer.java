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
    private com.badlogic.gdx.graphics.g3d.Model railModel;
    private com.badlogic.gdx.graphics.g3d.Model inactiveRailModel;
    private com.badlogic.gdx.graphics.g3d.Model cursorModel;
    private com.badlogic.gdx.graphics.g3d.Model locomotiveModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonModel;
    private com.badlogic.gdx.graphics.g3d.Model highlightModel;
    private com.badlogic.gdx.graphics.g3d.Model forkModel;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model waterModel;
    private com.badlogic.gdx.graphics.g3d.Model mountainModel;
    private com.badlogic.gdx.graphics.g3d.Model ballastModel;

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
            // Raíl fino (perfil rectangular, longitud base 0.7)
            railModel = modelBuilder.createBox(0.06f, 0.2f, 0.7f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.8f, 0.8f, 0.85f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Raíl inactivo (mucho más oscuro/negro para que se note el cambio)
            inactiveRailModel = modelBuilder.createBox(0.06f, 0.2f, 0.7f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.1f, 0.1f, 0.12f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Cursor en forma de bloque triangular (prisma triangular plano)
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

            // Modelo de resaltado (Caja amarilla más grande para que destaque)
            highlightModel = modelBuilder.createBox(1.0f, 0.15f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Modelo de indicador de desvío (Bloque Rojo pequeño)
            forkModel = modelBuilder.createBox(0.2f, 0.25f, 0.2f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.RED)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Modelos de terreno (planos de 1x1)
            groundModel = modelBuilder.createBox(1.0f, 0.01f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.4f, 0.6f, 0.3f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            waterModel = modelBuilder.createBox(1.0f, 0.01f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.2f, 0.4f, 0.8f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            mountainModel = modelBuilder.createBox(1.0f, 0.01f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.5f, 0.4f, 0.3f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Balasto (piedras grises debajo de los raíles)
            ballastModel = modelBuilder.createBox(0.5f, 0.1f, 0.7f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 1f))),
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
        map.forEach(track -> track.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        // Renderizamos cada ruta del tramo como dos medios segmentos paralelos
        track.forEach(route -> {
            drawHalfTrack(track.getPosition(), route.getFirst(), true);
            drawHalfTrack(track.getPosition(), route.getSecond(), true);
        });

        // Fallback: si no hay rutas, usamos la dirección abierta
        if (track.getNumRoutes() == 0) {
            letrain.map.Dir d = track.getFirstOpenDir();
            if (d != null) {
                drawHalfTrack(track.getPosition(), d, true);
            }
        }
    }

    private void drawHalfTrack(letrain.map.Point pos, letrain.map.Dir d, boolean active) {
        float dx = getDirX(d);
        float dz = getDirZ(d);
        float magnitude = (float) Math.sqrt(dx * dx + dz * dz);

        // Ángulo hacia la cara del tile
        float angle = (float) Math.atan2(dx, dz) * MathUtils.radiansToDegrees;

        // Escala proporcional a la distancia (0.5 para rectas, ~0.707 para diagonales)
        float scale = magnitude / 0.5f;

        // Primero dibujamos el balasto (piedras grises)
        ModelInstance ballast = new ModelInstance(ballastModel);
        ballast.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f),
                0.03f,
                pos.getY() + 0.5f + (dz / 2f));
        ballast.transform.rotate(0, 1, 0, angle);
        ballast.transform.scale(1, 1, scale);
        instances.add(ballast);

        // Calculamos el vector perpendicular para el desplazamiento lateral de los
        // raíles
        // Normalizado es (dx/magnitude, dz/magnitude)
        // Perpendicular es (-dz/magnitude, dx/magnitude)
        float offX = (-dz / magnitude) * 0.15f;
        float offZ = (dx / magnitude) * 0.15f;

        com.badlogic.gdx.graphics.g3d.Model activeModel = active ? railModel : inactiveRailModel;

        // Raíl izquierdo
        ModelInstance railL = new ModelInstance(activeModel);
        railL.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f) + offX,
                0.08f,
                pos.getY() + 0.5f + (dz / 2f) + offZ);
        railL.transform.rotate(0, 1, 0, angle);
        railL.transform.scale(1, 1, scale);
        instances.add(railL);

        // Raíl derecho
        ModelInstance railR = new ModelInstance(activeModel);
        railR.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f) - offX,
                0.08f,
                pos.getY() + 0.5f + (dz / 2f) - offZ);
        railR.transform.rotate(0, 1, 0, angle);
        railR.transform.scale(1, 1, scale);
        instances.add(railR);
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
            case MOVING:
                color = com.badlogic.gdx.graphics.Color.CYAN;
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
        // Resaltado si es el desvío seleccionado
        if (modelRef != null && modelRef.getMode() == Model.GameMode.FORKS) {
            if (modelRef.getSelectedFork() != null
                    && modelRef.getSelectedFork().getPosition().equals(track.getPosition())) {
                ModelInstance highlight = new ModelInstance(highlightModel);
                // Situamos el resaltado en la base, un poco más alto para evitar z-fighting con
                // el suelo
                highlight.transform.setToTranslation(track.getPosition().getX() + 0.5f, 0.04f,
                        track.getPosition().getY() + 0.5f);
                instances.add(highlight);
            }
        }

        // Determinamos la ruta activa
        letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> activeRoute = track.isUsingAlternativeRoute()
                ? track.getAlternativeRoute()
                : track.getOriginalRoute();

        // Solo dibujamos la ruta activa (brillante)
        if (activeRoute != null) {
            drawHalfTrack(track.getPosition(), activeRoute.getFirst(), true);
            drawHalfTrack(track.getPosition(), activeRoute.getSecond(), true);
        }

        // Indicador de ruta activa (bloque rojo)
        if (activeRoute != null) {
            ModelInstance indicator = new ModelInstance(forkModel);
            letrain.map.Dir d = activeRoute.getFirst(); // Tomamos una de las direcciones de la ruta para posicionar
            float ox = getDirX(d);
            float oz = getDirZ(d);
            indicator.transform.setToTranslation(track.getPosition().getX() + 0.5f + ox * 0.4f, 0.25f,
                    track.getPosition().getY() + 0.5f + oz * 0.4f);
            instances.add(indicator);
        }
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        visitRailTrack(track);
    }

    public void dispose() {
        if (railModel != null)
            railModel.dispose();
        if (inactiveRailModel != null)
            inactiveRailModel.dispose();
        if (cursorModel != null)
            cursorModel.dispose();
        if (locomotiveModel != null)
            locomotiveModel.dispose();
        if (wagonModel != null)
            wagonModel.dispose();
        if (highlightModel != null)
            highlightModel.dispose();
        if (forkModel != null)
            forkModel.dispose();
        if (groundModel != null)
            groundModel.dispose();
        if (waterModel != null)
            waterModel.dispose();
        if (mountainModel != null)
            mountainModel.dispose();
        if (ballastModel != null)
            ballastModel.dispose();
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
        groundMap.forEach(ground -> visitGround(ground));
    }

    @Override
    public void visitGround(Ground ground) {
        int type = ground.getType();
        com.badlogic.gdx.graphics.g3d.Model model;

        switch (type) {
            case GroundMap.GROUND:
                model = groundModel;
                break;
            case GroundMap.WATER:
                model = waterModel;
                break;
            case GroundMap.ROCK:
                model = mountainModel;
                break;
            default:
                model = groundModel;
                break;
        }

        if (model != null) {
            ModelInstance instance = new ModelInstance(model);
            instance.transform.setToTranslation(
                    ground.getPosition().getX() + 0.5f,
                    0.0f,
                    ground.getPosition().getY() + 0.5f);
            instances.add(instance);
        }
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
