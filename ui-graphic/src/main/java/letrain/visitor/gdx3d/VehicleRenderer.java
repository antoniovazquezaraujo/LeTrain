package letrain.visitor.gdx3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.List;
import letrain.track.CargoTypes;
import letrain.utils.PathGeometry;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;

public class VehicleRenderer extends BaseSubRenderer {

    public VehicleRenderer(Gdx3DResourceContext resourceContext, List<ModelInstance> instances,
            List<ModelInstance> transparentInstances, List<Gdx3DRenderer.VehicleLabel> labels) {
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
                    // Convert deque to list to slice it
                    // Logic might differ based on iteration order of deque vs join sense
                    // linkersToJoin is populated in order of distance from train.
                    // so we just take the first N.
                    Train train = selected.getTrain();
                    for (Linker l : train.getTrainCouplingManager()
                            .getSelectedLinkersToJoin(train)) {
                        if (l == locomotive) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    Train train = selected.getTrain();
                    for (Linker l : train.getLinkersToRemove()) {
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
            renderTangent.set(PathGeometry.getDirX(locomotive.getDir()), 0,
                    PathGeometry.getDirZ(locomotive.getDir()));
        } else {
            // Follow the chain of same-train linkers forward until we find
            // either a free cell (can enter) or a different train (blocked).
            boolean canEnterNext = true;
            {
                letrain.track.Track lookTrack = locomotive.getTrack();
                letrain.map.Dir lookDir = locomotive.getDir();
                int chainDepth = 0;
                while (lookTrack != null) {
                    if (chainDepth >= 100) {
                        throw new IllegalStateException(
                                "CRITICAL ERROR: Infinite loop detected in rendering chain for locomotive train "
                                        + train.getId());
                    }
                    chainDepth++;
                    letrain.track.Track nextTrack = lookTrack.getConnected(lookDir);
                    if (nextTrack == null) {
                        break;
                    }
                    letrain.vehicle.rail.Linker occupyingL = nextTrack.getLinker();
                    if (occupyingL == null)
                        break; // free cell
                    if (occupyingL == locomotive)
                        break; // cycle detected (circular train)
                    if (occupyingL.getTrain() != train) {
                        canEnterNext = false; // different train blocks the chain
                        break;
                    }
                    // Same train — follow the chain forward
                    letrain.map.Dir entry = lookDir.inverse();
                    if (occupyingL.getEntryDir() == entry) {
                        lookDir = occupyingL.getDir();
                    } else if (occupyingL.getDir() == entry) {
                        lookDir = occupyingL.getEntryDir();
                    } else {
                        canEnterNext = false; // blocks the route
                        break;
                    }
                    lookTrack = nextTrack;
                }
            }

            PathGeometry.calculateTwoStagePath(locomotive.getPosition().getX(),
                    locomotive.getPosition().getY(), locomotive.getEntryDir(), locomotive.getDir(),
                    locomotive.getTrack(), progress, locoSpeed, canEnterNext, pComputed,
                    renderTangent);

            if (pComputed.x != 0 || pComputed.z != 0) {
                renderX = pComputed.x;
                renderY = pComputed.z;
                angle = (float) Math.atan2(-renderTangent.z, renderTangent.x)
                        * MathUtils.radiansToDegrees;
            }
        }

        Model locoModelToUse = resourceContext.locomotiveModel;

        ModelInstance instance = resourceContext.getModelInstance(locoModelToUse);
        if (locomotive.getColor() != null && !instance.materials.isEmpty()) {
            Color locoColor = getLibGdxColor(locomotive.getColor());
            if (locoColor != null) {
                instance.materials.get(0)
                        .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                .createDiffuse(locoColor));
            }
        }
        if (locomotive.isDestroying()) {
            // Derailed: random offset + tilt
            int seed = locomotive.hashCode();
            float ox = ((seed & 0xFF) / 255f) * 0.6f - 0.3f;
            float oz = (((seed >> 8) & 0xFF) / 255f) * 0.6f - 0.3f;
            instance.transform.setToTranslation(renderX + ox, 0.61f, renderY + oz);
            instance.transform.rotate(0, 1, 0, angle);
            instance.transform.rotate(1, 0, 0, (seed % 60) - 30);
            instance.transform.rotate(0, 0, 1, ((seed >> 16) % 60) - 30);
        } else {
            instance.transform.setToTranslation(renderX, 0.61f, renderY);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (unlinkHighlight || highlight) {
            Model overlayModel = unlinkHighlight ? resourceContext.locomotiveUnlinkModel
                    : resourceContext.locomotiveHighlightModel;
            ModelInstance overlay = resourceContext.getModelInstance(overlayModel);
            overlay.transform.set(instance.transform);
            transparentInstances.add(overlay);
        }

        // Auto mode indicator (blinking red dot on top face, top-left)
        boolean isAuto = (locomotive.getTrain() != null && locomotive.getTrain().isAutoMode());
        if (isAuto && !locomotive.isDestroying()) {
            if (System.currentTimeMillis() % 600 < 300) {
                ModelInstance dot =
                        resourceContext.getModelInstance(resourceContext.autoModeDotModel);
                dot.transform.set(instance.transform);
                dot.transform.translate(-0.25f, 0.41f, -0.25f);
                instances.add(dot);
            }
        }

        // Green line (direction marker) - ONLY for selected locomotive
        boolean isSelected = (modelRef != null && modelRef.getSelectedLocomotive() == locomotive);
        if (isSelected) {
            ModelInstance selectionLine =
                    resourceContext.getModelInstance(resourceContext.selectionLineModel);
            v1.set(renderTangent).nor();
            float dxL = v1.x;
            float dzL = v1.z;
            float lineOffset = 0.25f;
            selectionLine.transform.setToTranslation(renderX + dxL * lineOffset, 1.05f,
                    renderY + dzL * lineOffset);
            selectionLine.transform.rotate(0, 1, 0, angle);

            if (locomotive.getColor() != null && (locomotive.getColor().equalsIgnoreCase("GREEN")
                    || locomotive.getColor().equalsIgnoreCase("GREEN_BRIGHT"))) {
                selectionLine.materials.get(0)
                        .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                .createDiffuse(Color.BLUE));
            } else {
                selectionLine.materials.get(0)
                        .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                                .createDiffuse(Color.GREEN));
            }

            instances.add(selectionLine);
        }

