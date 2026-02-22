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
    private List<ModelInstance> instances = new ArrayList<>();

    public List<ModelInstance> getInstances() {
        return instances;
    }

    private com.badlogic.gdx.graphics.g3d.utils.ModelBuilder modelBuilder;
    private com.badlogic.gdx.graphics.g3d.Model railModel;
    private com.badlogic.gdx.graphics.g3d.Model inactiveRailModel;
    private com.badlogic.gdx.graphics.g3d.Model sirenModel;
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
    private Set<letrain.track.rail.RailTrack> selectedStationTracks = new HashSet<>();
    private com.badlogic.gdx.graphics.g3d.Model stationSignModel;
    private com.badlogic.gdx.graphics.g3d.Model stationSignSelectedModel;
    private com.badlogic.gdx.graphics.g3d.Model platformModel;
    private com.badlogic.gdx.graphics.g3d.Model platformSelectedModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonCargoModel;
    private com.badlogic.gdx.graphics.g3d.Model wagonJewelModel;

    // Industrial Zone Models
    private com.badlogic.gdx.graphics.g3d.Model forestModel;
    private com.badlogic.gdx.graphics.g3d.Model sawmillModel;
    private com.badlogic.gdx.graphics.g3d.Model mineModel;
    private com.badlogic.gdx.graphics.g3d.Model powerPlantModel;
    private com.badlogic.gdx.graphics.g3d.Model portModel;
    private com.badlogic.gdx.graphics.g3d.Model marketModel;

    public static class VehicleLabel {
        public com.badlogic.gdx.math.Vector3 pos;
        public String text;
        public com.badlogic.gdx.math.Vector3 normal;
        public com.badlogic.gdx.graphics.Color color;

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal) {
            this(pos, text, normal, com.badlogic.gdx.graphics.Color.WHITE);
        }

        public VehicleLabel(com.badlogic.gdx.math.Vector3 pos, String text, com.badlogic.gdx.math.Vector3 normal,
                com.badlogic.gdx.graphics.Color color) {
            this.pos = pos;
            this.text = text;
            this.normal = normal;
            this.color = color;
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

            // Vagón simple (Bloque Azul con transparencia)
            // wagonModel moved down

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

            // Chasis del vagón (Caja azul hueca)
            wagonModel = createOpenBox(modelBuilder, 0.8f, 0.6f, 0.8f,
                    new com.badlogic.gdx.graphics.Color(0.5f, 0.5f, 0.5f, 1f)); // Gray color

            // deleted legacy container initializers

            // deleted unused container initializers

            // ----------------------------------------------------------------------------------
            // FIN NUEVOS MODELOS DISPONIBLES
            // ----------------------------------------------------------------------------------

            wagonCargoModel = modelBuilder.createBox(0.6f, 0.5f, 0.6f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.ORANGE)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // SOLID CARGO BLOCK (Unit cube 1x1x1, shiny, opaque)
            wagonJewelModel = modelBuilder.createBox(1.0f, 1.0f, 1.0f,
                    new com.badlogic.gdx.graphics.g3d.Material(
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE),
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createSpecular(com.badlogic.gdx.graphics.Color.WHITE),
                            com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.createShininess(16f)),
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

            // Estación (Mastil con letrero)
            // 1. Modelo normal (Poste Gris, Cubo Blanco)
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mbStation = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mbStation.begin();
            // Poste (Gris) - 1.6f de alto
            mbStation.node().id = "pole";
            com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpbSign = mbStation.part("pole",
                    com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpbSign, 0.05f, 1.6f, 0.05f,
                    10);

            // Letrero (Cubo Blanco)
            com.badlogic.gdx.graphics.g3d.model.Node signNode = mbStation.node();
            signNode.id = "sign";
            signNode.translation.set(0, 0.8f, 0); // Arriba del poste (1.6/2)
            mpbSign = mbStation.part("sign", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSign, 0.6f, 0.6f, 0.6f);
            stationSignModel = mbStation.end();

            // 2. Modelo seleccionado (Poste Amarillo, Cubo Verde)
            mbStation = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mbStation.begin();
            // Poste (Amarillo)
            mbStation.node().id = "pole";
            mpbSign = mbStation.part("pole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.YELLOW)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpbSign, 0.05f, 1.6f, 0.05f,
                    10);

            // Letrero (Cubo Verde)
            signNode = mbStation.node();
            signNode.id = "sign";
            signNode.translation.set(0, 0.8f, 0);
            mpbSign = mbStation.part("sign", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.GREEN)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSign, 0.6f, 0.6f, 0.6f);
            stationSignSelectedModel = mbStation.end();

            platformModel = modelBuilder.createBox(1.2f, 0.2f, 2.4f,
                    new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                            .createDiffuse(com.badlogic.gdx.graphics.Color.LIGHT_GRAY)),
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);

            // Resaltado de estación (La propia superficie en verde cuando se selecciona)
            platformSelectedModel = modelBuilder.createBox(1.2f, 0.2f, 2.4f,
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
            // Alargado a 0.85f para cubrir huecos en curvas
            ballastModel = modelBuilder.createBox(0.5f, 0.1f, 0.85f,
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

            // INDUSTRIAL MODELS (Hollow boxes like wagons)
            forestModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.WOOD.getColor());
            sawmillModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.WOOD.getColor());

            mineModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.COAL.getColor());
            powerPlantModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.COAL.getColor());

            portModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.FISH.getColor());
            marketModel = createOpenBox(modelBuilder, 0.9f, 0.6f, 0.9f, letrain.track.CargoTypes.FISH.getColor());

            // ----------------------------------------------------------------------------------
            // STATION SIREN (Red/White rotating block)
            // ----------------------------------------------------------------------------------
            com.badlogic.gdx.graphics.g3d.utils.ModelBuilder mbSiren = new com.badlogic.gdx.graphics.g3d.utils.ModelBuilder();
            mbSiren.begin();

            // Note: Larger solid boxes to ensure 100% visibility from all angles.
            // Red half
            com.badlogic.gdx.graphics.g3d.model.Node nodeR = mbSiren.node();
            nodeR.id = "red";
            nodeR.translation.set(0.101f, 0, 0); // Positioned so they join in the center
            com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpbSR = mbSiren.part("red",
                    com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createDiffuse(com.badlogic.gdx.graphics.Color.RED),
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createEmissive(com.badlogic.gdx.graphics.Color.RED) // Glow for visibility
                    ));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSR, 0.2f, 0.4f, 0.2f);

            // White half
            com.badlogic.gdx.graphics.g3d.model.Node nodeW = mbSiren.node();
            nodeW.id = "white";
            nodeW.translation.set(-0.101f, 0, 0); // Join in center
            com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpbSW = mbSiren.part("white",
                    com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                    com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                            | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                    new com.badlogic.gdx.graphics.g3d.Material(
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createDiffuse(com.badlogic.gdx.graphics.Color.WHITE),
                            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                    .createEmissive(com.badlogic.gdx.graphics.Color.WHITE)));
            com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSW, 0.2f, 0.4f, 0.2f);

            sirenModel = mbSiren.end();
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
        model.getStations().forEach(s -> s.accept(this));
        model.getLocomotives().forEach(l -> l.accept(this));
        model.getWagons().forEach(w -> w.accept(this));
        visitCursor(model.getCursor());
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

        // STATION GHOST PREVIEW
        if (modelRef != null
                && (modelRef.getMode() == Model.GameMode.RAILS || modelRef.getMode() == Model.GameMode.STATIONS)) {
            letrain.track.rail.RailTrack rt = modelRef.getCursorRailTrack();
            if (rt != null && rt.getSensor() == null) {
                CargoTypes cargo = modelRef.getStationGhostCargoType();
                CargoTypes.StationRole role = modelRef.getStationGhostRole();
                drawStation(null, pos, cargo, role, rt, -1, "NEW STATION", false, 0.5f);
            }
        }
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
        if (sirenModel != null)
            sirenModel.dispose();
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
        if (wagonCargoModel != null)
            wagonCargoModel.dispose();
        if (semaphoreOpenModel != null)
            semaphoreOpenModel.dispose();
        if (semaphoreClosedModel != null)
            semaphoreClosedModel.dispose();
        if (sensorModel != null)
            sensorModel.dispose();
        if (platformSelectedModel != null)
            platformSelectedModel.dispose();
        if (forestModel != null)
            forestModel.dispose();
        if (sawmillModel != null)
            sawmillModel.dispose();
        if (mineModel != null)
            mineModel.dispose();
        if (powerPlantModel != null)
            powerPlantModel.dispose();
        if (portModel != null)
            portModel.dispose();
        if (marketModel != null)
            marketModel.dispose();
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
                        // Ángulo inicial: Dirección actual de movimiento
                        float startAngle = locomotive.getDir().getValue() * 45f;

                        // Ángulo objetivo: Dirección que tomaremos en el siguiente track
                        // nextTrack.getDir(entryDir) nos da la dirección de salida dado una entrada.
                        // Nuestra dirección de entrada al nextTrack es locomotive.getDir().inverse()
                        // (Entramos desde el Lado Opuesto)

                        letrain.map.Dir nextDir = nextTrack.getDir(locomotive.getDir().inverse());
                        float targetAngle = nextDir != null ? nextDir.getValue() * 45f : startAngle;

                        // Corregir wrapping de ángulos (360 -> 0)
                        // Si start=315 (NW), target=0 (E). Diff = -315. Shortest = +45.
                        // MathUtils.lerpAngleDeg maneja esto internamente? Si.

                        // Interpolar suavemente
                        // Usar la segunda mitad del progreso (0.5 -> 1.0) para girar DENTRO de la nueva
                        // celda
                        // "Tienen que girar en la curva, no antes"
                        // 0.0 -> 0.5: progress en celda anterior. rotProgress <= 0.
                        // 0.5 -> 1.0: progress en nueva celda. rotProgress 0 -> 1.
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

        instance.transform.setToTranslation(x + 0.5f, 0.6f, y + 0.5f);
        // float angle = locomotive.getDir().getValue() * 45f; // Ya calculado arriba o
        // default
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
        // Angle 0 (E) -> dx=1, dz=0. cos(0)=1, -sin(0)=0. Correcto.
        // Angle 90 (N) -> dx=0, dz=-1. cos(90)=0, -sin(90)=-1. Correcto.
        float dx = com.badlogic.gdx.math.MathUtils.cos(rad);
        float dz = -com.badlogic.gdx.math.MathUtils.sin(rad);

        // Perpendicular: (dz, -dx)
        float perpX = dz * 0.42f;
        float perpZ = -dx * 0.42f;

        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(x + 0.5f + perpX, 0.6f, y + 0.5f + perpZ),
                locomotive.getAspect(),
                new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ).nor()));

        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(x + 0.5f - perpX, 0.6f, y + 0.5f - perpZ),
                locomotive.getAspect(),
                new com.badlogic.gdx.math.Vector3(-perpX, 0, -perpZ).nor()));
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
        instance.transform.setToTranslation(x + 0.5f, wagonY, y + 0.5f);
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

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
        float rad = angle * com.badlogic.gdx.math.MathUtils.degreesToRadians;

        float dx = com.badlogic.gdx.math.MathUtils.cos(rad);
        float dz = -com.badlogic.gdx.math.MathUtils.sin(rad);

        // Perpendicular: (dz, -dx)
        float perpX = dz * 0.42f;
        float perpZ = -dx * 0.42f;

        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(x + 0.5f + perpX, 0.6f, y + 0.5f + perpZ),
                wagon.getAspect(),
                new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ).nor()));
        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(x + 0.5f - perpX, 0.6f, y + 0.5f - perpZ),
                wagon.getAspect(),
                new com.badlogic.gdx.math.Vector3(-perpX, 0, -perpZ).nor()));
    }

    @Override
    public void visitSensor(Sensor sensor) {
        if (sensor.getPosition() == null)
            return;

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

        letrain.map.Dir orientation = getValidOrientation(track);
        letrain.map.Dir rightDir = orientation.turnRight().turnRight();

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

        float distPlatform = 0.8f;
        float centerX = xIndex + 0.5f + perpX * distPlatform;
        float centerZ = zIndex + 0.5f + perpZ * distPlatform;

        float mastHeight = 1.2f;
        if (selected) {
            mastHeight = 2.0f;
        }

        com.badlogic.gdx.graphics.Color structureColor = (cargo != null) ? cargo.getColor().cpy()
                : com.badlogic.gdx.graphics.Color.WHITE.cpy();
        structureColor.a = alpha;

        com.badlogic.gdx.graphics.Color mastColor = com.badlogic.gdx.graphics.Color.GRAY.cpy();
        mastColor.a = alpha;

        // 0. Expansive plate: covers track tile + mast area in perpendicular direction
        // Length: from tile center to mast center + some margin
        float plateLengthPerp = distPlatform + 0.6f; // spans track -> mast
        float plateWidth = 1.0f; // full tile width along para direction
        float plateMidX = xIndex + 0.5f + perpX * (distPlatform / 2f);
        float plateMidZ = zIndex + 0.5f + perpZ * (distPlatform / 2f);
        // Angle so that the long side aligns with perpendicular direction
        float plateAngle = (float) Math.atan2(perpX, perpZ) * MathUtils.radiansToDegrees;

        ModelInstance plate = new ModelInstance(getBoxModel(structureColor));
        plate.transform.setToTranslation(plateMidX, 0.015f, plateMidZ);
        plate.transform.rotate(0, 1, 0, plateAngle);
        plate.transform.scale(plateLengthPerp, 0.02f, plateWidth);
        if (alpha < 1.0f) {
            plate.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(plate);

        // 1. Mástil
        ModelInstance mast = new ModelInstance(getCylinderModel(mastColor));
        mast.transform.setToTranslation(centerX, mastHeight / 2f, centerZ);
        mast.transform.scale(0.05f, mastHeight, 0.05f);
        if (alpha < 1.0f) {
            mast.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(mast);

        // 2. Cartel (Cubo de color de mercancía)
        float boardSize = 0.6f;
        ModelInstance board = new ModelInstance(getBoxModel(structureColor));
        board.transform.setToTranslation(centerX, mastHeight + (boardSize / 2f), centerZ);
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

        // Role indicator (Keep it slightly above for clarity)
        String roleStr = (role == CargoTypes.StationRole.PRODUCER) ? "PROD" : "CONS";
        com.badlogic.gdx.graphics.Color roleColor = (role == CargoTypes.StationRole.PRODUCER)
                ? com.badlogic.gdx.graphics.Color.YELLOW.cpy()
                : com.badlogic.gdx.graphics.Color.CYAN.cpy();
        roleColor.a = alpha;

        labels.add(new VehicleLabel(
                new com.badlogic.gdx.math.Vector3(centerX, mastHeight + boardSize + 0.2f, centerZ),
                roleStr, new com.badlogic.gdx.math.Vector3(perpX, 0, perpZ), roleColor));

        // 3. Siren (Optional: only if a train is loading here)
        boolean showSiren = false;
        if (modelRef != null && station != null) {
            for (letrain.vehicle.impl.rail.Locomotive loc : modelRef.getLocomotives()) {
                letrain.vehicle.impl.rail.Train train = loc.getTrain();
                if (train != null && train.isLoading()) {
                    letrain.track.Track t = loc.getTrack();
                    if (t != null && t.getSensor() == station) {
                        showSiren = true;
                        break;
                    }
                }
            }
        }

        if (showSiren) {
            ModelInstance siren = new ModelInstance(sirenModel);
            float sirenY = mastHeight + boardSize + 0.1f; // On top of the board
            siren.transform.setToTranslation(centerX, sirenY, centerZ);

            // Rotating effect using system time for smoothness
            float rotation = (System.currentTimeMillis() % 1000) / 1000f * 360f * 2f; // 2 rotations per second
            siren.transform.rotate(0, 1, 0, rotation);
            instances.add(siren);
        }

        // 4. Render Station Storage Jewels (PRODUCERS ONLY)
        if (role == CargoTypes.StationRole.PRODUCER && cargo != null && station != null) {
            int storage = station.getStorage();
            int maxStorage = station.getMaxStorage();
            if (storage > 0) {
                com.badlogic.gdx.graphics.Color jewelColor = cargo.getColor().cpy();
                jewelColor.a = alpha;

                float fullness = (float) storage / (float) maxStorage;
                float currentHeight = fullness * 0.5f; // Max height 0.5f

                ModelInstance jewelBlock = new ModelInstance(wagonJewelModel);
                jewelBlock.materials.get(0).set(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(jewelColor));
                if (alpha < 1.0f) {
                    jewelBlock.materials.get(0)
                            .set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
                }

                // Center on the plate
                float jewelY = 0.015f + (currentHeight / 2f);

                jewelBlock.transform.setToTranslation(plateMidX, jewelY, plateMidZ);
                jewelBlock.transform.rotate(0, 1, 0, plateAngle);
                jewelBlock.transform.scale(plateLengthPerp * 0.8f, currentHeight, plateWidth * 0.8f);

                instances.add(jewelBlock);
            }
        }
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
        groundMap.forEach(ground -> visitGround(ground));
    }

    @Override
    public void visitGround(Ground ground) {
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
            // CONSUMER - Flat Carpet (Keep it simple, as it was before)
            CargoTypes cargo = CargoTypes.IndustryMapper.getCargoForTerrain(type);
            colorOverride = (cargo != null) ? cargo.getColor() : com.badlogic.gdx.graphics.Color.WHITE;
            yPosition = 0.02f;
            scaleY = 0.04f;
            scaleX = 0.95f;
            scaleZ = 0.95f;
        } else {
            switch (type) {
                case GroundMap.GROUND:
                    model = groundModel;
                    yPosition = 0.0f;
                    break;
                case GroundMap.WATER:
                    model = waterModel;
                    yPosition = -0.05f;
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
            instance.transform.setToTranslation(x, yPosition, z);
            instance.transform.scale(scaleX, scaleY, scaleZ);
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

    private com.badlogic.gdx.graphics.g3d.Model getBoxModel(com.badlogic.gdx.graphics.Color color) {
        return modelBuilder.createBox(1f, 1f, 1f,
                new com.badlogic.gdx.graphics.g3d.Material(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color)),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    }

    private com.badlogic.gdx.graphics.g3d.Model getCylinderModel(com.badlogic.gdx.graphics.Color color) {
        return modelBuilder.createCylinder(1f, 1f, 1f, 24,
                new com.badlogic.gdx.graphics.g3d.Material(
                        com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color)),
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                        | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal);
    }
}
