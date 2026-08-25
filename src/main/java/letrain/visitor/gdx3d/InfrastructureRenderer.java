package letrain.visitor.gdx3d;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;
import letrain.utils.PathGeometry;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;

public class InfrastructureRenderer extends BaseSubRenderer {
    private final TrackRenderer trackRenderer;

    public InfrastructureRenderer(Gdx3DResourceContext resourceContext, 
                                List<ModelInstance> instances, 
                                List<ModelInstance> transparentInstances,
                                List<Gdx3DRenderer.VehicleLabel> labels,
                                TrackRenderer trackRenderer) {
        super(resourceContext, instances, transparentInstances, labels);
        this.trackRenderer = trackRenderer;
    }

    @Override
    public void updateState(letrain.mvp.Model modelRef, com.badlogic.gdx.graphics.Camera camera, float animationAlpha, boolean isXRayActive) {
        super.updateState(modelRef, camera, animationAlpha, isXRayActive);
        trackRenderer.updateState(modelRef, camera, animationAlpha, isXRayActive);
    }

    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        boolean isSelected = false;
        if (modelRef != null && modelRef.getMode() == letrain.mvp.Model.GameMode.FORKS) {
            if (modelRef.getSelectedFork() != null
                    && modelRef.getSelectedFork().getPosition().equals(track.getPosition())) {
                isSelected = true;
            }
        }

        ModelInstance base = resourceContext.getModelInstance(
                isSelected ? resourceContext.selectedForkBaseModel : resourceContext.forkBaseModel);
        base.transform.setToTranslation(track.getPosition().getX() + 0.5f, 0.03f,
                track.getPosition().getY() + 0.5f);
        instances.add(base);

        float bx = track.getPosition().getX() + 0.5f;
        float bz = track.getPosition().getY() + 0.5f;
        float boxOffset = 0.8f;

        Dir trackAxis = track.getOriginalRoute().getFirst();
        Dir sideDir = trackAxis.turnRight().turnRight();
        bx += PathGeometry.getDirX(sideDir) * boxOffset;
        bz += PathGeometry.getDirZ(sideDir) * boxOffset;

        ModelInstance box = resourceContext.getModelInstance(
                isSelected ? resourceContext.selectedForkBoxModel : resourceContext.forkBoxModel);
        box.transform.setToTranslation(bx, 0.07f, bz); 
        instances.add(box);

        Pair<Dir, Dir> route = track.isUsingAlternativeRoute()
                ? track.getAlternativeRoute()
                : track.getOriginalRoute();

