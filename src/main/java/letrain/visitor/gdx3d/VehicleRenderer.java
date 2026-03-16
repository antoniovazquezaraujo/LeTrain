package letrain.visitor.gdx3d;

import java.util.List;
import letrain.visitor.Visitor;
import java.util.Random;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import letrain.track.CargoTypes;
import letrain.utils.PathGeometry;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.Tractor;

public class VehicleRenderer extends BaseSubRenderer {

    public VehicleRenderer(Gdx3DResourceContext resourceContext, 
                         List<ModelInstance> instances, 
                         List<ModelInstance> transparentInstances,
                         List<Gdx3DRenderer.VehicleLabel> labels) {
        super(resourceContext, instances, transparentInstances, labels);
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        boolean highlight = false;
        boolean unlinkHighlight = false;

        if (modelRef != null) {
            if (modelRef.getMode() == letrain.mvp.Model.GameMode.LINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == locomotive) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
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
        Model modelToUse = resourceContext.locomotiveModel;

        if (unlinkHighlight) {
            modelToUse = resourceContext.locomotiveUnlinkModel;
        } else if (highlight) {
            modelToUse = resourceContext.locomotiveHighlightModel;
        }

        ModelInstance instance = new ModelInstance(modelToUse);

        float renderX = locomotive.getPosition().getX() + 0.5f;
        float renderY = locomotive.getPosition().getY() + 0.5f;
        float angle = locomotive.getDir().getValue() * 45f;
        float progress = 0.5f;
        Vector3 renderTangent = new Vector3(PathGeometry.getDirX(locomotive.getDir()), 0, PathGeometry.getDirZ(locomotive.getDir()));

        Locomotive interpolationRef = locomotive;
        Train train = locomotive.getTrain();
        if (train != null) {
            Tractor director = train.getDirectorLinker();
            if (director instanceof Locomotive) {
                interpolationRef = (Locomotive) director;
            }
        }

        if (interpolationRef.getTotalTurns() > 0) {
            float totalDelay = (float) interpolationRef.getTotalTurns();
            float currentDelay = (float) interpolationRef.getTurns() - animationAlpha;
            progress = 1.0f - (currentDelay / totalDelay);
            progress = MathUtils.clamp(progress, 0, 1);

            Vector3 pComputed = new Vector3();
            PathGeometry.calculateTwoStagePath(locomotive.getPosition().getX(), locomotive.getPosition().getY(), 
                locomotive.getEntryDir(), locomotive.getDir(), locomotive.getTrack(), 
                progress, locomotive.getSpeed(), pComputed, renderTangent);
            
            renderX = pComputed.x;
            renderY = pComputed.z;
            angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
        } else {
            Vector3 pComputed = new Vector3();
            PathGeometry.calculateTwoStagePath(locomotive.getPosition().getX(), locomotive.getPosition().getY(), 
                locomotive.getEntryDir(), locomotive.getDir(), locomotive.getTrack(), 
                0.0f, locomotive.getSpeed(), pComputed, renderTangent);
            
            renderX = pComputed.x;
            renderY = pComputed.z;
            angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
        }

        if (locomotive.isDestroying()) {
            Random rnd = new Random(locomotive.getId());
            float offsetX = (rnd.nextFloat() - 0.5f) * 0.4f;
            float offsetZ = (rnd.nextFloat() - 0.5f) * 0.4f;
            float rotX = (rnd.nextFloat() - 0.5f) * 45f;
            float rotY = (rnd.nextFloat() - 0.5f) * 45f;
            float rotZ = (rnd.nextFloat() - 0.5f) * 45f;

            instance.transform.setToTranslation(renderX + offsetX, 0.6f, renderY + offsetZ);
            instance.transform.rotate(1, 0, 0, rotX);
            instance.transform.rotate(0, 1, 0, angle + rotY);
            instance.transform.rotate(0, 0, 1, rotZ);
        } else {
            instance.transform.setToTranslation(renderX, 0.6f, renderY);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (locomotive.isDestroying()) {
            drawFire(renderX, 0.6f, renderY, animationAlpha + locomotive.getId());
        }

        if (isSelected) {
            Vector3 forward = renderTangent.cpy().nor();
            float dxL = forward.x;
            float dzL = forward.z;

            labels.add(new Gdx3DRenderer.VehicleLabel(
                    new Vector3(renderX, 1.01f, renderY),
                    "" + locomotive.getId(),
                    new Vector3(0, 1, 0), 
                    new Vector3(dxL, 0, dzL).nor(), 
                    Color.WHITE));

            ModelInstance selectionLine = new ModelInstance(resourceContext.selectionLineModel);
            float lineOffset = 0.25f;
            selectionLine.transform.setToTranslation(renderX + dxL * lineOffset, 1.01f, renderY + dzL * lineOffset);
            selectionLine.transform.rotate(0, 1, 0, angle);
            instances.add(selectionLine);
        }

        {
            Vector3 forward = renderTangent.cpy().nor();
            float dxL = forward.x;
            float dzL = forward.z;
            float perpXL = dzL * 0.48f;
            float perpZL = -dxL * 0.48f;

            labels.add(new Gdx3DRenderer.VehicleLabel(
                    new Vector3(renderX + perpXL, 0.4f, renderY + perpZL),
                    locomotive.getAspect(),
                    new Vector3(perpXL, 0, perpZL).nor()));
            labels.add(new Gdx3DRenderer.VehicleLabel(
                    new Vector3(renderX - perpXL, 0.4f, renderY - perpZL),
                    locomotive.getAspect(),
                    new Vector3(-perpXL, 0, -perpZL).nor()));
        }
    }

    @Override
    public void visitWagon(Wagon wagon) {
        boolean highlight = false;
        boolean unlinkHighlight = false;

        if (modelRef != null) {
            if (modelRef.getMode() == letrain.mvp.Model.GameMode.LINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (letrain.vehicle.impl.Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == wagon) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
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

        Model chassisModel = resourceContext.wagonModel;
        Color chassisColor = Color.BLUE;
        
        if (wagon.getExclusiveCargoType() != CargoTypes.NONE) {
            chassisColor = wagon.getExclusiveCargoType().getColor();
        }

        if (unlinkHighlight) {
            chassisModel = resourceContext.wagonUnlinkModel;
        } else if (highlight) {
            chassisModel = resourceContext.wagonHighlightModel;
        }

        ModelInstance instance = new ModelInstance(chassisModel);
        if (!highlight && !unlinkHighlight) {
            instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(chassisColor));
        }

        float renderX = wagon.getPosition().getX() + 0.5f;
        float renderY = wagon.getPosition().getY() + 0.5f;
        float angle = wagon.getDir().getValue() * 45f;
        float progress = 0.5f;
        Vector3 renderTangent = new Vector3(PathGeometry.getDirX(wagon.getDir()), 0, PathGeometry.getDirZ(wagon.getDir()));

        Train train = wagon.getTrain();
        if (train != null) {
            Tractor director = train.getDirectorLinker();
            if (director instanceof Locomotive) {
                Locomotive loc = (Locomotive) director;
                if (loc.getTotalTurns() > 0) {
                    float totalDelay = (float) loc.getTotalTurns();
                    float currentDelay = (float) loc.getTurns() - animationAlpha;
                    progress = 1.0f - (currentDelay / totalDelay);
                    progress = MathUtils.clamp(progress, 0, 1);

                    Vector3 pComputed = new Vector3();
                    PathGeometry.calculateTwoStagePath(wagon.getPosition().getX(), wagon.getPosition().getY(), 
                        wagon.getEntryDir(), wagon.getDir(), wagon.getTrack(), 
                        progress, loc.getSpeed(), pComputed, renderTangent);
                    
                    renderX = pComputed.x;
                    renderY = pComputed.z;
                    angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
                } else {
                    Vector3 pComputed = new Vector3();
                    PathGeometry.calculateTwoStagePath(wagon.getPosition().getX(), wagon.getPosition().getY(), 
                        wagon.getEntryDir(), wagon.getDir(), wagon.getTrack(), 
                        0.0f, loc.getSpeed(), pComputed, renderTangent);
                    
                    renderX = pComputed.x;
                    renderY = pComputed.z;
                    angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
                }
            }
        }

        float wagonY = 0.45f;
        if (wagon.isDestroying()) {
            Random rnd = new Random(wagon.hashCode());
            float offsetX = (rnd.nextFloat() - 0.5f) * 0.4f;
            float offsetZ = (rnd.nextFloat() - 0.5f) * 0.4f;
            float rotX = (rnd.nextFloat() - 0.5f) * 45f;
            float rotY = (rnd.nextFloat() - 0.5f) * 45f;
            float rotZ = (rnd.nextFloat() - 0.5f) * 45f;

            instance.transform.setToTranslation(renderX + offsetX, wagonY, renderY + offsetZ);
            instance.transform.rotate(1, 0, 0, rotX);
            instance.transform.rotate(0, 1, 0, angle + rotY);
            instance.transform.rotate(0, 0, 1, rotZ);
        } else {
            instance.transform.setToTranslation(renderX, wagonY, renderY);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (wagon.isDestroying()) {
            drawFire(renderX, 0.5f, renderY, animationAlpha + wagon.hashCode());
        }

        if (wagon.getCargoAmount() > 0 && !highlight && !unlinkHighlight) {
            Color cargoColor = (wagon.getCargoType() != null) ? wagon.getCargoType().getColor().cpy() : Color.YELLOW.cpy();
            float fullness = (float) wagon.getCargoAmount() / (float) wagon.getMaxCapacity();
            float maxHeight = 0.5f;
            float currentHeight = fullness * maxHeight;

            ModelInstance jewelBlock = new ModelInstance(resourceContext.wagonJewelModel);
            jewelBlock.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(cargoColor));
            float jewelY = 0.25f + (currentHeight / 2f);
            jewelBlock.transform.setToTranslation(renderX, jewelY, renderY);
            jewelBlock.transform.rotate(0, 1, 0, angle);
            jewelBlock.transform.scale(0.6f, currentHeight, 0.6f);
            instances.add(jewelBlock);
        }

        {
            Vector3 forward = renderTangent.cpy().nor();
            float dxW = forward.x;
            float dzW = forward.z;
            float perpXW = dzW * 0.48f;
            float perpZW = -dxW * 0.48f;

            labels.add(new Gdx3DRenderer.VehicleLabel(
                    new Vector3(renderX + perpXW, 0.4f, renderY + perpZW),
                    wagon.getAspect(),
                    new Vector3(perpXW, 0, perpZW).nor()));
            labels.add(new Gdx3DRenderer.VehicleLabel(
                    new Vector3(renderX - perpXW, 0.4f, renderY - perpZW),
                    wagon.getAspect(),
                    new Vector3(-perpXW, 0, -perpZW).nor()));
        }
    }

    private void drawFire(float x, float y, float z, float stateTime) {
        float realTime = (float) (com.badlogic.gdx.Gdx.graphics.getFrameId()) * 0.025f;
        float timeScale = 2.5f;
        int numParticles = 12;
        for (int i = 0; i < numParticles; i++) {
            float seed = i * 123.456f;
            float offsetX = (float) Math.sin(seed * 0.7f + realTime * timeScale) * 0.4f;
            float offsetZ = (float) Math.cos(seed * 0.8f + realTime * timeScale * 1.1f) * 0.4f;
            float offsetY = (float) ((realTime * 1.5f + seed) % 1.5f);

            Model fireModel;
            int colorPick = (int) (seed * 10f + realTime * 5f) % 6;
            boolean isSphere = (i % 2 == 0);

            if (colorPick == 0) fireModel = isSphere ? resourceContext.redSphereModel1 : resourceContext.redFireModel1;
            else if (colorPick == 1) fireModel = isSphere ? resourceContext.redSphereModel2 : resourceContext.redFireModel2;
            else if (colorPick == 2) fireModel = isSphere ? resourceContext.redSphereModel3 : resourceContext.redFireModel3;
            else if (colorPick == 3) fireModel = isSphere ? resourceContext.yellowSphereModel1 : resourceContext.yellowFireModel1;
            else if (colorPick == 4) fireModel = isSphere ? resourceContext.yellowSphereModel2 : resourceContext.yellowFireModel2;
            else fireModel = isSphere ? resourceContext.yellowSphereModel3 : resourceContext.yellowFireModel3;

            if (fireModel == null) continue;

            float sizeScale = 1.0f - offsetY / 1.5f;
            if (sizeScale <= 0) continue;

            ModelInstance firePart = new ModelInstance(fireModel);
            firePart.transform.setToTranslation(x + offsetX, y + offsetY, z + offsetZ);
            firePart.transform.scale(sizeScale, sizeScale, sizeScale);
            firePart.transform.rotate(Vector3.Y, realTime * 150f + seed * 100f);
            instances.add(firePart);
        }
    }
}
