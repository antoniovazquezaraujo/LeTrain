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
            if (absDist >= 1 && absDist <= 3) {
                v1.set(PathGeometry.getDirX(d1), 0, PathGeometry.getDirZ(d1));
                v2.set(PathGeometry.getDirX(d2), 0, PathGeometry.getDirZ(d2));
                v3.set(0, 0, 0);
                trackRenderer.renderMultiSegmentCurve(track.getPosition(), v1, v3, v2, d1Connected && d2Connected, 0, resourceContext.railModel);
            } else {
                trackRenderer.drawHalfTrack(track.getPosition(), d1, d1Connected, 1.0f, 1.0f);
                trackRenderer.drawHalfTrack(track.getPosition(), d2, d2Connected, 1.0f, 1.0f);
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
        if (track == null) return;

        Point pos = station.getPosition();
        float x = pos.getX() + 0.5f;
        float z = pos.getY() + 0.5f;

        Color cargoColor = (station.getCargoType() != null) ? station.getCargoType().getColor().cpy() : Color.WHITE.cpy();
        boolean selected = (modelRef != null && modelRef.getSelectedStation() == station);

        // Low colored box at the station position (like a short wagon)
        ModelInstance box = resourceContext.getModelInstance(resourceContext.wagonModel);
        box.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(cargoColor));
        box.transform.setToTranslation(x, 0.15f, z);
        box.transform.scale(0.8f, 0.3f, 0.8f);
        instances.add(box);

        // Station ID label on top
        String idText = (station.getId() >= 0) ? String.valueOf(station.getId()) : "?";
        labels.add(new Gdx3DRenderer.VehicleLabel(
                new Vector3(x, 0.35f, z), idText,
                new Vector3(0, 1, 0), Color.WHITE));

        // Selection highlight
        if (selected) {
            ModelInstance hl = resourceContext.getModelInstance(resourceContext.selectionLineModel);
            hl.transform.setToTranslation(x, 0.35f, z);
            hl.transform.scale(1.2f, 0.05f, 1.2f);
            instances.add(hl);
        }
    }

    @Override
    public void visitTunnelGateRailTrack(letrain.track.rail.TunnelGateRailTrack tunnelGateRailTrack) {
        ModelInstance portal = resourceContext.getModelInstance(resourceContext.tunnelPortalModel);
        if (isXRayActive) {
            portal.materials.get(0).set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(true, 0.4f));
            portal.transform.setToTranslation(
                    tunnelGateRailTrack.getPosition().getX() + 0.5f,
                    0.6f,
                    tunnelGateRailTrack.getPosition().getY() + 0.5f);
            transparentInstances.add(portal);
        } else {
            portal.transform.setToTranslation(
                    tunnelGateRailTrack.getPosition().getX() + 0.5f,
                    0.6f,
                    tunnelGateRailTrack.getPosition().getY() + 0.5f);
            instances.add(portal);
        }
        trackRenderer.visitRailTrack(tunnelGateRailTrack);
    }

    @Override
    public void visitCursor(letrain.vehicle.impl.Cursor cursor) {
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
}