        if (route != null) {
            Dir d1 = route.getFirst();
            Dir d2 = route.getSecond();
            int dist = d1.angularDistance(d2);
            int absDist = Math.abs(dist);
            boolean d1Connected = isConnected(track, d1);
            boolean d2Connected = isConnected(track, d2);
            Color blockedColor = trackRenderer.getTrackBlockedColor(track);
            if (absDist >= 1 && absDist <= 3) {
                v1.set(PathGeometry.getDirX(d1), 0, PathGeometry.getDirZ(d1));
                v2.set(PathGeometry.getDirX(d2), 0, PathGeometry.getDirZ(d2));
                v3.set(0, 0, 0);
                trackRenderer.renderMultiSegmentCurve(track.getPosition(), v1, v3, v2, d1Connected && d2Connected, 0, resourceContext.railModel, blockedColor);
            } else {
                trackRenderer.drawHalfTrack(track.getPosition(), d1, d1Connected, 1.0f, 1.0f, blockedColor);
                trackRenderer.drawHalfTrack(track.getPosition(), d2, d2Connected, 1.0f, 1.0f, blockedColor);
            }

            // Draw automatic visual semaphores for all 3 converging/diverging branches
            Dir rootDir = track.getOriginalRoute().getFirst();
            Dir branch1Dir = track.getOriginalRoute().getSecond();
            Dir branch2Dir = track.getAlternativeRoute().getSecond();
            
            letrain.vehicle.rail.impl.Train ownerTrain = getTrackOwner(track);
            
            for (Dir entryDir : new Dir[]{ rootDir, branch1Dir, branch2Dir }) {
                Dir rightSideDir = entryDir.turnLeft().turnLeft();
                // Move further out (0.65) and to the right side (0.35)
                float sx = track.getPosition().getX() + 0.5f + PathGeometry.getDirX(entryDir) * 0.65f + PathGeometry.getDirX(rightSideDir) * 0.35f;
                float sz = track.getPosition().getY() + 0.5f + PathGeometry.getDirZ(entryDir) * 0.65f + PathGeometry.getDirZ(rightSideDir) * 0.35f;
                
                boolean isGreen = true;
                if (ownerTrain != null) {
                    isGreen = false;
                    boolean onFork = false;
                    for (letrain.vehicle.rail.Linker l : ownerTrain.getLinkers()) {
                        if (l.getTrack() == track) {
                            onFork = true;
                            break;
                        }
                    }
                    if (!onFork && ownerTrain.getDirectorLinker() instanceof letrain.vehicle.rail.Linker) {
                        letrain.vehicle.rail.Linker director = (letrain.vehicle.rail.Linker) ownerTrain.getDirectorLinker();
                        if (director.getTrack() != null) {
                            letrain.map.Point trainPos = director.getTrack().getPosition();
                            float minDist = Float.MAX_VALUE;
                            Dir closestDir = null;
                            for (Dir d : new Dir[]{ rootDir, branch1Dir, branch2Dir }) {
                                 float tdx = track.getPosition().getX() + PathGeometry.getDirX(d) - trainPos.getX();
                                 float tdy = track.getPosition().getY() + PathGeometry.getDirZ(d) - trainPos.getY();
                                 float dDist = tdx * tdx + tdy * tdy;
                                 if (dDist < minDist) {
                                     minDist = dDist;
                                     closestDir = d;
                                 }
                            }
                            if (entryDir == closestDir) {
                                isGreen = true;
                            }
                        }
                    }
                }
                
                ModelInstance autoSemaphore = resourceContext.getModelInstance(
                        isGreen ? resourceContext.semaphoreOpenModel : resourceContext.semaphoreClosedModel);
                autoSemaphore.transform.setToTranslation(sx, 0.5f, sz);
                
                float sdx = PathGeometry.getDirX(entryDir);
                float sdz = PathGeometry.getDirZ(entryDir);
                float sAngle = (float) Math.atan2(sdx, sdz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
                autoSemaphore.transform.rotate(0, 1, 0, sAngle);
                instances.add(autoSemaphore);
            }
        }

        String idText = String.valueOf(track.getId());
        v1.set(bx, 0.09f, bz);
        v2.set(0, 1, 0);
        v3.set(0, 0, -1);
        addLabel(v1, idText, v2, v3, Color.BLACK, 0.4f);

        if (modelRef != null && modelRef.getGroundMap() != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(track.getPosition());
            if (terrain != null && terrain == letrain.ground.GroundMap.WATER) {
                ModelInstance pillar = resourceContext.getModelInstance(resourceContext.bridgePillarModel);
                pillar.transform.setToTranslation(track.getPosition().getX() + 0.5f, -1.05f,
                        track.getPosition().getY() + 0.5f);
                pillar.transform.scale(1f, 1.9f, 1f);
                instances.add(pillar);
            }
        }
    }

    @Override
    public void visitSensor(Sensor sensor) {
        if (sensor.getPosition() == null)
            return;

        float x = sensor.getPosition().getX();
        float y = sensor.getPosition().getY();
        Dir dir = sensor.getCreationDir();
        if (dir == null)
            dir = Dir.N;

        float dx = PathGeometry.getDirX(dir);
        float dz = PathGeometry.getDirZ(dir);
        float angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        ModelInstance instance = resourceContext.getModelInstance(resourceContext.sensorModel);
        instance.materials.get(0)
                .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                        .createDiffuse(Color.YELLOW));
        float sensorBottomHeight = 0.09f;
        instance.transform.setToTranslation(x + 0.5f, sensorBottomHeight, y + 0.5f);
        instance.transform.rotate(0, 1, 0, angle - 90f);
        instance.transform.scale(1.0f, 1.04f, 1.0f);
        instances.add(instance);

        String idText = String.valueOf(sensor.getId());
        float labelHeight = 0.19f; 
        labels.add(new Gdx3DRenderer.VehicleLabel(new Vector3(x + 0.5f, labelHeight, y + 0.5f), idText,
                new Vector3(0, 1, 0), new Vector3(0, 0, -1),
                Color.BLACK, 0.4f));
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        float x = semaphore.getPosition().getX();
        float y = semaphore.getPosition().getY();

        com.badlogic.gdx.graphics.g3d.Model modelToUse = semaphore.isOpen() ? resourceContext.semaphoreOpenModel
                : resourceContext.semaphoreClosedModel;
        ModelInstance instance = resourceContext.getModelInstance(modelToUse);

