package letrain.visitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.impl.RailMap;
import letrain.mvp.Model;
import letrain.track.CargoTypes;
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
    private final java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> instances = new java.util.ArrayList<>();
    private final java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> transparentInstances = new java.util.ArrayList<>();

    public java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> getInstances() {
        return instances;
    }

    private com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder;
    private com.badlogic.gdx.graphics.g3d.Model railModel;
    private com.badlogic.gdx.graphics.g3d.Model inactiveRailModel;
    private com.badlogic.gdx.graphics.g3d.Model invalidRailModel;
    private com.badlogic.gdx.graphics.g3d.Model cursorModel;
    private com.badlogic.gdx.graphics.g3d.Model locomotiveModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonModel;
    private com.badlogic.gdx.graphics.g3d.Model highlightModel;
    private com.badlogic.gdx.graphics.g3d.Model locomotiveHighlightModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonHighlightModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonUnlinkModel;
    private com.badlogic.gdx.graphics.g3d.Model locomotiveUnlinkModel;
    private com.badlogic.gdx.graphics.g3d.Model forkModel;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model waterModel;
    private com.badlogic.gdx.graphics.g3d.Model mountainModel;
    private com.badlogic.gdx.graphics.g3d.Model ballastModel;
    private com.badlogic.gdx.graphics.g3d.Model bridgePillarModel;
    private com.badlogic.gdx.graphics.g3d.Model forkBaseModel;
    private com.badlogic.gdx.graphics.g3d.Model selectedForkBaseModel;
    private com.badlogic.gdx.graphics.g3d.Model forkBoxModel;
    private com.badlogic.gdx.graphics.g3d.Model selectedForkBoxModel;
    private com.badlogic.gdx.graphics.g3d.Model tunnelPortalModel;
    private com.badlogic.gdx.graphics.g3d.Model terrainWallModel;
    private com.badlogic.gdx.graphics.g3d.Model semaphoreOpenModel;
    private com.badlogic.gdx.graphics.g3d.Model semaphoreClosedModel;
    private com.badlogic.gdx.graphics.g3d.Model sensorModel;
    private com.badlogic.gdx.graphics.g3d.Model goldConsumerModel;
    private com.badlogic.gdx.graphics.g3d.Model coalConsumerModel;
    private com.badlogic.gdx.graphics.g3d.Model rubyConsumerModel;
    private Set<letrain.track.rail.RailTrack> selectedStationTracks = new HashSet<>();
    private com.badlogic.gdx.graphics.g3d.Model wagonJewelModel;
    private com.badlogic.gdx.graphics.g3d.Model cylinderModel;
    private com.badlogic.gdx.graphics.g3d.Model selectionLineModel;
    private com.badlogic.gdx.graphics.g3d.Model redFireModel1;
    private com.badlogic.gdx.graphics.g3d.Model redFireModel2;
    private com.badlogic.gdx.graphics.g3d.Model redFireModel3;
    private com.badlogic.gdx.graphics.g3d.Model yellowFireModel1;
    private com.badlogic.gdx.graphics.g3d.Model yellowFireModel2;
    private com.badlogic.gdx.graphics.g3d.Model yellowFireModel3;
    private com.badlogic.gdx.graphics.g3d.Model redSphereModel1;
    private com.badlogic.gdx.graphics.g3d.Model redSphereModel2;
    private com.badlogic.gdx.graphics.g3d.Model redSphereModel3;
    private com.badlogic.gdx.graphics.g3d.Model yellowSphereModel1;
    private com.badlogic.gdx.graphics.g3d.Model yellowSphereModel2;
    private com.badlogic.gdx.graphics.g3d.Model yellowSphereModel3;

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

    private List<VehicleLabel> labels = new ArrayList<>();
    private Model modelRef;
    private com.badlogic.gdx.graphics.Camera camera;
    private float animationAlpha = 1.0f;
    private boolean isXRayActive = false;

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

            // Raíl mal conectado (Ahora un cubo amarillo: Motoniveladora)
            invalidRailModel = modelBuilder.createBox(0.4f, 0.4f, 0.4f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Cursor en forma de bloque triangular (prisma triangular plano)
            cursorModel = modelBuilder.createCylinder(0.8f, 0.02f, 0.8f, 3,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Locomotora simple (Bloque Gris Claro)
            locomotiveModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.6f, 0.6f, 0.6f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Vagón simple (Bloque Azul con transparencia)
            // wagonModel moved down

            // Modelo de resaltado (Caja amarilla más grande para que destaque)
            highlightModel = modelBuilder.createBox(1.0f, 0.15f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Grey Fork Base (Point 20 refinement)
            forkBaseModel = modelBuilder.createBox(1.0f, 0.06f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // White Selected Fork Base (Point 20 refinement)
            selectedForkBaseModel = modelBuilder.createBox(1.0f, 0.06f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Locomotora amarilla para modo LINK
            locomotiveHighlightModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Línea de selección (Rectángulo verde fino, orientado con el texto)
            selectionLineModel = modelBuilder.createBox(0.12f, 0.02f, 0.5f,
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

            // Chasis del vagón (Caja azul hueca)
            wagonModel = createOpenBox(modelBuilder, 0.8f, 0.6f, 0.8f,
                    new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 1f)); // Gray color

            // deleted legacy container initializers

            // deleted unused container initializers

            // Indicador de ruta en desvíos (Placa pequeña roja)
            forkModel = modelBuilder.createBox(0.4f, 0.02f, 0.4f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.RED)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Thin Fork Plate (User request: plate instead of block)
            forkBoxModel = modelBuilder.createBox(0.3f, 0.02f, 0.3f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // White Selected Fork Plate
            selectedForkBoxModel = modelBuilder.createBox(0.3f, 0.02f, 0.3f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // ----------------------------------------------------------------------------------
            // FIN NUEVOS MODELOS DISPONIBLES
            // ----------------------------------------------------------------------------------

            wagonJewelModel = modelBuilder.createBox(1.0f, 1.0f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE),
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createSpecular(com.badlogic.gdx.graphics.Color.WHITE),
                            com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.createShininess(16f)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            cylinderModel = modelBuilder.createCylinder(1f, 1f, 1f, 24,
                    new com.badlogic.gdx.graphics.g3d.Material(
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // ----------------------------------------------------------------------------------

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
            com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpbSem = mb.part("pole",
                    com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpbSem, 0.1f, 1.0f, 0.1f, 10);
            // Luz
            com.badlogic.gdx.graphics.g3d.model.Node node2 = mb.node();
            node2.id = "light";
            node2.translation.set(0, 0.5f, 0);
            mpbSem = mb.part("light", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GREEN)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSem, 0.2f, 0.3f, 0.2f);
            semaphoreOpenModel = mb.end();

            // Semáforo Cerrado (Poste gris + Caja Roja)
            mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mb.begin();
            // Poste
            node1 = mb.node();
            node1.id = "pole";
            mpbSem = mb.part("pole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpbSem, 0.1f, 1.0f, 0.1f, 10);
            // Luz
            node2 = mb.node();
            node2.id = "light";
            node2.translation.set(0, 0.5f, 0);
            mpbSem = mb.part("light", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.RED)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSem, 0.2f, 0.3f, 0.2f);
            semaphoreClosedModel = mb.end();

            // Sensor (Caja pequeña amarilla en el suelo, entre los raíles)
            sensorModel = modelBuilder.createBox(0.4f, 0.05f, 0.4f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)),
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
            // Alargado a 0.85f para cubrir huecos en curvas
            ballastModel = modelBuilder.createBox(0.5f, 0.1f, 0.85f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Pilar de puente (columna vertical)
            // Altura base 1.0 para escalar fácilmente
            bridgePillarModel = modelBuilder.createBox(0.4f, 1.0f, 0.4f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Portal de túnel (bloque negro del mismo tamaño que montaña)
            tunnelPortalModel = modelBuilder.createBox(1.0f, 1.2f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(new com.badlogic.gdx.graphics.Color(0.15f, 0.15f, 0.15f, 1f))),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Pared de terreno (para bordes con agua)
            // 1.0 de ancho, 2.1 de alto (para solapar un poco), 0.05 de grosor
            terrainWallModel = modelBuilder.createBox(1.0f, 2.1f, 0.05f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Pre-create fire models (pyramids) - Larger size (approx double: 0.3w x 0.45h)
            redFireModel1 = createPyramidModel(0.3f, 0.45f, 0.3f,
                    new com.badlogic.gdx.graphics.Color(0.6f, 0f, 0f, 1f)); // Dark Red
            redFireModel2 = createPyramidModel(0.3f, 0.45f, 0.3f, com.badlogic.gdx.graphics.Color.RED); // Red
            redFireModel3 = createPyramidModel(0.3f, 0.45f, 0.3f,
                    new com.badlogic.gdx.graphics.Color(1f, 0.3f, 0.3f, 1f)); // Light Red

            yellowFireModel1 = createPyramidModel(0.3f, 0.45f, 0.3f,
                    new com.badlogic.gdx.graphics.Color(1f, 0.5f, 0f, 1f)); // Orange
            yellowFireModel2 = createPyramidModel(0.3f, 0.45f, 0.3f, com.badlogic.gdx.graphics.Color.ORANGE); // Orange
                                                                                                              // (standard)
            yellowFireModel3 = createPyramidModel(0.3f, 0.45f, 0.3f, com.badlogic.gdx.graphics.Color.YELLOW); // Yellow

            // Pre-create sphere models (embers) - Similar size
            redSphereModel1 = createSphereModel(0.25f, new com.badlogic.gdx.graphics.Color(0.6f, 0f, 0f, 1f));
            redSphereModel2 = createSphereModel(0.25f, com.badlogic.gdx.graphics.Color.RED);
            redSphereModel3 = createSphereModel(0.25f, new com.badlogic.gdx.graphics.Color(1f, 0.3f, 0.3f, 1f));
            yellowSphereModel1 = createSphereModel(0.25f, new com.badlogic.gdx.graphics.Color(1f, 0.5f, 0f, 1f));
            yellowSphereModel2 = createSphereModel(0.25f, com.badlogic.gdx.graphics.Color.ORANGE);
            yellowSphereModel3 = createSphereModel(0.25f, com.badlogic.gdx.graphics.Color.YELLOW);

            // Pre-create Consumer Models (Performance Optimization: 1 instance instead of
            // 7)
            goldConsumerModel = createConsumerModel(letrain.track.CargoTypes.GOLD.getColor());
            coalConsumerModel = createConsumerModel(letrain.track.CargoTypes.COAL.getColor());
            rubyConsumerModel = createConsumerModel(letrain.track.CargoTypes.RUBY.getColor());
        }
    }

    private com.badlogic.gdx.graphics.g3d.Model createConsumerModel(com.badlogic.gdx.graphics.Color color) {
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        mb.begin();
        float thickness = 0.06f;
        float h = 0.04f;

        // Calculate contrast color for the bars
        // Luminance = 0.299*R + 0.587*G + 0.114*B
        float luminance = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b;
        com.badlogic.gdx.graphics.Color barColor = color.cpy();
        if (luminance > 0.5f) {
            barColor.lerp(com.badlogic.gdx.graphics.Color.BLACK, 0.5f); // Darker for bright backgrounds
        } else {
            barColor.lerp(com.badlogic.gdx.graphics.Color.WHITE, 0.6f); // Much lighter for dark backgrounds (like coal)
        }

        com.badlogic.gdx.graphics.g3d.Material iconMat = new com.badlogic.gdx.graphics.g3d.Material(
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(barColor));
        com.badlogic.gdx.graphics.g3d.Material bgMat = new com.badlogic.gdx.graphics.g3d.Material(
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));

        // Background Plate
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpb = mb.part("bg",
                com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                bgMat);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, 0.95f, 0.01f, 0.95f);

        // Diagonal X
        mpb = mb.part("x", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                iconMat);

        com.badlogic.gdx.math.Matrix4 m = new com.badlogic.gdx.math.Matrix4();
        m.setToRotation(0, 1, 0, 45).trn(0, 0.02f, 0);
        mpb.setVertexTransform(m);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, thickness, h, 1.35f);
        m.setToRotation(0, 1, 0, -45).trn(0, 0.02f, 0);
        mpb.setVertexTransform(m);
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, thickness, h, 1.35f);

        // Frame
        for (int i = 0; i < 4; i++) {
            float angle = i * 90f;
            float offset = 0.47f;
            float bx = (float) Math.cos(Math.toRadians(angle)) * offset;
            float bz = (float) Math.sin(Math.toRadians(angle)) * offset;
            m.setToRotation(0, 1, 0, angle).trn(bx, 0.02f, bz);
            mpb.setVertexTransform(m);
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, thickness, h, 1.0f);
        }

        return mb.end();
    }

    private com.badlogic.gdx.graphics.g3d.Model createSphereModel(float size, com.badlogic.gdx.graphics.Color color) {
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        return mb.createSphere(size, size, size, 12, 12,
                new com.badlogic.gdx.graphics.g3d.Material(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color)),
                (long) (com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal));
    }

    private com.badlogic.gdx.graphics.g3d.Model createPyramidModel(float w, float h, float d,
            com.badlogic.gdx.graphics.Color color) {
        com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mb = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
        mb.begin();
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder meshBuilder = mb.part("pyramid",
                com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                (long) (com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal),
                new com.badlogic.gdx.graphics.g3d.Material(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color)));

        com.badlogic.gdx.math.Vector3 p0 = new com.badlogic.gdx.math.Vector3(-w / 2, 0, -d / 2);
        com.badlogic.gdx.math.Vector3 p1 = new com.badlogic.gdx.math.Vector3(w / 2, 0, -d / 2);
        com.badlogic.gdx.math.Vector3 p2 = new com.badlogic.gdx.math.Vector3(w / 2, 0, d / 2);
        com.badlogic.gdx.math.Vector3 p3 = new com.badlogic.gdx.math.Vector3(-w / 2, 0, d / 2);
        com.badlogic.gdx.math.Vector3 top = new com.badlogic.gdx.math.Vector3(0, h, 0);

        meshBuilder.triangle(p0, p1, top);
        meshBuilder.triangle(p1, p2, top);
        meshBuilder.triangle(p2, p3, top);
        meshBuilder.triangle(p3, p0, top);

        return mb.end();
    }

    public void clear() {
        instances.clear();
        transparentInstances.clear();
        labels.clear();
    }

    public java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> getTransparentInstances() {
        return transparentInstances;
    }

    public void visitGroundPlane(com.badlogic.gdx.graphics.g3d.Model ground) {
        instances.add(new ModelInstance(ground));
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
    }

    @Override
    public void visitModel(Model model) {
        visitModel(model, null);
    }

    public void visitModel(Model model, com.badlogic.gdx.graphics.Camera camera) {
        this.modelRef = model;
        this.camera = camera;
        labels.clear();

        // ----------------------------------------------------------------------------------
        // X-RAY Detection: Are we inside any mountain or tunnel?
        // ----------------------------------------------------------------------------------
        isXRayActive = false;
        if (model.getMode() == letrain.mvp.Model.GameMode.RAILS) {
            letrain.map.Point cp = model.getCursor().getPosition();
            Integer terrain = model.getGroundMap().getValueAt(cp);
            if (terrain != null && terrain == GroundMap.ROCK) {
                isXRayActive = true;
            } else {
                // Also check if cursor is over a tunnel gate
                letrain.track.rail.RailTrack rt = model.getCursorRailTrack();
                if (rt instanceof letrain.track.rail.TunnelGateRailTrack) {
                    isXRayActive = true;
                }
            }
        }

        // Ya no limpiamos instances aquí, lo hace la vista antes de empezar
        model.getGroundMap().accept(this);
        model.getRailMap().accept(this);
        model.getSemaphores().forEach(s -> s.accept(this));
        model.getSensors().forEach(s -> s.accept(this));
        model.getStations().forEach(s -> s.accept(this));
        model.getLocomotives().forEach(l -> l.accept(this));
        model.getWagons().forEach(w -> w.accept(this));
        visitCursor(model.getCursor());
    }

    private boolean isVisible(letrain.map.Point pos) {
        if (camera == null)
            return true;
        // Check if the tile bounds are within the camera frustum (performance
        // optimization)
        return camera.frustum.boundsInFrustum(pos.getX() + 0.5f, 0.5f, pos.getY() + 0.5f, 0.5f, 0.5f, 0.5f);
    }

    private letrain.map.Dir getValidOrientation(letrain.track.rail.RailTrack track) {
        letrain.map.Dir dir = track.getFirstOpenDir();
        if (dir == null)
            return letrain.map.Dir.N;
        return dir;
    }

    @Override
    public void visitRailMap(RailMap map) {
        // Pre-calcular tracks de la estación seleccionada para iluminar el andén
        // completo
        selectedStationTracks.clear();
        if (modelRef != null && modelRef.getSelectedStation() != null) {
            letrain.track.Track startTrack = modelRef.getSelectedStation().getTrack();
            if (startTrack instanceof letrain.track.rail.StationRailTrack) {
                java.util.Queue<letrain.track.rail.RailTrack> queue = new java.util.LinkedList<>();
                queue.add((letrain.track.rail.RailTrack) startTrack);
                selectedStationTracks.add((letrain.track.rail.RailTrack) startTrack);

                while (!queue.isEmpty()) {
                    letrain.track.rail.RailTrack current = queue.poll();
                    for (letrain.map.Dir d : letrain.map.Dir.values()) {
                        letrain.track.Track neighbor = current.getConnected(d);
                        if (neighbor instanceof letrain.track.rail.StationRailTrack
                                && !selectedStationTracks.contains(neighbor)) {
                            selectedStationTracks.add((letrain.track.rail.RailTrack) neighbor);
                            queue.add((letrain.track.rail.RailTrack) neighbor);
                        }
                    }
                }
            }
        }
        map.forEach(track -> track.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        if (!isVisible(track.getPosition()))
            return;
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
            boolean d1Connected = isConnected(track, d1);
            boolean d2Connected = isConnected(track, d2);

            drawHalfTrack(track.getPosition(), d1, d1Connected, shortenL1, shortenR1);
            drawHalfTrack(track.getPosition(), d2, d2Connected, shortenL2, shortenR2);
        });

        // Si la vía está sobre agua, ponemos un pilar
        if (modelRef != null && modelRef.getGroundMap() != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(track.getPosition());
            if (terrain != null && terrain == GroundMap.WATER) {
                ModelInstance pillar = new ModelInstance(bridgePillarModel);
                // El agua está en y=-2.0. La vía en y=0.0.
                // Altura del pilar = 1.9. Centro en y=-1.05 (desde -2.0 a -0.1).
                pillar.transform.setToTranslation(
                        track.getPosition().getX() + 0.5f,
                        -1.05f,
                        track.getPosition().getY() + 0.5f);
                pillar.transform.scale(1f, 1.9f, 1f);
                instances.add(pillar);
            }
        }

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

    private void drawHalfTrack(letrain.map.Point pos, letrain.map.Dir d, boolean connected, float shortenL,
            float shortenR) {
        drawHalfTrackElevated(pos, d, connected, 0.0f, shortenL, shortenR, railModel);
    }

    private void drawHalfTrackElevated(letrain.map.Point pos, letrain.map.Dir d, boolean connected, float elevation,
            float shortenL, float shortenR) {
        drawHalfTrackElevated(pos, d, connected, elevation, shortenL, shortenR, railModel);
    }

    private void drawHalfTrackElevated(letrain.map.Point pos, letrain.map.Dir d, boolean connected, float elevation,
            float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        float dx = getDirX(d);
        float dz = getDirZ(d);
        float magnitude = (float) Math.sqrt(dx * dx + dz * dz);

        // Ángulo hacia la cara del tile
        float angle = (float) Math.atan2(dx, dz) * MathUtils.radiansToDegrees;

        // Primero dibujamos el balasto (piedras grises)
        // Usamos el promedio de acortamiento para el balasto
        float shortenB = (shortenL + shortenR) / 2f;
        float shortenBallast = connected ? shortenB : 0.95f; // Mostramos más balasto si está mal para que se vea
        float scaleB = (magnitude * shortenBallast) / 0.5f;
        ModelInstance ballast = new ModelInstance(ballastModel);
        ballast.transform.setToTranslation(
                pos.getX() + 0.5f + (dx * (1f - shortenBallast / 2f)),
                0.03f + elevation,
                pos.getY() + 0.5f + (dz * (1f - shortenBallast / 2f)));
        ballast.transform.rotate(0, 1, 0, angle);
        ballast.transform.scale(1, 1, scaleB);
        instances.add(ballast);

        // Calculamos el vector perpendicular para el desplazamiento lateral de los
        // raíles
        float offX = (-dz / magnitude) * 0.15f;
        float offZ = (dx / magnitude) * 0.15f;

        if (connected) {
            com.badlogic.gdx.graphics.g3d.Model activeModel = railModelToUse;

            // Raíl izquierdo
            float scale = magnitude / 0.5f; // Base scale for a full half-track
            ModelInstance railL = new ModelInstance(activeModel);
            // Ajuste de desplazamiento para mantener el extremo exterior fijo si se acorta
            // Si shorten < 1, el raíl se encoge hacia su centro. Para que solo se encoja
            // desde el centro del tile,
            // debemos desplazarlo hacia afuera por la mitad de la longitud perdida.
            float finalShortenL = shortenL;
            float shiftX_L = dx * (1 - finalShortenL) / 2f;
            float shiftZ_L = dz * (1 - finalShortenL) / 2f;

            railL.transform.setToTranslation(
                    pos.getX() + 0.5f + (dx / 2f) + offX + shiftX_L,
                    0.08f + elevation,
                    pos.getY() + 0.5f + (dz / 2f) + offZ + shiftZ_L);
            railL.transform.rotate(0, 1, 0, angle);
            railL.transform.scale(1, 1, scale * finalShortenL);
            instances.add(railL);

            // Raíl derecho
            float finalShortenR = shortenR;
            float shiftX_R = dx * (1 - finalShortenR) / 2f;
            float shiftZ_R = dz * (1 - finalShortenR) / 2f;

            ModelInstance railR = new ModelInstance(activeModel);
            railR.transform.setToTranslation(
                    pos.getX() + 0.5f + (dx / 2f) - offX + shiftX_R,
                    0.08f + elevation,
                    pos.getY() + 0.5f + (dz / 2f) - offZ + shiftZ_R);
            railR.transform.rotate(0, 1, 0, angle);
            railR.transform.scale(1, 1, scale * finalShortenR);
            instances.add(railR);
        } else {
            // "Motoniveladora": Cubo amarillo en el centro del tramo
            ModelInstance grader = new ModelInstance(invalidRailModel);
            grader.transform.setToTranslation(
                    pos.getX() + 0.5f + (dx * 0.7f),
                    0.2f + elevation,
                    pos.getY() + 0.5f + (dz * 0.7f));
            grader.transform.rotate(0, 1, 0, angle);
            instances.add(grader);
        }
    }

    private boolean isConnected(letrain.track.Track track, letrain.map.Dir dir) {
        letrain.track.Track neighbor = track.getConnected(dir);
        if (neighbor == null)
            return false;
        // El vecino debe tener una ruta que empiece desde nuestra dirección inversa
        return neighbor.getRouter().getDir(dir.inverse()) != null;
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
        float cursorY = 0.10f;
        instance.transform.setToTranslation(pos.getX() + 0.5f, cursorY, pos.getY() + 0.5f);
        instance.transform.rotate(0, 1, 0, angle - 90f);
        // Escalamos para afilar el cursor: más largo en X (dirección) y más estrecho en
        // Z
        instance.transform.scale(1.6f, 1f, 0.6f);
        instances.add(instance);

        // X-RAY GHOST: Rendered through depth with transparency
        ModelInstance ghost = new ModelInstance(instance);
        ghost.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
        ghost.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute(
                com.badlogic.gdx.graphics.GL20.GL_GREATER, false));
        instances.add(ghost);

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
        boolean isSelected = false;
        // Check if selected
        if (modelRef != null && modelRef.getMode() == Model.GameMode.FORKS) {
            if (modelRef.getSelectedFork() != null
                    && modelRef.getSelectedFork().getPosition().equals(track.getPosition())) {
                isSelected = true;
            }
        }

        // Base plate (Always visible: Gray by default, White if selected)
        ModelInstance base = new ModelInstance(isSelected ? selectedForkBaseModel : forkBaseModel);
        base.transform.setToTranslation(track.getPosition().getX() + 0.5f, 0.03f,
                track.getPosition().getY() + 0.5f);
        instances.add(base);

        // SMALL BOX ON THE SIDE (Point 20 refinement)
        float bx = track.getPosition().getX() + 0.5f;
        float bz = track.getPosition().getY() + 0.5f;
        float boxOffset = 0.8f; // More separated as requested

        letrain.map.Dir trackAxis = track.getOriginalRoute().getFirst();
        letrain.map.Dir sideDir = trackAxis.turnRight().turnRight();
        bx += getDirX(sideDir) * boxOffset;
        bz += getDirZ(sideDir) * boxOffset;

        ModelInstance box = new ModelInstance(isSelected ? selectedForkBoxModel : forkBoxModel);
        box.transform.setToTranslation(bx, 0.06f + 0.01f, bz); // On top of the base plate (height 0.06)
        instances.add(box);

        // Active route rails
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
                if (dist > 0) {
                    shortenR1 = 0.75f;
                    shortenL2 = 0.75f;
                    shortenL1 = 0.9f;
                    shortenR2 = 0.9f;
                } else if (dist < 0) {
                    shortenL1 = 0.75f;
                    shortenR2 = 0.75f;
                    shortenR1 = 0.9f;
                    shortenL2 = 0.9f;
                }
            }
            boolean d1Connected = isConnected(track, d1);
            boolean d2Connected = isConnected(track, d2);

            drawHalfTrack(track.getPosition(), d1, d1Connected, shortenL1, shortenR1);
            drawHalfTrack(track.getPosition(), d2, d2Connected, shortenL2, shortenR2);
        }

        // Labels (Point 20 refinement)
        String idText = String.valueOf(track.getId());
        float labelScale = 0.4f; // Smaller labels as requested

        // Labels on top of the plate
        float labelHeight = 0.06f + 0.02f + 0.01f; // base + plate + slight offset
        labels.add(new VehicleLabel(new com.badlogic.gdx.math.Vector3(bx, labelHeight, bz), idText,
                new com.badlogic.gdx.math.Vector3(0, 1, 0), new com.badlogic.gdx.math.Vector3(0, 0, -1),
                com.badlogic.gdx.graphics.Color.BLACK, labelScale));
        // Bridge pillars logic
        if (modelRef != null && modelRef.getGroundMap() != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(track.getPosition());
            if (terrain != null && terrain == GroundMap.WATER) {
                ModelInstance pillar = new ModelInstance(bridgePillarModel);
                pillar.transform.setToTranslation(track.getPosition().getX() + 0.5f, -1.05f,
                        track.getPosition().getY() + 0.5f);
                pillar.transform.scale(1f, 1.9f, 1f);
                instances.add(pillar);
            }
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
        if (invalidRailModel != null)
            invalidRailModel.dispose();
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
        if (wagonJewelModel != null)
            wagonJewelModel.dispose();
        if (cylinderModel != null)
            cylinderModel.dispose();
        if (semaphoreOpenModel != null)
            semaphoreOpenModel.dispose();
        if (semaphoreClosedModel != null)
            semaphoreClosedModel.dispose();
        if (sensorModel != null)
            sensorModel.dispose();
        if (terrainWallModel != null)
            terrainWallModel.dispose();
        if (redFireModel1 != null)
            redFireModel1.dispose();
        if (redFireModel2 != null)
            redFireModel2.dispose();
        if (redFireModel3 != null)
            redFireModel3.dispose();
        if (yellowFireModel1 != null)
            yellowFireModel1.dispose();
        if (yellowFireModel2 != null)
            yellowFireModel2.dispose();
        if (yellowFireModel3 != null)
            yellowFireModel3.dispose();
        if (redSphereModel1 != null)
            redSphereModel1.dispose();
        if (redSphereModel2 != null)
            redSphereModel2.dispose();
        if (redSphereModel3 != null)
            redSphereModel3.dispose();
        if (yellowSphereModel1 != null)
            yellowSphereModel1.dispose();
        if (yellowSphereModel2 != null)
            yellowSphereModel2.dispose();
        if (yellowSphereModel3 != null)
            yellowSphereModel3.dispose();
        modelBuilder = null;
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
        }
        // Selection color removed as requested by user

        ModelInstance instance = new ModelInstance(modelToUse);

        // Interpolación continua (Predictiva)
        // en lugar de usar previousPosition (que interpolaba el "salto" ya ocurrido),
        // usamos la posición actual y la proyectamos hacia la siguiente celda
        // basándonos en el tiempo de espera (turns).

        float x = locomotive.getPosition().getX();
        float y = locomotive.getPosition().getY();
        float angle = locomotive.getDir().getValue() * 45f; // Default angle

        // Interpolación continua (Predictiva) basada en la locomotora directora (o sí
        // mismo si es director)
        Locomotive interpolationRef = locomotive;
        letrain.vehicle.impl.rail.Train train = locomotive.getTrain();
        if (train != null) {
            letrain.vehicle.impl.Tractor director = train.getDirectorLinker();
            if (director instanceof Locomotive) {
                interpolationRef = (Locomotive) director;
            }
        }

        if (interpolationRef.getTotalTurns() >= 0) {
            float totalDelay = (float) interpolationRef.getTotalTurns() + 1.0f;
            float currentDelay = (float) interpolationRef.getTurns() + 1.0f - animationAlpha;
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
                    // Prevent visual "mixing": check if next track is blocked recursively
                    boolean blocked = isPredictiveMoveBlocked(locomotive, new HashSet<>());
                    if (blocked) {
                        progress *= 0.25f; // Slow down to touch exactly at end of tick
                    }
                    if (locomotive.getTrain() != null && locomotive.getTrain().isStalled()) {
                        progress = 0.25f; // Stay touching
                    }

                    float nextX = nextTrack.getPosition().getX();
                    float nextY = nextTrack.getPosition().getY();

                    // Si la distancia es mayor a 1 (teletransporte/wrap), no interpolar
                    if (Math.abs(nextX - x) <= 1 && Math.abs(nextY - y) <= 1) {
                        x = x + (nextX - x) * progress;
                        y = y + (nextY - y) * progress;

                        // Interpolación de ángulo para curvas
                        // Ángulo inicial: Dirección actual de movimiento
                        float startAngle = locomotive.getDir().getValue() * 45f;

                        letrain.map.Dir nextDir = nextTrack.getDir(locomotive.getDir().inverse());
                        float targetAngle = nextDir != null ? nextDir.getValue() * 45f : startAngle;

                        // Corregir wrapping de ángulos
                        float diff = targetAngle - startAngle;
                        if (diff > 180)
                            targetAngle -= 360;
                        if (diff < -180)
                            targetAngle += 360;

                        // Interpolar suavemente
                        // Usar la segunda mitad del progreso (0.5 -> 1.0) para girar DENTRO de la nueva
                        // celda
                        float rotProgress = (progress - 0.5f) * 2.0f;
                        if (rotProgress < 0f)
                            rotProgress = 0f;
                        if (rotProgress > 1f)
                            rotProgress = 1f;

                        angle = com.badlogic.gdx.math.MathUtils.lerpAngleDeg(startAngle, targetAngle, rotProgress);
                    }
                }
            }
        }

        if (locomotive.isDestroying()) {
            // Derailed effect: deterministic random based on id
            long seed = locomotive.getId();
            java.util.Random rnd = new java.util.Random(seed);
            float offsetX = (rnd.nextFloat() - 0.5f) * 0.4f;
            float offsetZ = (rnd.nextFloat() - 0.5f) * 0.4f;
            float rotX = (rnd.nextFloat() - 0.5f) * 45f;
            float rotY = (rnd.nextFloat() - 0.5f) * 45f;
            float rotZ = (rnd.nextFloat() - 0.5f) * 45f;

            instance.transform.setToTranslation(x + 0.5f + offsetX, 0.6f, y + 0.5f + offsetZ);
            instance.transform.rotate(1, 0, 0, rotX);
            instance.transform.rotate(0, 1, 0, angle + rotY);
            instance.transform.rotate(0, 0, 1, rotZ);
        } else {
            instance.transform.setToTranslation(x + 0.5f, 0.6f, y + 0.5f);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (locomotive.isDestroying()) {
            drawFire(x + 0.5f, 0.6f, y + 0.5f, animationAlpha + locomotive.getId());
        }

        // Añadir indicador de selección para locomotora seleccionada (Número ID + Línea
        // verde)
        // Pegado a la cara superior (flat on top)
        if (isSelected) {
            float radL = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            float dxL = com.badlogic.gdx.math.MathUtils.cos(radL);
            float dzL = -com.badlogic.gdx.math.MathUtils.sin(radL);

            // Número ID de la locomotora (En blanco y más grande)
            labels.add(new VehicleLabel(
                    new com.badlogic.gdx.math.Vector3(x + 0.5f, 1.01f, y + 0.5f),
                    "" + locomotive.getId(),
                    new com.badlogic.gdx.math.Vector3(0, 1, 0), // Normal up
                    new com.badlogic.gdx.math.Vector3(dxL, 0, dzL).nor(), // Up vector
                    com.badlogic.gdx.graphics.Color.WHITE));

            // Línea verde pegada al techo, delante del número
            ModelInstance selectionLine = new ModelInstance(selectionLineModel);
            float lineOffset = 0.25f;
            selectionLine.transform.setToTranslation(x + 0.5f + dxL * lineOffset, 1.01f, y + 0.5f + dzL * lineOffset);
            selectionLine.transform.rotate(0, 1, 0, angle);
            instances.add(selectionLine);
        }

        // Añadir etiquetas a los lados
        {
            // Calcular vectores dirección dinámicos basados en el ángulo interpolado
            float radL = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;

            float dxL = com.badlogic.gdx.math.MathUtils.cos(radL);
            float dzL = -com.badlogic.gdx.math.MathUtils.sin(radL);

            // Perpendicular: (dz, -dx)
            float perpXL = dzL * 0.48f;
            float perpZL = -dxL * 0.48f;

            labels.add(new VehicleLabel(
                    new com.badlogic.gdx.math.Vector3(x + 0.5f + perpXL, 0.4f, y + 0.5f + perpZL),
                    locomotive.getAspect(),
                    new com.badlogic.gdx.math.Vector3(perpXL, 0, perpZL).nor()));
            labels.add(new VehicleLabel(
                    new com.badlogic.gdx.math.Vector3(x + 0.5f - perpXL, 0.4f, y + 0.5f - perpZL),
                    locomotive.getAspect(),
                    new com.badlogic.gdx.math.Vector3(-perpXL, 0, -perpZL).nor()));
        }
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

        // 1. Renderizar Chasis (Siempre visible, Azul)
        // Salvo que esté en modo highlight/unlink, en cuyo caso usamos el modelo
        // completo de antes?
        // El usuario quiere "Toy Style".
        // Vamos a usar el chasis como base.

        com.badlogic.gdx.graphics.g3d.Model chassisModel = wagonModel; // El nuevo chasis plano
        if (unlinkHighlight) {
            chassisModel = wagonUnlinkModel; // Rojo completo
        } else if (highlight) {
            chassisModel = wagonHighlightModel; // Amarillo completo
        }
        // Blinking removed as per user request

        ModelInstance instance = new ModelInstance(chassisModel);

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
                            // Prevent visual "mixing": check if next track is blocked recursively
                            boolean blocked = isPredictiveMoveBlocked(wagon, new HashSet<>());
                            if (blocked) {
                                progress *= 0.25f; // Slow down to touch exactly at end of tick
                            }
                            if (wagon.getTrain() != null && wagon.getTrain().isStalled()) {
                                progress = 0.25f; // Stay touching
                            }

                            float nextX = nextTrack.getPosition().getX();
                            float nextY = nextTrack.getPosition().getY();
                            if (Math.abs(nextX - x) <= 1 && Math.abs(nextY - y) <= 1) {
                                x = x + (nextX - x) * progress;
                                y = y + (nextY - y) * progress;

                                // Interpolación de ángulo para curvas
                                float startAngle = wagon.getDir().getValue() * 45f;

                                letrain.map.Dir nextDir = nextTrack.getDir(wagon.getDir().inverse());
                                float targetAngle = nextDir != null ? nextDir.getValue() * 45f : startAngle;

                                // Corregir wrapping de ángulos
                                float diff = targetAngle - startAngle;
                                if (diff > 180)
                                    targetAngle -= 360;
                                if (diff < -180)
                                    targetAngle += 360;

                                float rotProgress = (progress - 0.5f) * 2.0f;
                                if (rotProgress < 0f)
                                    rotProgress = 0f;
                                if (rotProgress > 1f)
                                    rotProgress = 1f;

                                angle = com.badlogic.gdx.math.MathUtils.lerpAngleDeg(startAngle, targetAngle,
                                        rotProgress);
                            }
                        }
                    }
                }
            }
        }

        // Raise wagon to sit on rails. Rails are at 0.08 + elevation.
        // Wagon height is now 0.6. Center needs to be at 0.08 + 0.3 = 0.38?
        // Let's try 0.45f to be safe and clearly on top.
        float wagonY = 0.45f;
        if (wagon.isDestroying()) {
            // Derailed effect: deterministic random based on hash
            long seed = wagon.hashCode();
            java.util.Random rnd = new java.util.Random(seed);
            float offsetX = (rnd.nextFloat() - 0.5f) * 0.4f;
            float offsetZ = (rnd.nextFloat() - 0.5f) * 0.4f;
            float rotX = (rnd.nextFloat() - 0.5f) * 45f;
            float rotY = (rnd.nextFloat() - 0.5f) * 45f;
            float rotZ = (rnd.nextFloat() - 0.5f) * 45f;

            instance.transform.setToTranslation(x + 0.5f + offsetX, wagonY, y + 0.5f + offsetZ);
            instance.transform.rotate(1, 0, 0, rotX);
            instance.transform.rotate(0, 1, 0, angle + rotY);
            instance.transform.rotate(0, 0, 1, rotZ);
        } else {
            instance.transform.setToTranslation(x + 0.5f, wagonY, y + 0.5f);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (wagon.isDestroying()) {
            drawFire(x + 0.5f, 0.5f, y + 0.5f, animationAlpha + wagon.hashCode());
        }

        // 2. Renderizar Bloque de Carga (Si hay carga y no estamos en modo highlight
        // que lo oculte)
        // Si hay blink, a veces ocultamos todo.
        // Si hay highlight/unlink, ocultamos la carga para ser claros con la selección.
        if (wagon.getCargoAmount() > 0 && !highlight && !unlinkHighlight && chassisModel != highlightModel) {
            int cargoAmount = wagon.getCargoAmount();
            int maxCapacity = wagon.getMaxCapacity();
            com.badlogic.gdx.graphics.Color cargoColor = (wagon.getCargoType() != null)
                    ? wagon.getCargoType().getColor().cpy()
                    : com.badlogic.gdx.graphics.Color.YELLOW.cpy();

            float fullness = (float) cargoAmount / (float) maxCapacity;

            // Single Block Jewels
            // Base width/depth to fit walls: 0.6f (same as wagonCargoModel footprint)
            // Max height: 0.5f (same as wagon height)
            float maxHeight = 0.5f;
            float currentHeight = fullness * maxHeight;

            ModelInstance jewelBlock = new ModelInstance(wagonJewelModel);
            jewelBlock.materials.get(0).set(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(cargoColor));

            // Position: y=0.25 (floor level) + half height
            float jewelY = 0.25f + (currentHeight / 2f);

            jewelBlock.transform.setToTranslation(x + 0.5f, jewelY, y + 0.5f);
            jewelBlock.transform.rotate(0, 1, 0, angle);
            jewelBlock.transform.scale(0.6f, currentHeight, 0.6f);

            instances.add(jewelBlock);
        }

        // Añadir etiquetas a los lados
        {
            float radW = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;

            float dxW = com.badlogic.gdx.math.MathUtils.cos(radW);
            float dzW = -com.badlogic.gdx.math.MathUtils.sin(radW);

            // Perpendicular: (dz, -dx)
            float perpXW = dzW * 0.48f;
            float perpZW = -dxW * 0.48f;

            labels.add(new VehicleLabel(
                    new com.badlogic.gdx.math.Vector3(x + 0.5f + perpXW, 0.4f, y + 0.5f + perpZW),
                    wagon.getAspect(),
                    new com.badlogic.gdx.math.Vector3(perpXW, 0, perpZW).nor()));
            labels.add(new VehicleLabel(
                    new com.badlogic.gdx.math.Vector3(x + 0.5f - perpXW, 0.4f, y + 0.5f - perpZW),
                    wagon.getAspect(),
                    new com.badlogic.gdx.math.Vector3(-perpXW, 0, -perpZW).nor()));
        }
    }

    private void drawFire(float x, float y, float z, float stateTime) {
        // Use real-time for animation so it doesn't pause when game logic stalls
        // Slower movement: decreased frame multiplier
        float realTime = (float) (com.badlogic.gdx.Gdx.graphics.getFrameId()) * 0.025f;
        float timeScale = 2.5f; // Slower swaying
        int numParticles = 12;
        for (int i = 0; i < numParticles; i++) {
            float seed = i * 123.456f; // Seed independent of stateTime for consistent speed
            float offsetX = (float) Math.sin(seed * 0.7f + realTime * timeScale) * 0.4f;
            float offsetZ = (float) Math.cos(seed * 0.8f + realTime * timeScale * 1.1f) * 0.4f;
            // Slower upward drift: reduced constant in realTime multiplier
            float offsetY = (float) ((realTime * 1.5f + seed) % 1.5f);

            com.badlogic.gdx.graphics.g3d.Model fireModel;
            int colorPick = (int) (seed * 10f + realTime * 5f) % 6;
            boolean isSphere = (i % 2 == 0); // Alternate shapes

            if (colorPick == 0)
                fireModel = isSphere ? redSphereModel1 : redFireModel1;
            else if (colorPick == 1)
                fireModel = isSphere ? redSphereModel2 : redFireModel2;
            else if (colorPick == 2)
                fireModel = isSphere ? redSphereModel3 : redFireModel3;
            else if (colorPick == 3)
                fireModel = isSphere ? yellowSphereModel1 : yellowFireModel1;
            else if (colorPick == 4)
                fireModel = isSphere ? yellowSphereModel2 : yellowFireModel2;
            else
                fireModel = isSphere ? yellowSphereModel3 : yellowFireModel3;

            if (fireModel == null)
                continue;

            float sizeScale = 1.0f - offsetY / 1.5f;
            if (sizeScale <= 0)
                continue;

            ModelInstance firePart = new ModelInstance(fireModel);
            firePart.transform.setToTranslation(x + offsetX, y + offsetY, z + offsetZ);
            firePart.transform.scale(sizeScale, sizeScale, sizeScale);
            // Slower rotation
            firePart.transform.rotate(com.badlogic.gdx.math.Vector3.Y, realTime * 150f + seed * 100f);
            instances.add(firePart);
        }
    }

    public void visitSensor(Sensor sensor) {
        if (sensor.getPosition() == null)
            return;

        float x = sensor.getPosition().getX();
        float y = sensor.getPosition().getY();
        letrain.map.Dir d = sensor.getCreationDir();
        if (d == null)
            d = letrain.map.Dir.N;

        float dx = getDirX(d);
        float dz = getDirZ(d);
        float angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        ModelInstance instance = new ModelInstance(cursorModel);
        instance.materials.get(0)
                .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                        .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW));
        // Raise to rail level (Y=0.08f) and scale Y by 10 to match rail height (0.2f
        // total)
        instance.transform.setToTranslation(x + 0.5f, 0.08f, y + 0.5f);
        instance.transform.rotate(0, 1, 0, angle - 90f);
        instance.transform.scale(0.7f, 10f, 0.25f);
        instances.add(instance);

        // Render ID label flat on top of the sensor
        String idText = String.valueOf(sensor.getId());
        float labelHeight = 0.19f; // Just above the new 0.20f top surface (0.08f + 0.10f) -> 0.18f + 0.01f margin
        labels.add(new VehicleLabel(new com.badlogic.gdx.math.Vector3(x + 0.5f, labelHeight, y + 0.5f), idText,
                new com.badlogic.gdx.math.Vector3(0, 1, 0), new com.badlogic.gdx.math.Vector3(0, 0, -1),
                com.badlogic.gdx.graphics.Color.BLACK, 0.4f));
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
            letrain.track.Track track = modelRef.getRailMap().getTrackAt((int) x, (int) y);
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
        if (station.getPosition() == null)
            return;

        letrain.track.rail.RailTrack track = (letrain.track.rail.RailTrack) station.getTrack();
        if (track == null) {
            track = modelRef.getRailMap().getTrackAt(station.getPosition());
        }

        if (track == null)
            return;

        drawStation(station, station.getPosition(), station.getCargoType(), station.getRole(), track,
                station.getId(), station.getName(), (modelRef != null && modelRef.getSelectedStation() == station),
                1.0f);
    }

    private void drawStation(letrain.track.Station station, letrain.map.Point pos, CargoTypes cargo,
            CargoTypes.StationRole role,
            letrain.track.rail.RailTrack track, int id, String name, boolean selected, float alpha) {

        float xIndex = pos.getX();
        float zIndex = pos.getY();

        letrain.map.Dir rightDir = (station != null && station.getSideDir() != null)
                ? station.getSideDir()
                : getValidOrientation(track).turnRight().turnRight();
        letrain.map.Dir orientation = (station != null && station.getSideDir() != null)
                ? station.getSideDir().turnLeft().turnLeft()
                : getValidOrientation(track);

        float perpX = getDirX(rightDir);
        float perpZ = getDirZ(rightDir);
        float lenPerp = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (lenPerp > 0) {
            perpX /= lenPerp;
            perpZ /= lenPerp;
        }

        float paraX = getDirX(orientation);
        float paraZ = getDirZ(orientation);
        float lenPara = (float) Math.sqrt(paraX * paraX + paraZ * paraZ);
        if (lenPara > 0) {
            paraX /= lenPara;
            paraZ /= lenPara;
        }

        // Proximity refinement: move structure closer to track
        float distPlatform = 0.65f;
        float centerX = xIndex + 0.5f + perpX * distPlatform;
        float centerZ = zIndex + 0.5f + perpZ * distPlatform;

        float mastHeight = 1.2f;
        if (selected) {
            mastHeight = 2.0f;
        }

        com.badlogic.gdx.graphics.Color structureColor = (cargo != null) ? cargo.getColor().cpy()
                : com.badlogic.gdx.graphics.Color.WHITE.cpy();
        structureColor.a = alpha;

        // Blinking logic (Action Active)
        boolean isActionActive = false;
        if (modelRef != null && station != null) {
            for (letrain.vehicle.impl.rail.Locomotive loc : modelRef.getLocomotives()) {
                letrain.vehicle.impl.rail.Train train = loc.getTrain();
                if (train != null && train.isLoading()) {
                    if (train.getStationAtTrain() == station) {
                        isActionActive = true;
                        break;
                    }
                }
            }
        }

        com.badlogic.gdx.graphics.Color boardColor = structureColor.cpy();
        if (isActionActive) {
            // Blinking effect: swap between base color and highlight
            if (System.currentTimeMillis() % 400 < 200) {
                boardColor = com.badlogic.gdx.graphics.Color.WHITE.cpy();
                boardColor.a = alpha;
            }
        }

        com.badlogic.gdx.graphics.Color mastColor = com.badlogic.gdx.graphics.Color.GRAY.cpy();
        mastColor.a = alpha;

        // 0. Expansive plate: covers track tile + mast area
        float plateLengthPerp = distPlatform + 0.5f;
        float plateWidth = 1.0f;
        float plateMidX = xIndex + 0.5f + perpX * (distPlatform / 2f);
        float plateMidZ = zIndex + 0.5f + perpZ * (distPlatform / 2f);
        float plateAngle = (float) Math.atan2(perpX, perpZ) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        ModelInstance plate = new ModelInstance(wagonJewelModel);
        plate.materials.get(0)
                .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(structureColor));
        plate.transform.setToTranslation(plateMidX, 0.01f, plateMidZ); // Lowered
        plate.transform.rotate(0, 1, 0, plateAngle);
        plate.transform.scale(plateLengthPerp, 0.01f, plateWidth); // Thinner
        if (alpha < 1.0f) {
            plate.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(plate);

        // 1. Mástil (Sturdier)
        ModelInstance mast = new ModelInstance(cylinderModel);
        mast.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(mastColor));
        mast.transform.setToTranslation(centerX, mastHeight / 2f, centerZ);
        mast.transform.rotate(0, 1, 0, plateAngle); // Rotated to match angle
        mast.transform.scale(0.15f, mastHeight, 0.15f); // Thicker
        if (alpha < 1.0f) {
            mast.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(mast);

        // 2. Cartel (Cube Sign)
        float boardSize = 0.6f;
        ModelInstance board = new ModelInstance(wagonJewelModel);
        board.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(boardColor));
        board.transform.setToTranslation(centerX, mastHeight + (boardSize / 2f), centerZ);
        board.transform.rotate(0, 1, 0, plateAngle); // Rotated to match angle
        board.transform.scale(boardSize, boardSize, boardSize);
        if (alpha < 1.0f) {
            board.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(board);

        // Labels (IDs on all 4 faces)
        float boardCenterY = mastHeight + (boardSize / 2f);
        com.badlogic.gdx.graphics.Color labelColor = com.badlogic.gdx.graphics.Color.WHITE.cpy();
        labelColor.a = alpha;

        String idText = (id >= 0) ? String.valueOf(id) : "?";
        float labelOffset = boardSize / 2f + 0.01f;

        // Face 1: Front
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(centerX + perpX * labelOffset, boardCenterY,
                        centerZ + perpZ * labelOffset),
                idText, new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ), labelColor));

        // Face 2: Back
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(centerX - perpX * labelOffset, boardCenterY,
                        centerZ - perpZ * labelOffset),
                idText, new com.badlogic.gdx.math.Vector3(-perpX, 0, -perpZ), labelColor));

        // Face 3: Side Para
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(centerX + paraX * labelOffset, boardCenterY,
                        centerZ + paraZ * labelOffset),
                idText, new com.badlogic.gdx.math.Vector3(paraX, 0, paraZ), labelColor));

        // Face 4: Side -Para
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(centerX - paraX * labelOffset, boardCenterY,
                        centerZ - paraZ * labelOffset),
                idText, new com.badlogic.gdx.math.Vector3(-paraX, 0, -paraZ), labelColor));

    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
        groundMap.forEach(ground -> visitGround(ground));
    }

    @Override
    public void visitGround(Ground ground) {
        if (!isVisible(ground.getPosition()))
            return;
        int type = ground.getType();
        com.badlogic.gdx.graphics.g3d.Model model = groundModel;
        float yPosition = 0.0f;
        float scaleX = 1.0f;
        float scaleY = 0.01f;
        float scaleZ = 1.0f;
        com.badlogic.gdx.graphics.Color colorOverride = null;

        if (type >= 10 && type <= 19) {
            // PRODUCER - Solid Crystal Jewel Block
            CargoTypes cargo = CargoTypes.IndustryMapper.getCargoForTerrain(type);
            com.badlogic.gdx.graphics.Color jewelColor = (cargo != null) ? cargo.getColor().cpy()
                    : com.badlogic.gdx.graphics.Color.WHITE.cpy();

            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;

            ModelInstance jewelBlock = new ModelInstance(wagonJewelModel);
            jewelBlock.materials.get(0).set(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(jewelColor));

            // Single large block (footprint 0.9x0.9, height 0.5)
            float h = 0.5f;
            jewelBlock.transform.setToTranslation(x, h / 2f, z);
            jewelBlock.transform.scale(0.9f, h, 0.9f);

            instances.add(jewelBlock);
            return;
        } else if (type >= 20 && type <= 29) {
            // CONSUMER - Optimized Pre-built Icon Model
            CargoTypes cargo = CargoTypes.IndustryMapper.getCargoForTerrain(type);
            com.badlogic.gdx.graphics.g3d.Model consumerModelToUse = coalConsumerModel;
            if (cargo == CargoTypes.GOLD)
                consumerModelToUse = goldConsumerModel;
            else if (cargo == CargoTypes.RUBY)
                consumerModelToUse = rubyConsumerModel;

            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;

            ModelInstance instance = new ModelInstance(consumerModelToUse);
            instance.transform.setToTranslation(x, 0.01f, z);
            instances.add(instance);
            return;
        } else {
            switch (type) {
                case GroundMap.GROUND:
                    model = groundModel;
                    yPosition = 0.0f;
                    break;
                case GroundMap.WATER:
                    model = waterModel;
                    yPosition = -2.0f;
                    break;
                case GroundMap.ROCK:
                    model = mountainModel;
                    yPosition = 0.6f;
                    scaleY = 1.2f;
                    break;
            }
        }

        if (model != null) {
            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;

            ModelInstance instance = new ModelInstance(model);
            if (colorOverride != null) {
                instance.materials.get(0)
                        .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(colorOverride));
            }

            // Mode-based transparency for Rocks/Tunnels
            if (type == GroundMap.ROCK || model == tunnelPortalModel) {
                if (isXRayActive) {
                    instance.materials.get(0)
                            .set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
                    // No DepthTestAttribute here, we manage it globally in the Two-Pass loop
                    instance.transform.setToTranslation(x, yPosition, z);
                    instance.transform.scale(scaleX, scaleY, scaleZ);
                    transparentInstances.add(instance);
                } else {
                    instance.transform.setToTranslation(x, yPosition, z);
                    instance.transform.scale(scaleX, scaleY, scaleZ);
                    instances.add(instance);
                }
            } else {
                instance.transform.setToTranslation(x, yPosition, z);
                instance.transform.scale(scaleX, scaleY, scaleZ);
                instances.add(instance);
            }

            // Si no es agua, comprobamos vecinos para poner "paredes" hacia el agua
            if (type != GroundMap.WATER && modelRef != null && modelRef.getGroundMap() != null) {
                int gx = ground.getPosition().getX();
                int gy = ground.getPosition().getY();

                // Color de la pared: si es roca, color montaña. Si no, color tierra.
                com.badlogic.gdx.graphics.Color wallColor = (type == GroundMap.ROCK)
                        ? new com.badlogic.gdx.graphics.Color(0.5f, 0.4f, 0.3f, 1f)
                        : new com.badlogic.gdx.graphics.Color(0.4f, 0.6f, 0.3f, 1f);

                checkAndAddWall(gx, gy - 1, x, -1.05f, z - 0.5f, 0, wallColor); // Norte
                checkAndAddWall(gx, gy + 1, x, -1.05f, z + 0.5f, 0, wallColor); // Sur
                checkAndAddWall(gx - 1, gy, x - 0.5f, -1.05f, z, 90, wallColor); // Oeste
                checkAndAddWall(gx + 1, gy, x + 0.5f, -1.05f, z, 90, wallColor); // Este
            }
        }
    }

    private void checkAndAddWall(int gx, int gy, float x, float y, float z, float rotationY,
            com.badlogic.gdx.graphics.Color color) {
        Integer neighborType = modelRef.getGroundMap().getValueAt(gx, gy);
        if (neighborType != null && neighborType == GroundMap.WATER) {
            ModelInstance wall = new ModelInstance(terrainWallModel);
            wall.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));
            wall.transform.setToTranslation(x, y, z);
            if (rotationY != 0) {
                wall.transform.rotate(0, 1, 0, rotationY);
            }
            instances.add(wall);
        }
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        // El pilar ahora se gestiona automáticamente en visitRailTrack si detecta agua.
        // Pero por si acaso no hay agua (puente sobre tierra?), mantenemos la lógica
        // o la unificamos.
        // visitRailTrack ya se llama al final.
        visitRailTrack(bridgeGateRailTrack);
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        // El pilar ahora se gestiona automáticamente en visitRailTrack si detecta agua.
        visitRailTrack(bridgeRailTrack);
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        // Renderizar portal del túnel como bloque negro simple
        ModelInstance portal = new ModelInstance(tunnelPortalModel);
        if (isXRayActive) {
            portal.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
            portal.transform.setToTranslation(
                    tunnelGateRailTrack.getPosition().getX() + 0.5f,
                    0.6f, // Mismo nivel que montañas
                    tunnelGateRailTrack.getPosition().getY() + 0.5f);
            transparentInstances.add(portal);
        } else {
            portal.transform.setToTranslation(
                    tunnelGateRailTrack.getPosition().getX() + 0.5f,
                    0.6f, // Mismo nivel que montañas
                    tunnelGateRailTrack.getPosition().getY() + 0.5f);
            instances.add(portal);
        }

        // Renderizar vías normales
        visitRailTrack(tunnelGateRailTrack);
    }

    // deleted getContainerModel

    // Helper para crear cajas huecas (sin tapa superior)
    private com.badlogic.gdx.graphics.g3d.Model createOpenBox(
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder,
            float w, float h, float d,
            com.badlogic.gdx.graphics.Color color) {

        modelBuilder.begin();
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpb;
        com.badlogic.gdx.graphics.g3d.Material mat = new com.badlogic.gdx.graphics.g3d.Material(
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));

        float thickness = 0.05f;

        // Floor
        mpb = modelBuilder.part("floor", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                mat);
        mpb.setVertexTransform(new com.badlogic.gdx.math.Matrix4().setToTranslation(0, -h / 2f + thickness / 2f, 0));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, w, thickness, d);

        // Wall Front
        mpb = modelBuilder.part("wall_front", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                mat);
        mpb.setVertexTransform(new com.badlogic.gdx.math.Matrix4().setToTranslation(0, 0, d / 2f - thickness / 2f));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, w, h, thickness);

        // Wall Back
        mpb = modelBuilder.part("wall_back", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                mat);
        mpb.setVertexTransform(new com.badlogic.gdx.math.Matrix4().setToTranslation(0, 0, -d / 2f + thickness / 2f));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, w, h, thickness);

        // Wall Left
        mpb = modelBuilder.part("wall_left", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                mat);
        mpb.setVertexTransform(new com.badlogic.gdx.math.Matrix4().setToTranslation(-w / 2f + thickness / 2f, 0, 0));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, thickness, h, d - 2 * thickness);

        // Wall Right
        mpb = modelBuilder.part("wall_right", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                mat);
        mpb.setVertexTransform(new com.badlogic.gdx.math.Matrix4().setToTranslation(w / 2f - thickness / 2f, 0, 0));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpb, thickness, h, d - 2 * thickness);

        return modelBuilder.end();
    }

    private boolean isPredictiveMoveBlocked(letrain.vehicle.impl.Linker linker,
            java.util.Set<letrain.vehicle.impl.Linker> visited) {
        if (!visited.add(linker))
            return false;

        letrain.track.Track currentTrack = linker.getTrack();
        if (currentTrack == null)
            return true;

        letrain.track.Track nextTrack = currentTrack.getConnected(linker.getDir());
        if (nextTrack == null)
            return true;

        // Check if we can enter (includes linker and reservation checks)
        // Dir is inversion of current move direction
        if (!nextTrack.canEnter(linker.getDir().inverse(), linker)) {
            letrain.vehicle.impl.Linker occupying = nextTrack.getLinker();
            letrain.vehicle.impl.Linker reservation = nextTrack.getReservation();

            boolean blockedByOther = (occupying != null && occupying.getTrain() != linker.getTrain())
                    || (reservation != null && reservation.getTrain() != linker.getTrain());

            if (blockedByOther)
                return true;

            // If it's blocked by OUR train, we need to check if THAT linker is also
            // blocked.
            if (occupying != null && occupying.getTrain() == linker.getTrain()) {
                return isPredictiveMoveBlocked(occupying, visited);
            }

            // If it's blocked by something else in canEnter (router/sensor), it's blocked.
            return true;
        }

        return false;
    }
}
