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
    private com.badlogic.gdx.graphics.g3d.Model locomotiveHighlightModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonHighlightModel;
    private com.badlogic.gdx.graphics.g3d.Model forkModel;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model waterModel;
    private com.badlogic.gdx.graphics.g3d.Model mountainModel;
    private com.badlogic.gdx.graphics.g3d.Model ballastModel;
    private com.badlogic.gdx.graphics.g3d.Model bridgePillarModel;
    private com.badlogic.gdx.graphics.g3d.Model tunnelPortalModel;
    private com.badlogic.gdx.graphics.g3d.Model directionIndicatorModel;
    private letrain.map.impl.RailMap railMap; // Referencia al mapa de vías

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

            // Locomotora amarilla para modo LINK
            locomotiveHighlightModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Vagón amarillo para modo LINK
            wagonHighlightModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Indicador de dirección (cono verde que apunta hacia adelante)
            directionIndicatorModel = modelBuilder.createCone(0.3f, 0.4f, 0.3f, 8,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GREEN)),
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

            mountainModel = modelBuilder.createBox(1.0f, 1.2f, 1.0f,
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

            // Pilar de puente (columna vertical)
            bridgePillarModel = modelBuilder.createBox(0.3f, 0.5f, 0.3f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.4f, 0.35f, 0.3f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Portal de túnel (bloque negro del mismo tamaño que montaña)
            tunnelPortalModel = modelBuilder.createBox(1.0f, 1.2f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.15f, 0.15f, 0.15f, 1f))),
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
        this.railMap = (letrain.map.impl.RailMap) map; // Guardar referencia
        map.forEach(track -> track.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        // Renderizamos cada ruta del tramo como dos medios segmentos
        track.forEach(route -> {
            letrain.map.Dir d1 = route.getFirst();
            letrain.map.Dir d2 = route.getSecond();
            float shortenL1 = 1.0f;
            float shortenR1 = 1.0f;
            float shortenL2 = 1.0f;
            float shortenR2 = 1.0f;

            int dist = d1.angularDistance(d2);
            int absDist = Math.abs(dist);
            if (absDist >= 1 && absDist <= 3) {
                if (dist > 0) { // Giro a la izquierda -> Raíl interior es el izquierdo de d1 (railR) y derecho
                                // de d2 (railL)
                    shortenR1 = 0.75f; // Interior (Perfecto según usuario)
                    shortenL2 = 0.75f; // Interior
                    shortenL1 = 0.9f; // Exterior (Quitar un poquito)
                    shortenR2 = 0.9f; // Exterior
                } else if (dist < 0) { // Giro a la derecha -> Raíl interior es el derecho de d1 (railL) y izquierdo de
                                       // d2 (railR)
                    shortenL1 = 0.75f; // Interior
                    shortenR2 = 0.75f; // Interior
                    shortenR1 = 0.9f; // Exterior
                    shortenL2 = 0.9f; // Exterior
                }
            }
            drawHalfTrack(track.getPosition(), d1, true, shortenL1, shortenR1);
            drawHalfTrack(track.getPosition(), d2, true, shortenL2, shortenR2);
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
        drawHalfTrackElevated(pos, d, active, 0.0f, 1.0f, 1.0f);
    }

    private void drawHalfTrack(letrain.map.Point pos, letrain.map.Dir d, boolean active, float shortenL,
            float shortenR) {
        drawHalfTrackElevated(pos, d, active, 0.0f, shortenL, shortenR);
    }

    private void drawHalfTrackElevated(letrain.map.Point pos, letrain.map.Dir d, boolean active, float elevation,
            float shortenL, float shortenR) {
        float dx = getDirX(d);
        float dz = getDirZ(d);
        float magnitude = (float) Math.sqrt(dx * dx + dz * dz);

        // Ángulo hacia la cara del tile
        float angle = (float) Math.atan2(dx, dz) * MathUtils.radiansToDegrees;

        // Primero dibujamos el balasto (piedras grises)
        // Usamos el promedio de acortamiento para el balasto
        float shortenB = (shortenL + shortenR) / 2f;
        float scaleB = (magnitude * shortenB) / 0.5f;
        ModelInstance ballast = new ModelInstance(ballastModel);
        ballast.transform.setToTranslation(
                pos.getX() + 0.5f + (dx * (1f - shortenB / 2f)),
                0.03f + elevation,
                pos.getY() + 0.5f + (dz * (1f - shortenB / 2f)));
        ballast.transform.rotate(0, 1, 0, angle);
        ballast.transform.scale(1, 1, scaleB);
        instances.add(ballast);

        // Calculamos el vector perpendicular para el desplazamiento lateral de los
        // raíles
        // Normalizado es (dx/magnitude, dz/magnitude)
        // Perpendicular es (-dz/magnitude, dx/magnitude)
        float offX = (-dz / magnitude) * 0.15f;
        float offZ = (dx / magnitude) * 0.15f;

        com.badlogic.gdx.graphics.g3d.Model activeModel = active ? railModel : inactiveRailModel;

        // Raíl izquierdo
        float scale = magnitude / 0.5f; // Base scale for a full half-track
        ModelInstance railL = new ModelInstance(activeModel);
        // Ajuste de desplazamiento para mantener el extremo exterior fijo si se acorta
        // Si shorten < 1, el raíl se encoge hacia su centro. Para que solo se encoja
        // desde el centro del tile,
        // debemos desplazarlo hacia afuera por la mitad de la longitud perdida.
        float shiftX_L = dx * (1 - shortenL) / 2f;
        float shiftZ_L = dz * (1 - shortenL) / 2f;

        railL.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f) + offX + shiftX_L,
                0.08f + elevation,
                pos.getY() + 0.5f + (dz / 2f) + offZ + shiftZ_L);
        railL.transform.rotate(0, 1, 0, angle);
        railL.transform.scale(1, 1, scale * shortenL);
        instances.add(railL);

        // Raíl derecho
        float shiftX_R = dx * (1 - shortenR) / 2f;
        float shiftZ_R = dz * (1 - shortenR) / 2f;

        ModelInstance railR = new ModelInstance(activeModel);
        railR.transform.setToTranslation(
                pos.getX() + 0.5f + (dx / 2f) - offX + shiftX_R,
                0.08f + elevation,
                pos.getY() + 0.5f + (dz / 2f) - offZ + shiftZ_R);
        railR.transform.rotate(0, 1, 0, angle);
        railR.transform.scale(1, 1, scale * shortenR);
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
        letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> route = track.isUsingAlternativeRoute()
                ? track.getAlternativeRoute()
                : track.getOriginalRoute();

        if (route != null) {
            letrain.map.Dir d1 = route.getFirst();
            letrain.map.Dir d2 = route.getSecond();
            float shortenL1 = 1.0f;
            float shortenR1 = 1.0f;
            float shortenL2 = 1.0f;
            float shortenR2 = 1.0f;

            int dist = d1.angularDistance(d2);
            int absDist = Math.abs(dist);
            if (absDist >= 1 && absDist <= 3) {
                if (dist > 0) { // Giro a la izquierda -> Raíl interior es el izquierdo de d1 (railR) y derecho
                                // de d2 (railL)
                    shortenR1 = 0.75f; // Interior
                    shortenL2 = 0.75f; // Interior
                    shortenL1 = 0.9f; // Exterior
                    shortenR2 = 0.9f; // Exterior
                } else if (dist < 0) { // Giro a la derecha -> Raíl interior es el derecho de d1 (railL) y izquierdo de
                                       // d2 (railR)
                    shortenL1 = 0.75f; // Interior
                    shortenR2 = 0.75f; // Interior
                    shortenR1 = 0.9f; // Exterior
                    shortenL2 = 0.9f; // Exterior
                }
            }
            drawHalfTrack(track.getPosition(), d1, true, shortenL1, shortenR1);
            drawHalfTrack(track.getPosition(), d2, true, shortenL2, shortenR2);
        }

        // Si la vía está ocupada destacamos el vehículo
        if (track.getLinker() != null) {
            // TODO: Implementar resaltado de vehículo en desvío
        }

        // Indicador de ruta activa (bloque rojo)
        if (route != null) {
            ModelInstance indicator = new ModelInstance(forkModel);
            letrain.map.Dir d = route.getFirst(); // Tomamos una de las direcciones de la ruta para posicionar
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
        if (bridgePillarModel != null)
            bridgePillarModel.dispose();
        if (tunnelPortalModel != null)
            tunnelPortalModel.dispose();
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

        ModelInstance instance = new ModelInstance(highlight ? locomotiveHighlightModel : locomotiveModel);
        instance.transform.setToTranslation(locomotive.getPosition().getX() + 0.5f, 0.6f,
                locomotive.getPosition().getY() + 0.5f);
        float angle = locomotive.getDir().getValue() * 45f;
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // Añadir indicador de dirección: cara amarilla en el frente de la locomotora
        com.badlogic.gdx.graphics.g3d.Material yellowFaceMaterial = new com.badlogic.gdx.graphics.g3d.Material(
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                        .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW));

        ModelInstance frontFace = new ModelInstance(
                modelBuilder.createBox(0.02f, 0.82f, 0.82f, yellowFaceMaterial,
                        com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                                | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal));

        // Posicionar la cara en el frente de la locomotora
        frontFace.transform.setToTranslation(
                locomotive.getPosition().getX() + 0.5f,
                0.6f,
                locomotive.getPosition().getY() + 0.5f);
        frontFace.transform.rotate(0, 1, 0, angle); // Rotar según dirección de locomotora
        frontFace.transform.translate(0.41f, 0, 0); // Mover hacia el frente (en X local)
        instances.add(frontFace);

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

        ModelInstance instance = new ModelInstance(highlight ? wagonHighlightModel : wagonModel);
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
        float yPosition = 0.0f;

        switch (type) {
            case GroundMap.GROUND:
                model = groundModel;
                yPosition = 0.0f;
                break;
            case GroundMap.WATER:
                model = waterModel;
                yPosition = -0.2f; // Agua hundida para que los puentes queden a nivel normal
                break;
            case GroundMap.ROCK:
                model = mountainModel;
                yPosition = 0.6f; // La mitad de la altura (1.2 / 2) para que la base esté al nivel del suelo
                break;
            default:
                model = groundModel;
                yPosition = 0.0f;
                break;
        }

        if (model != null) {
            ModelInstance instance = new ModelInstance(model);
            instance.transform.setToTranslation(
                    ground.getPosition().getX() + 0.5f,
                    yPosition,
                    ground.getPosition().getY() + 0.5f);
            instances.add(instance);
        }
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        // Renderizar pilares del puente que bajan hasta el agua
        ModelInstance pillar = new ModelInstance(bridgePillarModel);
        pillar.transform.setToTranslation(
                bridgeGateRailTrack.getPosition().getX() + 0.5f,
                -0.05f, // Posición para que el pilar baje hasta el agua (y=-0.2)
                bridgeGateRailTrack.getPosition().getY() + 0.5f);
        instances.add(pillar);

        // Renderizar vías a nivel normal
        visitRailTrack(bridgeGateRailTrack);
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        // Renderizar pilares del puente que bajan hasta el agua
        ModelInstance pillar = new ModelInstance(bridgePillarModel);
        pillar.transform.setToTranslation(
                bridgeRailTrack.getPosition().getX() + 0.5f,
                -0.05f, // Posición para que el pilar baje hasta el agua (y=-0.2)
                bridgeRailTrack.getPosition().getY() + 0.5f);
        instances.add(pillar);

        // Renderizar vías a nivel normal
        visitRailTrack(bridgeRailTrack);
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        // Renderizar portal del túnel como bloque negro simple
        ModelInstance portal = new ModelInstance(tunnelPortalModel);
        portal.transform.setToTranslation(
                tunnelGateRailTrack.getPosition().getX() + 0.5f,
                0.6f, // Mismo nivel que montañas
                tunnelGateRailTrack.getPosition().getY() + 0.5f);
        instances.add(portal);

        // Renderizar vías normales
        visitRailTrack(tunnelGateRailTrack);
    }
}
