package letrain.visitor.gdx3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import java.util.List;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.track.CargoTypes;

public class GroundRenderer extends BaseSubRenderer {

    public GroundRenderer(Gdx3DResourceContext resourceContext, List<ModelInstance> instances,
            List<ModelInstance> transparentInstances, List<Gdx3DRenderer.VehicleLabel> labels) {
        super(resourceContext, instances, transparentInstances, labels);
    }

    private final Color tempColor = new Color();
    private final com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute rockXRayBlending =
            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f);

    @Override
    public void visitGround(Ground ground) {
        if (!isVisible(ground.getPosition())) {
            return;
        }
        int type = ground.getType();
        Model model = resourceContext.groundModel;
        float yPosition = 0.0f;
        float scaleX = 1.0f;
        float scaleY = 0.01f;
        float scaleZ = 1.0f;

        int backgroundType = type;
        if (type >= 10 && type <= 29) {
            if (modelRef != null && modelRef.getGroundMap() != null) {
                backgroundType = modelRef.getGroundMap().getBackgroundTerrain(
                        ground.getPosition().getX(), ground.getPosition().getY());
            } else {
                backgroundType = letrain.ground.GroundMap.GROUND;
            }
        }

        boolean hasTrack = false;
        if (modelRef != null && modelRef.getRailMap() != null) {
            hasTrack = modelRef.getRailMap().getTrackAt(ground.getPosition().getX(), ground.getPosition().getY()) != null;
        }

        if (type >= 10 && type <= 19) {
            CargoTypes cargo = CargoTypes.IndustryMapper.getCargoForTerrain(type);
            tempColor.set((cargo != null) ? Color.valueOf(cargo.getColor()) : Color.WHITE);
            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;

            ModelInstance jewelBlock =
                    resourceContext.getModelInstance(resourceContext.wagonJewelModel);
            jewelBlock.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                    .createDiffuse(tempColor));
            float h = hasTrack ? 0.02f : 0.5f;
            float yPos = hasTrack ? 0.01f : h / 2f;
            jewelBlock.transform.setToTranslation(x, yPos, z);
            jewelBlock.transform.scale(0.9f, h, 0.9f);
            instances.add(jewelBlock);
        } else if (type >= 20 && type <= 29) {
            CargoTypes cargo = CargoTypes.IndustryMapper.getCargoForTerrain(type);
            com.badlogic.gdx.graphics.g3d.Model consumerModelToUse =
                    resourceContext.coalConsumerModel;
            if (cargo == CargoTypes.GOLD) {
                consumerModelToUse = resourceContext.goldConsumerModel;
            } else if (cargo == CargoTypes.RUBY)
                consumerModelToUse = resourceContext.rubyConsumerModel;

            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;
            ModelInstance instance = resourceContext.getModelInstance(consumerModelToUse);

            float cargoDepositElevation = hasTrack ? 0.01f : 0.15f;
            instance.transform.setToTranslation(x, cargoDepositElevation, z);
            instances.add(instance);
        }

        switch (backgroundType) {
            case letrain.ground.GroundMap.GROUND:
                model = resourceContext.groundModel;
                yPosition = 0.0f;
                break;
            case letrain.ground.GroundMap.WATER:
                model = resourceContext.waterModel;
                yPosition = -2.0f;
                break;
            case letrain.ground.GroundMap.ROCK:
                model = resourceContext.mountainModel;
                yPosition = 0.6f;
                scaleY = 1.2f;
                break;
        }

        if (model != null) {
            float x = ground.getPosition().getX() + 0.5f;
            float z = ground.getPosition().getY() + 0.5f;
            ModelInstance instance = resourceContext.getModelInstance(model);

            if (type == GroundMap.ROCK || model == resourceContext.tunnelPortalModel) {
                if (isXRayActive) {
                    ModelInstance groundBelow =
                            resourceContext.getModelInstance(resourceContext.groundModel);
                    groundBelow.transform.setToTranslation(x, 0.0f, z);
                    groundBelow.transform.scale(scaleX, scaleY, scaleZ);
                    instances.add(groundBelow);

                    instance.materials.get(0).set(rockXRayBlending);
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

            if (backgroundType != letrain.ground.GroundMap.WATER && modelRef != null
                    && modelRef.getGroundMap() != null) {
                int gx = ground.getPosition().getX();
                int gy = ground.getPosition().getY();
                if (backgroundType == letrain.ground.GroundMap.ROCK)
                    tempColor.set(0.5f, 0.4f, 0.3f, 1f);
                else
                    tempColor.set(0.4f, 0.6f, 0.3f, 1f);

                checkAndAddWall(gx, gy - 1, x, -1.05f, z - 0.5f, 0, tempColor);
                checkAndAddWall(gx, gy + 1, x, -1.05f, z + 0.5f, 0, tempColor);
                checkAndAddWall(gx - 1, gy, x - 0.5f, -1.05f, z, 90, tempColor);
                checkAndAddWall(gx + 1, gy, x + 0.5f, -1.05f, z, 90, tempColor);
            }
        }
    }

    private void checkAndAddWall(int gx, int gy, float x, float y, float z, float rotationY,
            Color color) {
        Integer neighborType = modelRef.getGroundMap().getValueAt(gx, gy);
        if (neighborType != null && neighborType == GroundMap.WATER) {
            ModelInstance wall = resourceContext.getModelInstance(resourceContext.terrainWallModel);
            wall.materials.get(0).set(
                    com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));
            wall.transform.setToTranslation(x, y, z);
            if (rotationY != 0) {
                wall.transform.rotate(0, 1, 0, rotationY);
            }
            instances.add(wall);
        }
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {}
}