        float offsetX = 0;
        float offsetZ = 0;
        float angle = 0;

        if (modelRef != null) {
            letrain.track.Track track = modelRef.getRailMap().getTrackAt((int) x, (int) y);
            if (track != null && track instanceof letrain.track.rail.RailTrack) {
                letrain.track.rail.RailTrack railTrack = (letrain.track.rail.RailTrack) track;
                if (railTrack.getNumRoutes() > 0) {
                    Dir dir = railTrack.getFirstOpenDir();
                    float dx = PathGeometry.getDirX(dir);
                    float dz = PathGeometry.getDirZ(dir);
                    offsetX = dz * 1.0f;
                    offsetZ = -dx * 1.0f;
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

    private void drawStation(Station station, Point pos, CargoTypes cargo,
            CargoTypes.StationRole role,
            letrain.track.rail.RailTrack track, int id, String name, boolean selected, float alpha) {

        float xIndex = pos.getX();
        float zIndex = pos.getY();

        Dir rightDir = (station != null && station.getSideDir() != null)
                ? station.getSideDir()
                : getValidOrientation(track).turnRight().turnRight();
        Dir orientation = (station != null && station.getSideDir() != null)
                ? station.getSideDir().turnLeft().turnLeft()
                : getValidOrientation(track);

        float perpX = PathGeometry.getDirX(rightDir);
        float perpZ = PathGeometry.getDirZ(rightDir);
        float lenPerp = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (lenPerp > 0) {
            perpX /= lenPerp;
            perpZ /= lenPerp;
        }

        float paraX = PathGeometry.getDirX(orientation);
        float paraZ = PathGeometry.getDirZ(orientation);
        float lenPara = (float) Math.sqrt(paraX * paraX + paraZ * paraZ);
        if (lenPara > 0) {
            paraX /= lenPara;
            paraZ /= lenPara;
        }

        float distPlatform = 0.65f;
        float centerX = xIndex + 0.5f + perpX * distPlatform;
        float centerZ = zIndex + 0.5f + perpZ * distPlatform;

        float mastHeight = 1.2f;
        if (selected) {
            mastHeight = 2.0f;
        }

        Color structureColor = (cargo != null) ? cargo.getColor().cpy()
                : Color.WHITE.cpy();
        structureColor.a = alpha;

        boolean isActionActive = false;
        if (modelRef != null && station != null) {
            for (Locomotive loc : modelRef.getLocomotives()) {
                Train train = loc.getTrain();
                if (train != null && train.getLogisticsManager().isLoading()) {
                    if (train.getLogisticsManager().getStationAtTrain() == station) {
                        isActionActive = true;
                        break;
                    }
                }
            }
        }

        Color boardColor = structureColor.cpy();
        if (isActionActive) {
            if (System.currentTimeMillis() % 400 < 200) {
                boardColor = Color.WHITE.cpy();
                boardColor.a = alpha;
            }
        }

        Color mastColor = Color.GRAY.cpy();
        mastColor.a = alpha;

        float plateLengthPerp = distPlatform + 0.5f;
        float plateWidth = 1.0f;
        float plateMidX = xIndex + 0.5f + perpX * (distPlatform / 2f);
        float plateMidZ = zIndex + 0.5f + perpZ * (distPlatform / 2f);
        float plateAngle = (float) Math.atan2(perpX, perpZ) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        ModelInstance plate = resourceContext.getModelInstance(resourceContext.wagonJewelModel);
        plate.materials.get(0)
                .set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(structureColor));
        
        float cargoPlateElevation = 0.05f; // Reverted back to original
        plate.transform.setToTranslation(plateMidX, cargoPlateElevation, plateMidZ);
        plate.transform.rotate(0, 1, 0, plateAngle);
        plate.transform.scale(plateLengthPerp, 0.05f, plateWidth);
        if (alpha < 1.0f) {
            plate.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(plate);

        ModelInstance mast = resourceContext.getModelInstance(resourceContext.cylinderModel);
        mast.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(mastColor));
        mast.transform.setToTranslation(centerX, mastHeight / 2f, centerZ);
        mast.transform.rotate(0, 1, 0, plateAngle);
        mast.transform.scale(0.15f, mastHeight, 0.15f);
        if (alpha < 1.0f) {
            mast.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(mast);

        float boardSize = 0.6f;
        ModelInstance board = resourceContext.getModelInstance(resourceContext.wagonJewelModel);
        board.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(boardColor));
        board.transform.setToTranslation(centerX, mastHeight + (boardSize / 2f), centerZ);
        board.transform.rotate(0, 1, 0, plateAngle);
        board.transform.scale(boardSize, boardSize, boardSize);
        if (alpha < 1.0f) {
            board.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, alpha));
        }
        instances.add(board);

        float boardCenterY = mastHeight + (boardSize / 2f);
        Color labelColor = Color.WHITE.cpy();
        labelColor.a = alpha;

        String idText = (id >= 0) ? String.valueOf(id) : "?";
        
        float zFightingLabelOffset = 0.05f;
        float labelOffset = boardSize / 2f + zFightingLabelOffset;

        float stationLabelScale = 0.5f;
        
        labels.add(new Gdx3DRenderer.VehicleLabel(
                new Vector3(centerX + perpX * labelOffset, boardCenterY,
                        centerZ + perpZ * labelOffset),
                idText, new Vector3(perpX, 0, perpZ), null, labelColor, stationLabelScale));

        labels.add(new Gdx3DRenderer.VehicleLabel(
                new Vector3(centerX - perpX * labelOffset, boardCenterY,
                        centerZ - perpZ * labelOffset),
                idText, new Vector3(-perpX, 0, -perpZ), null, labelColor, stationLabelScale));

        labels.add(new Gdx3DRenderer.VehicleLabel(
                new Vector3(centerX + paraX * labelOffset, boardCenterY,
                        centerZ + paraZ * labelOffset),
                idText, new Vector3(paraX, 0, paraZ), null, labelColor, stationLabelScale));

        labels.add(new Gdx3DRenderer.VehicleLabel(
                new Vector3(centerX - paraX * labelOffset, boardCenterY,
                        centerZ - paraZ * labelOffset),
                idText, new Vector3(-paraX, 0, -paraZ), null, labelColor, stationLabelScale));
    }

