package letrain.visitor.gdx3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pool;

import letrain.track.CargoTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the lifecycle and initialization of 3D models.
 * Extracted from Gdx3DRenderer to separate asset management from rendering logic.
 */
public class Gdx3DResourceContext implements Disposable {
    private final List<Model> models = new ArrayList<>();
    private ModelBuilder modelBuilder;

    private static final Color HIGHLIGHT_TRANSLUCENT_YELLOW = new Color(1f, 1f, 0f, 0.75f);
    private static final Color HIGHLIGHT_TRANSLUCENT_RED = new Color(1f, 0f, 0f, 0.75f);

    // --- Object Pooling ---
    private final Map<Model, Pool<ModelInstance>> pools = new HashMap<>();
    private final List<ModelInstance> activeInstances = new ArrayList<>();

    private final Pool<Decal> decalPool = new Pool<Decal>() {
        @Override
        protected Decal newObject() {
            // Decal needs a TextureRegion to be initialized, we'll set the real one in getDecal
            return Decal.newDecal(new TextureRegion());
        }
    };
    private final List<Decal> activeDecals = new ArrayList<>();

    public ModelInstance getModelInstance(Model model) {
        if (model == null) return null;
        Pool<ModelInstance> pool = pools.computeIfAbsent(model, m -> new Pool<ModelInstance>() {
            @Override
            protected ModelInstance newObject() {
                return new ModelInstance(m);
            }
        });
        ModelInstance instance = pool.obtain();
        // Reset transform and materials to default state from model
        instance.transform.idt();
        // LibGDX ModelInstance shares materials but let's ensure we don't carry over blending from X-ray
        for (int i = 0; i < instance.materials.size; i++) {
            instance.materials.get(i).clear();
            instance.materials.get(i).set(model.materials.get(i));
        }
        activeInstances.add(instance);
        return instance;
    }

    public Decal getDecal(TextureRegion region) {
        Decal decal = decalPool.obtain();
        decal.setTextureRegion(region);
        decal.setBlending(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        activeDecals.add(decal);
        return decal;
    }

    public void freeAllInstances() {
        for (ModelInstance instance : activeInstances) {
            Pool<ModelInstance> pool = pools.get(instance.model);
            if (pool != null) {
                pool.free(instance);
            }
        }
        activeInstances.clear();

        for (Decal decal : activeDecals) {
            decalPool.free(decal);
        }
        activeDecals.clear();
    }
    // ----------------------

    public Model railModel;
    public Model inactiveRailModel;
    public Model invalidRailModel;
    public Model cursorModel;
    public Model locomotiveModel;
    public Model wagonModel;
    public Model highlightModel;
    public Model locomotiveHighlightModel;
    public Model wagonHighlightModel;
    public Model wagonUnlinkModel;
    public Model locomotiveUnlinkModel;
    public Model forkModel;
    public Model groundModel;
    public Model waterModel;
    public Model mountainModel;
    public Model ballastModel;
    public Model bridgePillarModel;
    public Model forkBaseModel;
    public Model selectedForkBaseModel;
    public Model forkBoxModel;
    public Model selectedForkBoxModel;
    public Model tunnelPortalModel;
    public Model terrainWallModel;
    public Model semaphoreOpenModel;
    public Model semaphoreClosedModel;
    public Model speedSignalMaxModel;
    public Model speedSignalMinModel;
    public Model sensorModel;
    public Model goldConsumerModel;
    public Model coalConsumerModel;
    public Model rubyConsumerModel;
    public Model wagonJewelModel;
    public Model cylinderModel;
    public Model selectionLineModel;
    public Model redFireModel1;
    public Model redFireModel2;
    public Model redFireModel3;
    public Model yellowFireModel1;
    public Model yellowFireModel2;
    public Model yellowFireModel3;
    public Model redSphereModel1;
    public Model redSphereModel2;
    public Model redSphereModel3;
    public Model yellowSphereModel1;
    public Model yellowSphereModel2;
    public Model yellowSphereModel3;
    public Model autoModeDotModel;

    public final com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute blackDiffuseAttribute = com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.BLACK);