        if (locomotive.isDestroying()) {
            drawFire(renderX, 0.61f, renderY, animationAlpha + locomotive.getId());
        }

        // Locomotive labels
        {
            v1.set(renderTangent).nor();
            float dxL = v1.x;
            float dzL = v1.z;

            v1.set(renderX, 1.08f, renderY);
            v2.set(0, 1, 0);
            v3.set(dxL, 0, dzL).nor();

            Color labelColor = Color.WHITE;
            if (locomotive.getColor() != null && (locomotive.getColor().equalsIgnoreCase("WHITE")
                    || locomotive.getColor().equalsIgnoreCase("YELLOW")
                    || locomotive.getColor().equalsIgnoreCase("YELLOW_BRIGHT"))) {
                labelColor = Color.BLACK;
            }

            addLabel(v1, "" + locomotive.getId(), v2, v3, labelColor, 0.5f);

            float perpXL = dzL * 0.46f;
            float perpZL = -dxL * 0.46f;

            v1.set(renderX + perpXL, 0.55f, renderY + perpZL);
            v2.set(perpXL, 0, perpZL).nor();
            addLabel(v1, locomotive.getAspect(), v2, null, labelColor, 1.0f);

            v1.set(renderX - perpXL, 0.55f, renderY - perpZL);
            v2.set(-perpXL, 0, -perpZL).nor();
            addLabel(v1, locomotive.getAspect(), v2, null, labelColor, 1.0f);
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
                    // Convert deque to list to slice it
                    // Logic might differ based on iteration order of deque vs join sense
                    // linkersToJoin is populated in order of distance from train.
                    // so we just take the first N.
                    Train train = selected.getTrain();
                    for (Linker l : train.getTrainCouplingManager()
                            .getSelectedLinkersToJoin(train)) {
                        if (l == wagon) {
                            highlight = true;
                            break;
                        }
                    }
                }
            } else if (modelRef.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
                Locomotive selected = modelRef.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    Train train = selected.getTrain();
                    for (Linker l : train.getLinkersToRemove()) {
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
            chassisColor = com.badlogic.gdx.graphics.Color.valueOf(wagon.getExclusiveCargoType().getColor());
        }

        ModelInstance instance = resourceContext.getModelInstance(chassisModel);
        instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                .createDiffuse(chassisColor));

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
            renderTangent.set(PathGeometry.getDirX(wagon.getDir()), 0,
                    PathGeometry.getDirZ(wagon.getDir()));
        } else {
            // Check whether the next cell is blocked by another train.
            // Follow the chain of same-train linkers forward until we find
            // either a free cell (can enter) or a different train (blocked).
            boolean canEnterNext = true;
            {
                letrain.track.Track lookTrack = wagon.getTrack();
                letrain.map.Dir lookDir = wagon.getDir();
                int chainDepth = 0;
                while (lookTrack != null) {
                    if (chainDepth >= 90) {
                        System.out.println("DEBUG LOOP: chainDepth=" + chainDepth + ", lookTrack="
                                + lookTrack.getPosition() + ", lookDir=" + lookDir + ", entry="
                                + lookDir.inverse());
                    }
                    if (chainDepth >= 100) {
                        throw new IllegalStateException(
                                "CRITICAL ERROR: Infinite loop detected in rendering chain for wagon train "
                                        + train.getId());
                    }
                    chainDepth++;
                    letrain.track.Track nextTrackLocal = lookTrack.getConnected(lookDir);
                    if (nextTrackLocal == null) {
                        break;
                    }
                    letrain.vehicle.rail.Linker occupyingLLocal = nextTrackLocal.getLinker();
                    if (occupyingLLocal == null)
                        break; // free cell
                    if (occupyingLLocal == wagon)
                        break; // cycle detected (circular train)
                    if (occupyingLLocal.getTrain() != train) {
                        canEnterNext = false; // different train blocks the chain
                        break;
                    }
                    // Same train — follow the chain forward
                    letrain.map.Dir entry = lookDir.inverse();
                    if (occupyingLLocal.getEntryDir() == entry) {
                        lookDir = occupyingLLocal.getDir();
                    } else if (occupyingLLocal.getDir() == entry) {
                        lookDir = occupyingLLocal.getEntryDir();
                    } else {
                        canEnterNext = false; // blocks the route
                        break;
                    }
                    lookTrack = nextTrackLocal;
                }
            }

            PathGeometry.calculateTwoStagePath(wagon.getPosition().getX(),
                    wagon.getPosition().getY(), wagon.getEntryDir(), wagon.getDir(),
                    wagon.getTrack(), progress, speed, canEnterNext, pComputed, renderTangent);

            if (pComputed.x != 0 || pComputed.z != 0) {
                renderX = pComputed.x;
                renderY = pComputed.z;
                angle = (float) Math.atan2(-renderTangent.z, renderTangent.x)
                        * MathUtils.radiansToDegrees;
            } else {
                renderTangent.set(PathGeometry.getDirX(wagon.getDir()), 0,
                        PathGeometry.getDirZ(wagon.getDir()));
            }
        }

        if (wagon.isDestroying()) {
            drawFire(renderX, 0.46f, renderY, animationAlpha + wagon.hashCode());
            // Derailed: random offset + tilt
            int seed = wagon.hashCode();
            float ox = ((seed & 0xFF) / 255f) * 0.6f - 0.3f;
            float oz = (((seed >> 8) & 0xFF) / 255f) * 0.6f - 0.3f;
            instance.transform.setToTranslation(renderX + ox, 0.46f, renderY + oz);
            instance.transform.rotate(0, 1, 0, angle);
            instance.transform.rotate(1, 0, 0, (seed % 60) - 30);
            instance.transform.rotate(0, 0, 1, ((seed >> 16) % 60) - 30);
        } else {
            instance.transform.setToTranslation(renderX, 0.46f, renderY);
            instance.transform.rotate(0, 1, 0, angle);
        }
        instances.add(instance);

        if (unlinkHighlight || highlight) {
            Model overlayModel = unlinkHighlight ? resourceContext.wagonUnlinkModel
                    : resourceContext.wagonHighlightModel;
            ModelInstance overlay = resourceContext.getModelInstance(overlayModel);
            overlay.transform.set(instance.transform);
            transparentInstances.add(overlay);
        }

        // NO green line for wagons, as per user's mandate.

        // Jewel rendering
        if (wagon.getCargoAmount() > 0) {
            Color cargoColor =
                    (wagon.getCargoType() != null) ? com.badlogic.gdx.graphics.Color.valueOf(wagon.getCargoType().getColor()) : Color.YELLOW;
            float fullness = (float) wagon.getCargoAmount() / (float) wagon.getMaxCapacity();
            float maxHeight = 0.5f;
            float currentHeight = fullness * maxHeight;

            ModelInstance jewelBlock =
                    resourceContext.getModelInstance(resourceContext.wagonJewelModel);
            jewelBlock.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                    .createDiffuse(cargoColor));
            float jewelY = 0.26f + (currentHeight / 2f);
            jewelBlock.transform.setToTranslation(renderX, jewelY, renderY);
            jewelBlock.transform.rotate(0, 1, 0, angle);
            jewelBlock.transform.scale(0.55f, currentHeight, 0.55f);
            instances.add(jewelBlock);
        }

        // Wagon labels
        {
            v1.set(renderTangent).nor();
            float dxW = v1.x;
            float dzW = v1.z;
            float perpXW = dzW * 0.46f;
            float perpZW = -dxW * 0.46f;

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

            if (colorPick == 0)
                fireModel =
                        isSphere ? resourceContext.redSphereModel1 : resourceContext.redFireModel1;
            else if (colorPick == 1)
                fireModel =
                        isSphere ? resourceContext.redSphereModel2 : resourceContext.redFireModel2;
            else if (colorPick == 2)
                fireModel =
                        isSphere ? resourceContext.redSphereModel3 : resourceContext.redFireModel3;
            else if (colorPick == 3)
                fireModel = isSphere ? resourceContext.yellowSphereModel1
                        : resourceContext.yellowFireModel1;
            else if (colorPick == 4)
                fireModel = isSphere ? resourceContext.yellowSphereModel2
                        : resourceContext.yellowFireModel2;
            else
                fireModel = isSphere ? resourceContext.yellowSphereModel3
                        : resourceContext.yellowFireModel3;

            if (fireModel == null) {
                continue;
            }

            float sizeScale = 1.0f - offsetY / 1.5f;
            if (sizeScale <= 0) {
                continue;
            }

            ModelInstance firePart = resourceContext.getModelInstance(fireModel);
            firePart.transform.setToTranslation(x + offsetX, y + offsetY, z + offsetZ);
            firePart.transform.scale(sizeScale, sizeScale, sizeScale);
            firePart.transform.rotate(Vector3.Y, realTime * 150f + seed * 100f);
            instances.add(firePart);
        }
    }

    public static Color getLibGdxColor(String colorName) {
        if (colorName == null) {
            return null;
        }
        return switch (colorName.toUpperCase()) {
            case "RED", "RED_BRIGHT" -> Color.RED;
            case "GREEN", "GREEN_BRIGHT" -> Color.GREEN;
            case "YELLOW", "YELLOW_BRIGHT" -> Color.YELLOW;
            case "BLUE", "BLUE_BRIGHT" -> Color.BLUE;
            case "MAGENTA", "MAGENTA_BRIGHT" -> Color.MAGENTA;
            case "CYAN", "CYAN_BRIGHT" -> Color.CYAN;
            case "ORANGE" -> Color.ORANGE;
            case "PINK" -> Color.PINK;
            case "BLACK" -> Color.BLACK;
            case "GRAY" -> Color.GRAY;
            case "WHITE" -> Color.WHITE;
            default -> Color.WHITE;
        };
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {}
}
