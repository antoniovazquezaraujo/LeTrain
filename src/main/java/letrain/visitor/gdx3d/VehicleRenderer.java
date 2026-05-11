package letrain.visitor.gdx3d;

import java.util.List;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import letrain.track.CargoTypes;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.utils.PathGeometry;

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
                    for (Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == locomotive) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (Linker l : selected.getTrain().getLinkersToRemove()) {
                        if (l == locomotive) {
                            unlinkHighlight = true;
                            break;
                        }
                    }
                }
            }
        }

        float progress = 0.5f;
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
        } else {
            progress = 0.0f;
        }

        Vector3 pComputed = new Vector3();
        Vector3 renderTangent = new Vector3();
        float renderX = locomotive.getPosition().getX() + 0.5f;
        float renderY = locomotive.getPosition().getY() + 0.5f;
        float angle = locomotive.getDir().getValue() * 45f;

        // If the train is not moving, skip ALL interpolation.
        int locoSpeed = locomotive.getSpeed();
        if (locoSpeed == 0) {
            renderTangent.set(PathGeometry.getDirX(locomotive.getDir()), 0, PathGeometry.getDirZ(locomotive.getDir()));
        } else {
            // Follow the chain of same-train linkers forward until we find
            // either a free cell (can enter) or a different train (blocked).
            boolean canEnterNext = true;
            {
                letrain.track.Track lookTrack = locomotive.getTrack();
                letrain.map.Dir lookDir = locomotive.getDir();
                while (lookTrack != null) {
                    letrain.track.Track nextTrack = lookTrack.getConnected(lookDir);
                    if (nextTrack == null) break;
                    letrain.vehicle.impl.Linker occupyingL = nextTrack.getLinker();
                    if (occupyingL == null) break; // free cell
                    if (occupyingL.getTrain() != train) {
                        canEnterNext = false; // different train blocks the chain
                        break;
                    }
                    // Same train — follow the chain forward
                    lookDir = occupyingL.getDir();
                    lookTrack = nextTrack;
                }
            }

            PathGeometry.calculateTwoStagePath(locomotive.getPosition().getX(), locomotive.getPosition().getY(), 
                locomotive.getEntryDir(), locomotive.getDir(), locomotive.getTrack(), 
                progress, locoSpeed, canEnterNext, pComputed, renderTangent);

            if (pComputed.x != 0 || pComputed.z != 0) {
                renderX = pComputed.x;
                renderY = pComputed.z;
                angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
            }
        }

        Model locoModelToUse = resourceContext.locomotiveModel;
        if (unlinkHighlight) {
            locoModelToUse = resourceContext.locomotiveUnlinkModel;
        } else if (highlight) {
            locoModelToUse = resourceContext.locomotiveHighlightModel;
        }

        ModelInstance instance = resourceContext.getModelInstance(locoModelToUse);
        if (!highlight && !unlinkHighlight) {
            instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(
                    locomotive.getDiagnosticColor()));
        }
        instance.transform.setToTranslation(renderX, 0.61f, renderY);
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // Green line (direction marker) - ONLY for selected locomotive
        boolean isSelected = (modelRef != null && modelRef.getSelectedLocomotive() == locomotive);
        if (isSelected) {
            ModelInstance selectionLine = resourceContext.getModelInstance(resourceContext.selectionLineModel);
            v1.set(renderTangent).nor();
            float dxL = v1.x;
            float dzL = v1.z;
            float lineOffset = 0.25f;
            selectionLine.transform.setToTranslation(renderX + dxL * lineOffset, 1.02f, renderY + dzL * lineOffset);
            selectionLine.transform.rotate(0, 1, 0, angle);
            instances.add(selectionLine);
        }

        // Blinking red line for shunting locomotives
        if (train != null && train.isShuntingMode()) {
            // Blink: toggle every ~500ms
            boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;
            if (blinkOn) {
                ModelInstance shuntLine = resourceContext.getModelInstance(resourceContext.selectionLineModel);
                shuntLine.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(
                        com.badlogic.gdx.graphics.Color.RED));
                v1.set(renderTangent).nor();
                float dxL = v1.x;
                float dzL = v1.z;
                float lineOffset = 0.25f;
                shuntLine.transform.setToTranslation(renderX + dxL * lineOffset, 1.04f, renderY + dzL * lineOffset);
                shuntLine.transform.rotate(0, 1, 0, angle);
                instances.add(shuntLine);
            }
        }
        if (locomotive.isDestroying()) {
            drawFire(renderX, 0.61f, renderY, animationAlpha + locomotive.getId());
        }

        // Locomotive labels
        {
            v1.set(renderTangent).nor();
            float dxL = v1.x;
            float dzL = v1.z;

            v1.set(renderX, 1.02f, renderY);
            v2.set(0, 1, 0);
            v3.set(dxL, 0, dzL).nor();
            addLabel(v1, "" + locomotive.getId(), v2, v3, Color.WHITE, 1.0f);

            float perpXL = dzL * 0.42f;
            float perpZL = -dxL * 0.42f;

            v1.set(renderX + perpXL, 0.55f, renderY + perpZL);
            v2.set(perpXL, 0, perpZL).nor();
            addLabel(v1, locomotive.getAspect(), v2);

            v1.set(renderX - perpXL, 0.55f, renderY - perpZL);
            v2.set(-perpXL, 0, -perpZL).nor();
            addLabel(v1, locomotive.getAspect(), v2);
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
                    for (Linker l : selected.getTrain().getSelectedLinkersToJoin()) {
                        if (l == wagon) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    for (Linker l : selected.getTrain().getLinkersToRemove()) {
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

        ModelInstance instance = resourceContext.getModelInstance(chassisModel);
        if (!highlight && !unlinkHighlight) {
            instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(chassisColor));
        }

        float renderX = wagon.getPosition().getX() + 0.5f;
        float renderY = wagon.getPosition().getY() + 0.5f;
        float angle = wagon.getDir().getValue() * 45f;
        float speed = 0.0f;
        float progress = 0.0f;
        int totalTurns = 0;
        int currentTurns = 0;

        Train train = wagon.getTrain();
        Locomotive loc = null;
        if (train != null) {
            Tractor director = train.getDirectorLinker();
            if (director instanceof Locomotive) {
                loc = (Locomotive) director;
                speed = loc.getSpeed();
                totalTurns = loc.getTotalTurns();
                currentTurns = loc.getTurns();
                if (totalTurns > 0) {
                    float totalDelay = (float) totalTurns;
                    float currentDelay = (float) currentTurns - animationAlpha;
                    progress = 1.0f - (currentDelay / totalDelay);
                    progress = MathUtils.clamp(progress, 0, 1);
                } else {
                    progress = 0.0f;
                }
            }
        }

        Vector3 pComputed = new Vector3();
        Vector3 renderTangent = new Vector3();
        
        // If the train is stalled, stopped, or has no active turns, skip ALL interpolation.
        if (speed == 0 || totalTurns <= 0 || (train != null && train.isStalled())) {
            renderTangent.set(PathGeometry.getDirX(wagon.getDir()), 0, PathGeometry.getDirZ(wagon.getDir()));
        } else {
            // Check whether the next cell is blocked by another train.
            // Follow the chain of same-train linkers forward until we find
            // either a free cell (can enter) or a different train (blocked).
            boolean canEnterNext = true;
            {
                letrain.track.Track lookTrack = wagon.getTrack();
                letrain.map.Dir lookDir = wagon.getDir();
                while (lookTrack != null) {
                    letrain.track.Track nextTrack = lookTrack.getConnected(lookDir);
                    if (nextTrack == null) break;
                    letrain.vehicle.impl.Linker occupyingL = nextTrack.getLinker();
                    if (occupyingL == null) break; // free cell
                    if (occupyingL.getTrain() != train) {
                        canEnterNext = false; // different train blocks the chain
                        break;
                    }
                    // Same train — follow the chain forward
                    lookDir = occupyingL.getDir();
                    lookTrack = nextTrack;
                }
            }

            PathGeometry.calculateTwoStagePath(wagon.getPosition().getX(), wagon.getPosition().getY(), 
                wagon.getEntryDir(), wagon.getDir(), wagon.getTrack(), 
                progress, speed, canEnterNext, pComputed, renderTangent);
            
            if (pComputed.x != 0 || pComputed.z != 0) {
                renderX = pComputed.x;
                renderY = pComputed.z;
                angle = (float) Math.atan2(-renderTangent.z, renderTangent.x) * MathUtils.radiansToDegrees;
            } else {
                renderTangent.set(PathGeometry.getDirX(wagon.getDir()), 0, PathGeometry.getDirZ(wagon.getDir()));
            }
        }

        if (wagon.isDestroying()) {
            drawFire(renderX, 0.46f, renderY, animationAlpha + wagon.hashCode());
        }

        instance.transform.setToTranslation(renderX, 0.46f, renderY);
        instance.transform.rotate(0, 1, 0, angle);
        instances.add(instance);

        // NO green line for wagons, as per user's mandate.

        // Jewel rendering
        if (wagon.getCargoAmount() > 0 && !highlight && !unlinkHighlight) {
            Color cargoColor = (wagon.getCargoType() != null) ? wagon.getCargoType().getColor() : Color.YELLOW;
            float fullness = (float) wagon.getCargoAmount() / (float) wagon.getMaxCapacity();
            float maxHeight = 0.5f;
            float currentHeight = fullness * maxHeight;

            ModelInstance jewelBlock = resourceContext.getModelInstance(resourceContext.wagonJewelModel);
            jewelBlock.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(cargoColor));
            float jewelY = 0.26f + (currentHeight / 2f);
            jewelBlock.transform.setToTranslation(renderX, jewelY, renderY);
            jewelBlock.transform.rotate(0, 1, 0, angle);
            jewelBlock.transform.scale(0.6f, currentHeight, 0.6f);
            instances.add(jewelBlock);
        }

        // Wagon labels
        {
            v1.set(renderTangent).nor();
            float dxW = v1.x;
            float dzW = v1.z;
            float perpXW = dzW * 0.42f;
            float perpZW = -dxW * 0.42f;

            v1.set(renderX + perpXW, 0.5f, renderY + perpZW);
            v2.set(perpXW, 0, perpZW).nor();
            addLabel(v1, wagon.getAspect(), v2);

            v1.set(renderX - perpXW, 0.5f, renderY - perpZW);
            v2.set(-perpXW, 0, -perpZW).nor();
            addLabel(v1, wagon.getAspect(), v2);
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

            ModelInstance firePart = resourceContext.getModelInstance(fireModel);
            firePart.transform.setToTranslation(x + offsetX, y + offsetY, z + offsetZ);
            firePart.transform.scale(sizeScale, sizeScale, sizeScale);
            firePart.transform.rotate(Vector3.Y, realTime * 150f + seed * 100f);
            instances.add(firePart);
        }
    }
}