    @Override
    public void visitTunnelGateRailTrack(letrain.track.rail.TunnelGateRailTrack tunnelGateRailTrack) {
        ModelInstance portal = resourceContext.getModelInstance(resourceContext.tunnelPortalModel);
        if (isXRayActive) {
            portal.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
        }

        letrain.map.Dir dir = tunnelGateRailTrack.getAnyDir();
        float dx = 0;
        float dz = 0;
        float angle = 0;
        if (dir != null) {
            dx = letrain.utils.PathGeometry.getDirX(dir);
            dz = letrain.utils.PathGeometry.getDirZ(dir);
            angle = (float) Math.toDegrees(Math.atan2(-dz, dx));
        }

        float tx = tunnelGateRailTrack.getPosition().getX() + 0.5f;
        float tz = tunnelGateRailTrack.getPosition().getY() + 0.5f;

        if (modelRef != null && modelRef.getGroundMap() != null && dir != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(tunnelGateRailTrack.getPosition());
            if (terrain != null && terrain != letrain.ground.GroundMap.ROCK) {
                // The exit gate is placed on GROUND. Visually shift it 1 block backwards into the mountain.
                letrain.map.Dir opposite = dir.inverse();
                
                float dx1 = dx * 2;
                float dz1 = dz * 2;
                letrain.map.Point p1 = new letrain.map.Point(
                    tunnelGateRailTrack.getPosition().getX() + (int)dx1,
                    tunnelGateRailTrack.getPosition().getY() + (int)dz1
                );
                
                float dx2 = letrain.utils.PathGeometry.getDirX(opposite) * 2;
                float dz2 = letrain.utils.PathGeometry.getDirZ(opposite) * 2;
                letrain.map.Point p2 = new letrain.map.Point(
                    tunnelGateRailTrack.getPosition().getX() + (int)dx2,
                    tunnelGateRailTrack.getPosition().getY() + (int)dz2
                );
                
                Integer t1 = modelRef.getGroundMap().getValueAt(p1);
                Integer t2 = modelRef.getGroundMap().getValueAt(p2);
                
                if (t1 != null && t1 == letrain.ground.GroundMap.ROCK) {
                    tx += dx1;
                    tz += dz1;
                } else if (t2 != null && t2 == letrain.ground.GroundMap.ROCK) {
                    tx += dx2;
                    tz += dz2;
                }
            }
        }

        portal.transform.setToTranslation(tx, 0.0f, tz);
        portal.transform.rotate(com.badlogic.gdx.math.Vector3.Y, angle);

        if (isXRayActive) {
            transparentInstances.add(portal);
        } else {
            instances.add(portal);
        }
        trackRenderer.visitRailTrack(tunnelGateRailTrack);
    }

