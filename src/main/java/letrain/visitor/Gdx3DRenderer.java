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
    private com.badlogic.gdx.graphics.g3d.Model locomotiveSelectedModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonHighlightModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonUnlinkModel;
    private com.badlogic.gdx.graphics.g3d.Model locomotiveUnlinkModel;
    private com.badlogic.gdx.graphics.g3d.Model forkModel;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model waterModel;
    private com.badlogic.gdx.graphics.g3d.Model mountainModel;
    private com.badlogic.gdx.graphics.g3d.Model ballastModel;
    private com.badlogic.gdx.graphics.g3d.Model bridgePillarModel;
    private com.badlogic.gdx.graphics.g3d.Model tunnelPortalModel;
    private com.badlogic.gdx.graphics.g3d.Model semaphoreOpenModel;
    private com.badlogic.gdx.graphics.g3d.Model semaphoreClosedModel;
    private com.badlogic.gdx.graphics.g3d.Model sensorModel;

    public static class VehicleLabel {
        public com.badlogic.gdx.math.Vector3 pos;
        public String text;
        public com.badlogic.gdx.math.Vector3 normal;

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal) {
            this.pos = pos;
            this.text = text;
            this.normal = normal;
        }
    }

    private List<VehicleLabel> labels = new ArrayList<>();
    private Model modelRef;
    private float animationAlpha = 1.0f;

    public void setAnimationAlpha(float alpha) {
        this.animationAlpha = alpha;
    }

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

            // Locomotora verde para seleccionada
            locomotiveSelectedModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GREEN)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Vagón amarillo para modo LINK
            wagonHighlightModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Modelos Rojos para UNLINK
            locomotiveUnlinkModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.RED)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            wagonUnlinkModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.RED)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Semáforo Abierto (Poste gris + Caja Verde)
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mb.begin();
            // Poste
            com.badlogic.gdx.graphics.g3d.model.Node node1 = mb.node();
            node1.id = "pole";
            mb.part("pole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)))
                    .cylinder(0.1f, 1.0f, 0.1f, 10);
            // Luz
            com.badlogic.gdx.graphics.g3d.model.Node node2 = mb.node();
            node2.id = "light";
            node2.translation.set(0, 0.5f, 0);
            mb.part("light", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GREEN)))
                    .box(0.2f, 0.3f, 0.2f);
            semaphoreOpenModel = mb.end();

            // Semáforo Cerrado (Poste gris + Caja Roja)
            mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mb.begin();
            // Poste
            node1 = mb.node();
            node1.id = "pole";
            mb.part("pole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)))
                    .cylinder(0.1f, 1.0f, 0.1f, 10);
            // Luz
            node2 = mb.node();
            node2.id = "light";
            node2.translation.set(0, 0.5f, 0);
            mb.part("light", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.RED)))
                    .box(0.2f, 0.3f, 0.2f);
            semaphoreClosedModel = mb.end();

            // Sensor (Caja pequeña amarilla en el suelo, entre los raíles)
            sensorModel = modelBuilder.createBox(0.4f, 0.05f, 0.4f,
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
        model.getSemaphores().forEach(s -> s.accept(this));
        model.getSensors().forEach(s -> s.accept(this));
        model.getLocomotives().forEach(l -> l.accept(this));
        model.getWagons().forEach(w -> w.accept(this));
        visitCursor(model.getCursor());
    }

    @Override
    public void visitRailMap(RailMap map) {
        // this.railMap = (letrain.map.impl.RailMap) map; // Guardar referencia
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
        // Escalamos para afilar el cursor: más largo en X (dirección) y más estrecho en
        // Z
        instance.transform.scale(1.6f, 1f, 0.6f);
        instances.add(instance);
    }

    public static float getDirX(letrain.map.Dir d) {
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

    public static float getDirZ(letrain.map.Dir d) {
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
        if (locomotiveUnlinkModel != null)
            locomotiveUnlinkModel.dispose();
        if (wagonUnlinkModel != null)
            wagonUnlinkModel.dispose();
        if (semaphoreOpenModel != null)
            semaphoreOpenModel.dispose();
        if (semaphoreClosedModel != null)
            semaphoreClosedModel.dispose();
        if (sensorModel != null)
            sensorModel.dispose();
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        boolean highlight = false;
        boolean unlinkHighlight = false;

        if (modelRef != null) {
            if (modelRef.getMode() == Model.GameMode.LINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == locomotive) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getLinkersToRemove()) {
                        if (l == locomotive) {
                            unlinkHighlight = true;
                            break;
                        }
                    }
                }
            }
        }

        boolean isSelected = (modelRef != null && modelRef.getSelectedLocomotive() == locomotive);

        com.badlogic.gdx.graphics.g3d.Model modelToUse = locomotiveModel;
        if (unlinkHighlight) {
            modelToUse = locomotiveUnlinkModel; // Rojo (Unlink)
        } else if (highlight) {
            modelToUse = locomotiveHighlightModel; // Amarillo (Link)
        } else if (isSelected) {
            modelToUse = locomotiveSelectedModel; // Verde (Seleccionada)
        }

        ModelInstance instance = new ModelInstance(modelToUse);

        // Interpolación continua (Predictiva)
        // en lugar de usar previousPosition (que interpolaba el "salto" ya ocurrido),
        // usamos la posición actual y la proyectamos hacia la siguiente celda
        // basándonos en el tiempo de espera (turns).

        float x = locomotive.getPosition().getX();
        float y = locomotive.getPosition().getY();
        float angle = locomotive.getDir().getValue() * 45f; // Default angle

        if (locomotive.getTotalTurns() >= 0) {
            float totalDelay = (float) locomotive.getTotalTurns() + 1.0f;
            float currentDelay = (float) locomotive.getTurns() + 1.0f - animationAlpha;
            float progress = 1.0f - (currentDelay / totalDelay);

            // Clamp progress
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
                        
                        // Interpolación de ángulo para curvas
                        // Ángulo objetivo (hacia donde vamos)
                        float targetAngle = locomotive.getDir().getValue() * 45f;
                         // Ángulo inicial (de donde venimos)
                        float startAngle = targetAngle; // Por defecto recto
                        
                        // Determinar si estamos en una curva
                        // Check de la otra conexión del track actual
                        // Si currentTrack conecta con A y B. Y vamos hacia nextTrack (Dir A).
                        // Entonces venimos de Dir B.
                        // Nuestra dirección de entrada fue inverse(B).
                        letrain.map.Dir exitDir = locomotive.getDir();
                        // Iteramos direcciones para encontrar la otra conexión
                        for (letrain.map.Dir d : letrain.map.Dir.values()) {
                            if (d != exitDir && currentTrack.getConnected(d) != null) {
                                // Encontramos la otra conexión (entrada)
                                // Si entramos por 'd', nuetra dirección de movimiento era 'd.inverse()' o 'd'?
                                // 'getConnected(d)' devuelve el track conectado en el puerto 'd'.
                                // Si el track es una curva (S, E). Conectado en S y en E.
                                // Si salimos por E (exitDir=E).
                                // La otra conexión es S.
                                // Eso significa que entramos por el puerto S.
                                // Para entrar por el puerto S, venimos del Sur, moviéndonos hacia el Norte.
                                // Dirección de movimiento anterior: N.
                                // N es S.inverse().
                                startAngle = d.inverse().getValue() * 45f;
                                break; 
                            }
                        }
                        
                        
                        // Interpolar suavemente
                        // Multiplicamos progress por 2 para que el giro termine al llegar al borde de la casilla (0.5)
                        // "Tienen que terminar el giro al salir de la curva"
                        float rotProgress = progress * 2.0f;
                        if (rotProgress > 1.0f) rotProgress = 1.0f;
                        
                        angle = com.badlogic.gdx.math.MathUtils.lerpAngleDeg(startAngle, targetAngle, rotProgress);
                    }
                }
            }
        }

        instance.transform.setToTranslation(x + 0.5f, 0.6f, y + 0.5f);
        // float angle = locomotive.getDir().getValue() * 45f; // Ya calculado arriba o default
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

        // Posicionar la cara en el frente de la locomotora (usando las mismas
        // coordenadas interpoladas)
        frontFace.transform.setToTranslation(
                x + 0.5f,
                0.6f,
                y + 0.5f);
        frontFace.transform.rotate(0, 1, 0, angle); // Rotar según dirección de locomotora
        frontFace.transform.translate(0.41f, 0, 0); // Mover hacia el frente (en X local)
        instances.add(frontFace);

        // Añadir etiquetas a los lados
        // Calcular vectores dirección dinámicos basados en el ángulo interpolado
        float rad = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;
        
        // En nuestro sistema:
        // Angle 0 (N) -> dx=0, dz=-1. sin(0)=0, -cos(0)=-1. Correcto.
        // Angle 90 (E) -> dx=1, dz=0. sin(90)=1, -cos(90)=0. Correcto.
        float dx = com.badlogic.gdx.math.MathUtils.sin(rad);
        float dz = -com.badlogic.gdx.math.MathUtils.cos(rad);
        
        // Perpendicular: (dz, -dx)
        float perpX = dz * 0.42f;
        float perpZ = -dx * 0.42f;
        
        labels.add(new VehicleLabel(
            new com.badlogic.gdx.math.Vector3(x + 0.5f + perpX, 0.6f, y + 0.5f + perpZ), 
            locomotive.getAspect(), 
            new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ).nor()
        ));
        
        labels.add(new VehicleLabel(
            new com.badlogic.gdx.math.Vector3(x + 0.5f - perpX, 0.6f, y + 0.5f - perpZ),
            locomotive.getAspect(),
            new com.badlogic.gdx.math.Vector3(-perpX, 0, -perpZ).nor()
        ));
    }

    @Override
    public void visitWagon(Wagon wagon) {
        boolean highlight = false;
        boolean unlinkHighlight = false;

        if (modelRef != null) {
            if (modelRef.getMode() == Model.GameMode.LINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == wagon) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getLinkersToRemove()) {
                        if (l == wagon) {
                            unlinkHighlight = true;
                            break;
                        }
                    }
                }
            }
        }

        com.badlogic.gdx.graphics.g3d.Model modelToUse = wagonModel;
        if (unlinkHighlight) {
            modelToUse = wagonUnlinkModel;
        } else if (highlight) {
            modelToUse = wagonHighlightModel;
        }

        ModelInstance instance = new ModelInstance(modelToUse);

        float x = wagon.getPosition().getX();
        float y = wagon.getPosition().getY();
        float angle = wagon.getDir().getValue() * 45f; // Default angle

        // Interpolación continua (Predictiva) basada en la locomotora directora
        letrain.vehicle.impl.rail.Train train = wagon.getTrain();
        if (train != null) {
            letrain.vehicle.impl.Tractor director = train.getDirectorLinker();
            if (director instanceof Locomotive) {
                Locomotive loc = (Locomotive) director;
                if (loc.getTotalTurns() >= 0) {
                    float totalDelay = (float) loc.getTotalTurns() + 1.0f;
                    float currentDelay = (float) loc.getTurns() + 1.0f - animationAlpha;
                    float progress = 1.0f - (currentDelay / totalDelay);

                    if (progress < 0)
                        progress = 0;
                    if (progress > 1)
                        progress = 1;

                    letrain.track.Track currentTrack = wagon.getTrack();
                    if (currentTrack != null) {
                        letrain.track.Track nextTrack = currentTrack.getConnected(wagon.getDir());
                        if (nextTrack != null) {
                            float nextX = nextTrack.getPosition().getX();
                            float nextY = nextTrack.getPosition().getY();
                            if (Math.abs(nextX - x) <= 1 && Math.abs(nextY - y) <= 1) {
                                x = x + (nextX - x) * progress;
                                y = y + (nextY - y) * progress;
                                
                                float targetAngle = wagon.getDir().getValue() * 45f;
                                float startAngle = targetAngle;
                                letrain.map.Dir exitDir = wagon.getDir();
                                for (letrain.map.Dir d : letrain.map.Dir.values()) {
                                    if (d != exitDir && currentTrack.getConnected(d) != null) {
                                        startAngle = d.inverse().getValue() * 45f;
                                        break; 
                                    }
                                }
                                
                                // Multiplicamos progress por 2 para que el giro termine al llegar al borde de la casilla (0.5)
                                float rotProgress = progress * 2.0f;
                                if (rotProgress > 1.0f) rotProgress = 1.0f;
                                
                                angle = com.badlogic.gdx.math.MathUtils.lerpAngleDeg(startAngle, targetAngle, rotProgress);
                            }
                        }
                    }
                }
            }
        }

        // Elevamos el centro de masa (0.6f) para que se sitúe sobre las vías
        instance.transform.setToTranslation(x + 0.5f, 0.6f, y + 0.5f);
        // Orientación según la dirección del modelo
        // float angle = wagon.getDir().getValue() * 45f; // Ya calculado
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // Añadir etiquetas a los lados
        float rad = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;
        
        float dx = com.badlogic.gdx.math.MathUtils.sin(rad);
        float dz = -com.badlogic.gdx.math.MathUtils.cos(rad);
        
        // Perpendicular: (dz, -dx)
        float perpX = dz * 0.42f;
        float perpZ = -dx * 0.42f;
        
        labels.add(new VehicleLabel(
            new com.badlogic.gdx.math.Vector3(x + 0.5f + perpX, 0.6f, y + 0.5f + perpZ),
            wagon.getAspect(),
            new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ).nor()
        ));
        labels.add(new VehicleLabel(
            new com.badlogic.gdx.math.Vector3(x + 0.5f - perpX, 0.6f, y + 0.5f - perpZ),
            wagon.getAspect(),
            new com.badlogic.gdx.math.Vector3(-perpX, 0, -perpZ).nor()
        ));
    }

    @Override
    public void visitSensor(Sensor sensor) {
        if (sensor.getPosition() == null) return;
        
        float x = sensor.getPosition().getX();
        float y = sensor.getPosition().getY();

        ModelInstance instance = new ModelInstance(sensorModel);
        instance.transform.setToTranslation(x + 0.5f, 0.1f, y + 0.5f); // Centrado y a ras de suelo
        instances.add(instance);
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        float x = semaphore.getPosition().getX();
        float y = semaphore.getPosition().getY();
        
        com.badlogic.gdx.graphics.g3d.Model modelToUse = semaphore.isOpen() ? semaphoreOpenModel : semaphoreClosedModel;
        ModelInstance instance = new ModelInstance(modelToUse);

        // Calcular offset basado en la vía si existe
        float offsetX = 0;
        float offsetZ = 0;
        float angle = 0;
        
        if (modelRef != null) {
            letrain.track.Track track = modelRef.getRailMap().getTrackAt((int)x, (int)y);
            if (track != null && track instanceof letrain.track.rail.RailTrack) {
                letrain.track.rail.RailTrack railTrack = (letrain.track.rail.RailTrack) track;
                if (railTrack.getNumRoutes() > 0) {
                    // Usamos la primera ruta para determinar orientación
                    letrain.map.Dir d = railTrack.getFirstOpenDir();
                    float dx = getDirX(d);
                    float dz = getDirZ(d);
                    
                    // Perpendicular (offset a la derecha de la dirección)
                    // Dir(dx, dz) -> Perpendicular(dz, -dx)
                    offsetX = dz * 1.0f; 
                    offsetZ = -dx * 1.0f;
                    
                    // Rotar para mirar a la vía (opcional, pero queda mejor)
                    angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
                }
            }
        }

        instance.transform.setToTranslation(x + 0.5f + offsetX, 0.5f, y + 0.5f + offsetZ);
        instance.transform.rotate(0, 1, 0, angle);
        
        if (modelRef.getSelectedSemaphore() == semaphore) {
            instance.transform.scale(1.5f, 1.5f, 1.5f);
        }
        
        instances.add(instance);
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