    public void init() {
        if (modelBuilder == null) {
            modelBuilder = new ModelBuilder();

            // Raíl fino
            railModel = register(modelBuilder.createBox(0.06f, 0.2f, 0.7f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.8f, 0.8f, 0.85f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Raíl inactivo
            inactiveRailModel = register(modelBuilder.createBox(0.06f, 0.2f, 0.7f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.1f, 0.1f, 0.12f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Raíl mal conectado
            invalidRailModel = register(modelBuilder.createBox(0.4f, 0.4f, 0.4f,
                    new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Cursor
            cursorModel = register(modelBuilder.createCylinder(0.8f, 0.02f, 0.8f, 3,
                    new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Locomotora simple
            locomotiveModel = register(modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.6f, 0.6f, 0.6f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Modelo de resaltado
            highlightModel = register(modelBuilder.createBox(1.0f, 0.15f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Grey Fork Base
            forkBaseModel = register(modelBuilder.createBox(1.0f, 0.06f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(Color.GRAY)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // White Selected Fork Base
            selectedForkBaseModel = register(modelBuilder.createBox(1.0f, 0.06f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Locomotora amarilla (LINK)
            locomotiveHighlightModel = register(modelBuilder.createBox(0.85f, 0.85f, 0.85f,
                    new Material(ColorAttribute.createDiffuse(HIGHLIGHT_TRANSLUCENT_YELLOW),
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Línea de selección
            selectionLineModel = register(modelBuilder.createBox(0.06f, 0.02f, 0.5f,
                    new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Vagón amarillo (LINK)
            wagonHighlightModel = register(modelBuilder.createBox(0.85f, 0.85f, 0.85f,
                    new Material(ColorAttribute.createDiffuse(HIGHLIGHT_TRANSLUCENT_YELLOW),
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Chasis del vagón
            wagonModel = register(createOpenBox(0.8f, 0.6f, 0.8f, new Color(0.5f, 0.5f, 0.5f, 1f)));

            // Indicador de ruta en desvíos
            forkModel = register(modelBuilder.createBox(0.4f, 0.02f, 0.4f,
                    new Material(ColorAttribute.createDiffuse(Color.RED)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Thin Fork Plate
            forkBoxModel = register(modelBuilder.createBox(0.3f, 0.02f, 0.3f,
                    new Material(ColorAttribute.createDiffuse(Color.GRAY)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // White Selected Fork Plate
            selectedForkBoxModel = register(modelBuilder.createBox(0.3f, 0.02f, 0.3f,
                    new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            wagonJewelModel = register(modelBuilder.createBox(1.0f, 1.0f, 1.0f,
                    new Material(
                            ColorAttribute.createDiffuse(Color.WHITE),
                            ColorAttribute.createSpecular(Color.WHITE),
                            FloatAttribute.createShininess(16f)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            cylinderModel = register(modelBuilder.createCylinder(1f, 1f, 1f, 24,
                    new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // UNLINK
            locomotiveUnlinkModel = register(modelBuilder.createBox(0.85f, 0.85f, 0.85f,
                    new Material(ColorAttribute.createDiffuse(HIGHLIGHT_TRANSLUCENT_RED),
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            wagonUnlinkModel = register(modelBuilder.createBox(0.85f, 0.85f, 0.85f,
                    new Material(ColorAttribute.createDiffuse(HIGHLIGHT_TRANSLUCENT_RED),
                            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Semáforo Abierto
            semaphoreOpenModel = register(createSemaphoreModel(true));

            semaphoreClosedModel = register(createSemaphoreModel(false));
            speedSignalMaxModel = register(createSpeedSignalModel(true));
            speedSignalMinModel = register(createSpeedSignalModel(false));

            // Sensor
            sensorModel = register(modelBuilder.createBox(0.4f, 0.05f, 0.4f,
                    new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Indicador de modo automático en locomotora (puntito rojo)
            autoModeDotModel = register(modelBuilder.createBox(0.1f, 0.1f, 0.1f,
                    new Material(ColorAttribute.createDiffuse(Color.RED)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Terreno
            groundModel = register(modelBuilder.createBox(1.0f, 0.01f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.4f, 0.6f, 0.3f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            waterModel = register(modelBuilder.createBox(1.0f, 0.01f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.4f, 0.8f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            mountainModel = register(modelBuilder.createBox(1.0f, 1.2f, 1.0f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.5f, 0.4f, 0.3f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            ballastModel = register(modelBuilder.createBox(0.5f, 0.1f, 0.85f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.5f, 0.5f, 0.5f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            bridgePillarModel = register(modelBuilder.createBox(0.4f, 1.0f, 0.4f,
                    new Material(ColorAttribute.createDiffuse(new Color(0.5f, 0.5f, 0.5f, 1f))),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            tunnelPortalModel = register(createTunnelPortalModel());

            terrainWallModel = register(modelBuilder.createBox(1.0f, 2.1f, 0.05f,
                    new Material(ColorAttribute.createDiffuse(Color.GRAY)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));

            // Fire and Embers
            redFireModel1 = register(createPyramidModel(0.3f, 0.45f, 0.3f, new Color(0.6f, 0f, 0f, 1f)));
            redFireModel2 = register(createPyramidModel(0.3f, 0.45f, 0.3f, Color.RED));
            redFireModel3 = register(createPyramidModel(0.3f, 0.45f, 0.3f, new Color(1f, 0.3f, 0.3f, 1f)));
            yellowFireModel1 = register(createPyramidModel(0.3f, 0.45f, 0.3f, new Color(1f, 0.5f, 0f, 1f)));
            yellowFireModel2 = register(createPyramidModel(0.3f, 0.45f, 0.3f, Color.ORANGE));
            yellowFireModel3 = register(createPyramidModel(0.3f, 0.45f, 0.3f, Color.YELLOW));

            redSphereModel1 = register(createSphereModel(0.25f, new Color(0.6f, 0f, 0f, 1f)));
            redSphereModel2 = register(createSphereModel(0.25f, Color.RED));
            redSphereModel3 = register(createSphereModel(0.25f, new Color(1f, 0.3f, 0.3f, 1f)));
            yellowSphereModel1 = register(createSphereModel(0.25f, new Color(1f, 0.5f, 0f, 1f)));
            yellowSphereModel2 = register(createSphereModel(0.25f, Color.ORANGE));
            yellowSphereModel3 = register(createSphereModel(0.25f, Color.YELLOW));

            // Consumer Models
            goldConsumerModel = register(createConsumerModel(CargoTypes.GOLD.getColor()));
            coalConsumerModel = register(createConsumerModel(CargoTypes.COAL.getColor()));
            rubyConsumerModel = register(createConsumerModel(CargoTypes.RUBY.getColor()));
        }
    }

    private Model register(Model model) {
        models.add(model);
        return model;
    }

    private Model createSpeedSignalModel(boolean isMax) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Node node1 = mb.node();
        node1.id = "pole";
        MeshPartBuilder mpb = mb.part("pole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpb, 0.05f, 1.0f, 0.05f, 10);
        
        Node node2 = mb.node();
        node2.id = "plate";
        node2.translation.set(0, 0.5f, 0.025f);
        node2.rotation.set(com.badlogic.gdx.math.Vector3.X, 90f);
        com.badlogic.gdx.graphics.Color plateColor = isMax ? com.badlogic.gdx.graphics.Color.RED : com.badlogic.gdx.graphics.Color.BLUE;
        mpb = mb.part("plate", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(plateColor)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpb, 0.35f, 0.05f, 0.35f, 20);

        Node node3 = mb.node();
        node3.id = "center";
        node3.translation.set(0, 0.5f, 0.030f);
        node3.rotation.set(com.badlogic.gdx.math.Vector3.X, 90f);
        mpb = mb.part("center", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
                new com.badlogic.gdx.graphics.g3d.Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.WHITE)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpb, 0.25f, 0.05f, 0.25f, 20);

        return mb.end();
    }
    private Model createSemaphoreModel(boolean isOpen) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        // Poste
        Node node1 = mb.node();
        node1.id = "pole";
        MeshPartBuilder mpbSem = mb.part("pole", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.GRAY)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder.build(mpbSem, 0.05f, 1.0f, 0.05f, 10);
        
        // Plancha negra
        Node node2 = mb.node();
        node2.id = "plate";
        node2.translation.set(0, 0.5f, 0.025f);
        mpbSem = mb.part("plate", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(com.badlogic.gdx.graphics.Color.BLACK)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder.build(mpbSem, 0.2f, 0.4f, 0.05f);

        // Luces (esferas asomando)
        com.badlogic.gdx.graphics.Color topColor = isOpen ? com.badlogic.gdx.graphics.Color.valueOf("440000") : com.badlogic.gdx.graphics.Color.RED;
        com.badlogic.gdx.graphics.Color bottomColor = isOpen ? com.badlogic.gdx.graphics.Color.GREEN : com.badlogic.gdx.graphics.Color.valueOf("004400");
        
        Node node3 = mb.node();
        node3.id = "lightTop";
        node3.translation.set(0, 0.6f, 0.05f);
        mpbSem = mb.part("lightTop", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(topColor)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(mpbSem, 0.12f, 0.12f, 0.04f, 10, 10);
        
        Node node4 = mb.node();
        node4.id = "lightBottom";
        node4.translation.set(0, 0.4f, 0.05f);
        mpbSem = mb.part("lightBottom", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal,
                new Material(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(bottomColor)));
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(mpbSem, 0.12f, 0.12f, 0.04f, 10, 10);

        return mb.end();
    }

    private Model createConsumerModel(Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        float width = 0.9f;
        float height = 0.01f; // Flat like paint
        float thickness = 0.1f; // Width of the painted line
        
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        
        MeshPartBuilder mpb = mb.part("outline", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        Matrix4 m = new Matrix4();
        
        float offset = (width - thickness) / 2f;
        for (int i = 0; i < 4; i++) {
            float angle = i * 90f;
            float bx = (float) Math.cos(Math.toRadians(angle)) * offset;
            float bz = (float) Math.sin(Math.toRadians(angle)) * offset;
            m.setToRotation(0, 1, 0, angle).trn(bx, 0.03f, bz); // Y=0.03f to sit above the ground
            mpb.setVertexTransform(m);
            // The line is 'thickness' wide, 'height' tall, and 'width' long
            BoxShapeBuilder.build(mpb, thickness, height, width);
        }
        return mb.end();
    }

    private Model createSphereModel(float size, Color color) {
        ModelBuilder mb = new ModelBuilder();
        return mb.createSphere(size, size, size, 12, 12,
                new Material(ColorAttribute.createDiffuse(color)),
                (long) (VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal));
    }

    private Model createPyramidModel(float w, float h, float d, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder meshBuilder = mb.part("pyramid", GL20.GL_TRIANGLES,
                (long) (VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal),
                new Material(ColorAttribute.createDiffuse(color)));

        Vector3 p0 = new Vector3(-w / 2, 0, -d / 2);
        Vector3 p1 = new Vector3(w / 2, 0, -d / 2);
        Vector3 p2 = new Vector3(w / 2, 0, d / 2);
        Vector3 p3 = new Vector3(-w / 2, 0, d / 2);
        Vector3 top = new Vector3(0, h, 0);

        meshBuilder.triangle(p0, p1, top);
        meshBuilder.triangle(p1, p2, top);
        meshBuilder.triangle(p2, p3, top);
        meshBuilder.triangle(p3, p0, top);

        return mb.end();
    }

    private Model createOpenBox(float w, float h, float d, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb;
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        float thickness = 0.05f;

        // Floor
        mpb = mb.part("floor", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0, -h / 2f + thickness / 2f, 0));
        BoxShapeBuilder.build(mpb, w, thickness, d);

        // Walls
        mpb = mb.part("wall_front", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0, 0, d / 2f - thickness / 2f));
        BoxShapeBuilder.build(mpb, w, h, thickness);

        // Wall Back
        mpb = mb.part("wall_back", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0, 0, -d / 2f + thickness / 2f));
        BoxShapeBuilder.build(mpb, w, h, thickness);

        mpb = mb.part("wall_left", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(-w / 2f + thickness / 2f, 0, 0));
        BoxShapeBuilder.build(mpb, thickness, h, d);

        mpb = mb.part("wall_right", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, mat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(w / 2f - thickness / 2f, 0, 0));
        BoxShapeBuilder.build(mpb, thickness, h, d);

        return mb.end();
    }

    private Model createTunnelPortalModel() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb;
        Material stoneMat = new Material(ColorAttribute.createDiffuse(Color.GRAY));
        Material darkMat = new Material(ColorAttribute.createDiffuse(new Color(0.05f, 0.05f, 0.05f, 1f)));

        // We build the model aligned with the X axis.
        // It has depth 2.0 along X (x from -1.0 to 1.0)

        // Left Pillar (Z = -0.45)
        mpb = mb.part("pillarL", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, stoneMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 0.475f, -0.45f));
        BoxShapeBuilder.build(mpb, 2.0f, 0.95f, 0.1f);

        // Right Pillar (Z = 0.45)
        mpb = mb.part("pillarR", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, stoneMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 0.475f, 0.45f));
        BoxShapeBuilder.build(mpb, 2.0f, 0.95f, 0.1f);

        // Top Lintel (Y = 1.025)
        mpb = mb.part("lintel", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, stoneMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 1.025f, 0f));
        BoxShapeBuilder.build(mpb, 2.0f, 0.15f, 1.0f);

        // Arch corners (to make it look round)
        mpb = mb.part("cornerL", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, stoneMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 0.9f, -0.35f));
        BoxShapeBuilder.build(mpb, 2.0f, 0.1f, 0.1f);

        mpb = mb.part("cornerR", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, stoneMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 0.9f, 0.35f));
        BoxShapeBuilder.build(mpb, 2.0f, 0.1f, 0.1f);

        // Black Hole background. It blocks the view inside the mountain.
        // We put a thin black wall in the middle of the portal (at X = 0).
        mpb = mb.part("hole", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal, darkMat);
        mpb.setVertexTransform(new Matrix4().setToTranslation(0f, 0.475f, 0f));
        BoxShapeBuilder.build(mpb, 0.05f, 0.95f, 0.8f);

        return mb.end();
    }

    @Override
    public void dispose() {
        for (Model model : models) {
            model.dispose();
        }
        models.clear();
    }
}