    @Override
    public void visitCursor(Cursor cursor) {
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

        Dir dir = cursor.getDir();
        Point pos = cursor.getPosition();
        float dx = PathGeometry.getDirX(dir);
        float dz = PathGeometry.getDirZ(dir);
        float angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

        ModelInstance instance = resourceContext.getModelInstance(resourceContext.cursorModel);
        instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(color));

        float cursorY = 0.10f;
        instance.transform.setToTranslation(pos.getX() + 0.5f, cursorY, pos.getY() + 0.5f);
        instance.transform.rotate(0, 1, 0, angle - 90f);
        instance.transform.scale(1.6f, 1f, 0.6f);
        instances.add(instance);

        ModelInstance ghost = resourceContext.getModelInstance(resourceContext.cursorModel);
        ghost.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
        ghost.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute(
                com.badlogic.gdx.graphics.GL20.GL_GREATER, false));
        ghost.transform.set(instance.transform);
        instances.add(ghost);
    }

    private letrain.vehicle.rail.impl.Train getTrackOwner(letrain.track.rail.RailTrack track) {
        if (modelRef == null || track == null) return null;
        letrain.vehicle.rail.impl.Train ownerTrain = null;
        letrain.segments.RailwayGraph graph = modelRef.getRailwayGraph();
        letrain.segments.BlockManager blockManager = modelRef.getBlockManager();
        if (graph != null && blockManager != null) {
            letrain.segments.Segment segment = graph.getSegment(track);
            if (segment != null) {
                java.util.List<letrain.vehicle.rail.impl.Train> owners = blockManager.getOwners(segment);
                if (owners != null && !owners.isEmpty()) {
                    ownerTrain = owners.get(0);
                }
            }
        }
        if (ownerTrain == null && track.getLinker() != null) {
            ownerTrain = track.getLinker().getTrain();
        }
        return ownerTrain;
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {
        float x = speedSignal.getPosition().getX();
        float y = speedSignal.getPosition().getY();

        com.badlogic.gdx.graphics.g3d.Model modelToUse = speedSignal.isMax() ? resourceContext.speedSignalMaxModel
                : resourceContext.speedSignalMinModel;
        ModelInstance instance = resourceContext.getModelInstance(modelToUse);

        float offsetX = 0;
        float offsetZ = 0;
        float angle = 0;

        Dir creationDir = speedSignal.getCreationDir();
        if (creationDir != null) {
            float dx = PathGeometry.getDirX(creationDir);
            float dz = PathGeometry.getDirZ(creationDir);
            // Position the signal to the side of the track (like semaphore)
            offsetX = dz * 1.0f;
            offsetZ = -dx * 1.0f;
            angle = (float) Math.atan2(dx, dz) * com.badlogic.gdx.math.MathUtils.radiansToDegrees + 180f;
        }

        instance.transform.setToTranslation(x + 0.5f + offsetX, 0.5f, y + 0.5f + offsetZ);
        instance.transform.rotate(0, 1, 0, angle);


        float scale = (modelRef != null && modelRef.getSelectedSpeedSignal() == speedSignal) ? 1.5f : 1.0f;
        if (scale > 1.0f) {
            instance.transform.scale(scale, scale, scale);
        }

        instances.add(instance);

        // Add label for the speed limit
        int limit = speedSignal.getLimit();
        String limitText = limit >= 1 && limit <= 10 ? String.valueOf(limit) : "?";
        
        float rad = (float) Math.toRadians(angle);
        float localZ = 0.05f * scale + 0.05f; // Just outside the scaled plate
        float labelOffsetX = (float) Math.sin(rad) * localZ;
        float labelOffsetZ = (float) Math.cos(rad) * localZ;
        
        float worldY = 0.5f + 0.5f * scale; 
        
        Vector3 labelPos = new Vector3(x + 0.5f + offsetX + labelOffsetX, worldY, y + 0.5f + offsetZ + labelOffsetZ);
        Vector3 labelNormal = new Vector3((float) Math.sin(rad), 0, (float) Math.cos(rad));
        
        Color textColor = com.badlogic.gdx.graphics.Color.BLACK;
        labels.add(new Gdx3DRenderer.VehicleLabel(labelPos, limitText, labelNormal, new Vector3(0, 1, 0), textColor, 0.30f * scale));
    }
}
