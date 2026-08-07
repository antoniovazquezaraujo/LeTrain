package letrain.visitor.gdx3d;

import java.util.List;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.utils.PathGeometry;
import letrain.vehicle.rail.impl.Locomotive;

public class TrackRenderer extends BaseSubRenderer {

    public TrackRenderer(Gdx3DResourceContext resourceContext, 
                       List<ModelInstance> instances, 
                       List<ModelInstance> transparentInstances,
                       List<Gdx3DRenderer.VehicleLabel> labels) {
        super(resourceContext, instances, transparentInstances, labels);
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        if (!isVisible(track.getPosition()))
            return;
        
        com.badlogic.gdx.graphics.Color blockedColor = getTrackBlockedColor(track);
        
        track.forEach(route -> {
            Dir d1 = route.getFirst();
            Dir d2 = route.getSecond();
            float shortenL1 = 1.0f;
            float shortenR1 = 1.0f;
            float shortenL2 = 1.0f;
            float shortenR2 = 1.0f;

            int dist = d1.angularDistance(d2);
            int absDist = Math.abs(dist);
            boolean d1Connected = isConnected(track, d1);
            boolean d2Connected = isConnected(track, d2);
            if (absDist >= 1 && absDist <= 3) {
                Vector3 p1 = new Vector3(PathGeometry.getDirX(d1), 0, PathGeometry.getDirZ(d1));
                Vector3 p2 = new Vector3(PathGeometry.getDirX(d2), 0, PathGeometry.getDirZ(d2));
                Vector3 pc = new Vector3(0, 0, 0);
                
                renderMultiSegmentCurve(track.getPosition(), p1, pc, p2, d1Connected && d2Connected, 0, resourceContext.railModel, blockedColor);
            } else {
                drawHalfTrack(track.getPosition(), d1, d1Connected, shortenL1, shortenR1, blockedColor);
                drawHalfTrack(track.getPosition(), d2, d2Connected, shortenL2, shortenR2, blockedColor);
            }
        });

        if (modelRef != null && modelRef.getGroundMap() != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(track.getPosition());
            if (terrain != null && terrain == GroundMap.WATER) {
                ModelInstance pillar = resourceContext.getModelInstance(resourceContext.bridgePillarModel);
                pillar.transform.setToTranslation(
                        track.getPosition().getX() + 0.5f, -1.05f, track.getPosition().getY() + 0.5f);
                pillar.transform.scale(1f, 1.9f, 1f);
                instances.add(pillar);
            }
        }

        if (track.getNumRoutes() == 0) {
            Dir dir = getValidOrientation(track);
            if (dir != null) {
                drawHalfTrack(track.getPosition(), dir, true, 1.0f, 1.0f, blockedColor);
            }
        }
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        visitRailTrack(track);
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        visitRailTrack(bridgeGateRailTrack);
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        visitRailTrack(bridgeRailTrack);
    }

    public void drawHalfTrack(Point pos, Dir dir, boolean connected, float shortenL, float shortenR) {
        drawHalfTrack(pos, dir, connected, shortenL, shortenR, null);
    }

    public void drawHalfTrack(Point pos, Dir dir, boolean connected, float shortenL, float shortenR, com.badlogic.gdx.graphics.Color tintColor) {
        drawHalfTrackElevated(pos, dir, connected, 0.0f, shortenL, shortenR, resourceContext.railModel, tintColor);
    }

    public void drawHalfTrackElevated(Point pos, Dir dir, boolean connected, float elevation,
            float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        drawHalfTrackElevated(pos, dir, connected, elevation, shortenL, shortenR, railModelToUse, null);
    }

    public void drawHalfTrackElevated(Point pos, Dir dir, boolean connected, float elevation,
            float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse, com.badlogic.gdx.graphics.Color tintColor) {
        float dx = PathGeometry.getDirX(dir);
        float dz = PathGeometry.getDirZ(dir);
        renderSegment(pos, new Vector3(0, 0, 0), new Vector3(dx, 0, dz), 
                connected, elevation, shortenL, shortenR, railModelToUse, tintColor);
    }

    public void renderMultiSegmentCurve(Point pos, Vector3 p0, Vector3 pc, Vector3 p2, 
            boolean connected, float elevation, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        renderMultiSegmentCurve(pos, p0, pc, p2, connected, elevation, railModelToUse, null);
    }

    public void renderMultiSegmentCurve(Point pos, Vector3 p0, Vector3 pc, Vector3 p2, 
            boolean connected, float elevation, com.badlogic.gdx.graphics.g3d.Model railModelToUse, com.badlogic.gdx.graphics.Color tintColor) {
        int numSegments = 10;
        Vector3 pPrev = new Vector3();
        Vector3 pCurr = new Vector3();
        Vector3 nPrev = new Vector3();
        Vector3 nCurr = new Vector3();
        Vector3 tan = new Vector3();

        for (int i = 0; i < numSegments; i++) {
            float t0 = (float) i / numSegments;
            float t1 = (float) (i + 1) / numSegments;
            PathGeometry.getQuadraticBezier(pPrev, p0, pc, p2, t0);
            PathGeometry.getQuadraticBezier(pCurr, p0, pc, p2, t1);
            PathGeometry.getQuadraticBezierTangent(tan, p0, pc, p2, t0);
            nPrev.set(-tan.z, 0, tan.x).nor();
            PathGeometry.getQuadraticBezierTangent(tan, p0, pc, p2, t1);
            nCurr.set(-tan.z, 0, tan.x).nor();
            
            renderLineModel(pos, pPrev, pCurr, 0.03f + elevation, resourceContext.ballastModel, null);
            if (connected) {
                Vector3 startOut = new Vector3(pPrev).add(nPrev.x * 0.15f, 0, nPrev.z * 0.15f);
                Vector3 endOut = new Vector3(pCurr).add(nCurr.x * 0.15f, 0, nCurr.z * 0.15f);
                renderLineModel(pos, startOut, endOut, 0.08f + elevation, railModelToUse, tintColor);
                Vector3 startIn = new Vector3(pPrev).sub(nPrev.x * 0.15f, 0, nPrev.z * 0.15f);
                Vector3 endIn = new Vector3(pCurr).sub(nCurr.x * 0.15f, 0, nCurr.z * 0.15f);
                renderLineModel(pos, startIn, endIn, 0.08f + elevation, railModelToUse, tintColor);
            } else if (i == numSegments / 2) {
                renderLineModel(pos, pPrev, pCurr, 0.2f + elevation, resourceContext.invalidRailModel, null);
            }
        }
    }

    public void renderLineModel(Point pos, Vector3 pStart, Vector3 pEnd, float y, com.badlogic.gdx.graphics.g3d.Model model) {
        renderLineModel(pos, pStart, pEnd, y, model, null);
    }

    public void renderLineModel(Point pos, Vector3 pStart, Vector3 pEnd, float y, com.badlogic.gdx.graphics.g3d.Model model, com.badlogic.gdx.graphics.Color tintColor) {
        Vector3 diff = new Vector3(pEnd).sub(pStart);
        float len = diff.len();
        if (len < 0.001f) return;
        float angle = (float) Math.atan2(diff.x, diff.z) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        Vector3 mid = new Vector3(pStart).add(pEnd).scl(0.5f);
        ModelInstance instance = resourceContext.getModelInstance(model);
        if (tintColor != null && !instance.materials.isEmpty()) {
            instance.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(tintColor));
        }
        instance.transform.setToTranslation(pos.getX() + 0.5f + mid.x, y, pos.getY() + 0.5f + mid.z);
        instance.transform.rotate(0, 1, 0, angle);
        instance.transform.scale(1, 1, len / 0.5f);
        instances.add(instance);
    }

    public void renderSegment(Point pos, Vector3 pStart, Vector3 pEnd, 
            boolean connected, float elevation, float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        renderSegment(pos, pStart, pEnd, connected, elevation, shortenL, shortenR, railModelToUse, null);
    }

    public void renderSegment(Point pos, Vector3 pStart, Vector3 pEnd, 
            boolean connected, float elevation, float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse, com.badlogic.gdx.graphics.Color tintColor) {
        Vector3 diff = new Vector3(pEnd).sub(pStart);
        float len = diff.len();
        if (len < 0.001f) return;
        float angle = (float) Math.atan2(diff.x, diff.z) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        Vector3 mid = new Vector3(pStart).add(pEnd).scl(0.5f);
        
        float shortenB = (shortenL + shortenR) / 2f;
        float shortenBallast = connected ? shortenB : 0.95f;
        float scaleB = (len * shortenBallast) / 0.5f;
        ModelInstance ballast = resourceContext.getModelInstance(resourceContext.ballastModel);
        ballast.transform.setToTranslation(pos.getX() + 0.5f + mid.x, 0.03f + elevation, pos.getY() + 0.5f + mid.z);
        ballast.transform.rotate(0, 1, 0, angle);
        ballast.transform.scale(1, 1, scaleB);
        instances.add(ballast);

        if (connected) {
            float offX = (-diff.z / len) * 0.15f;
            float offZ = (diff.x / len) * 0.15f;
            float scale = len / 0.5f;
            
            float shiftX_L = diff.x * (1 - shortenL) / 2f;
            float shiftZ_L = diff.z * (1 - shortenL) / 2f;
            ModelInstance railL = resourceContext.getModelInstance(railModelToUse);
            if (tintColor != null && !railL.materials.isEmpty()) {
                railL.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(tintColor));
            }
            railL.transform.setToTranslation(pos.getX() + 0.5f + mid.x + offX + shiftX_L, 0.08f + elevation, pos.getY() + 0.5f + mid.z + offZ + shiftZ_L);
            railL.transform.rotate(0, 1, 0, angle);
            railL.transform.scale(1, 1, scale * shortenL);
            instances.add(railL);

            float shiftX_R = diff.x * (1 - shortenR) / 2f;
            float shiftZ_R = diff.z * (1 - shortenR) / 2f;
            ModelInstance railR = resourceContext.getModelInstance(railModelToUse);
            if (tintColor != null && !railR.materials.isEmpty()) {
                railR.materials.get(0).set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(tintColor));
            }
            railR.transform.setToTranslation(pos.getX() + 0.5f + mid.x - offX + shiftX_R, 0.08f + elevation, pos.getY() + 0.5f + mid.z - offZ + shiftZ_R);
            railR.transform.rotate(0, 1, 0, angle);
            railR.transform.scale(1, 1, scale * shortenR);
            instances.add(railR);
        } else {
            ModelInstance grader = resourceContext.getModelInstance(resourceContext.invalidRailModel);
            grader.transform.setToTranslation(pos.getX() + 0.5f + pStart.x + diff.x * 0.7f, 0.2f + elevation, pos.getY() + 0.5f + pStart.z + diff.z * 0.7f);
            grader.transform.rotate(0, 1, 0, angle);
            instances.add(grader);
        }
    }

    public com.badlogic.gdx.graphics.Color getTrackBlockedColor(RailTrack track) {
        if (modelRef == null || track == null) {
            return null;
        }
        letrain.vehicle.rail.impl.Train ownerTrain = null;
        letrain.segments.RailwayGraph graph = modelRef.getRailwayGraph();
        letrain.segments.BlockManager blockManager = modelRef.getBlockManager();
        if (graph != null && blockManager != null) {
            letrain.segments.Segment segment = graph.getSegment(track);
            if (segment != null) {
                List<letrain.vehicle.rail.impl.Train> owners = blockManager.getOwners(segment);
                if (owners != null && !owners.isEmpty()) {
                    ownerTrain = owners.get(0);
                }
            }
        }
        if (ownerTrain == null && track.getLinker() != null) {
            ownerTrain = track.getLinker().getTrain();
        }
        if (ownerTrain == null) {
            return null;
        }
        Locomotive loco = null;
        if (ownerTrain.getDirectorLinker() instanceof Locomotive) {
            loco = (Locomotive) ownerTrain.getDirectorLinker();
        } else {
            for (letrain.vehicle.rail.Linker l : ownerTrain.getLinkers()) {
                if (l instanceof Locomotive) {
                    loco = (Locomotive) l;
                    break;
                }
            }
        }
        if (loco != null && loco.getColor() != null) {
            return VehicleRenderer.getLibGdxColor(loco.getColor());
        }
        return null;
    }

    public Dir getValidOrientation(RailTrack track) {
        Dir dir = track.getFirstOpenDir();
        if (dir == null)
            return Dir.N;
        return dir;
    }

    public boolean isConnected(letrain.track.Track track, Dir dir) {
        letrain.track.Track neighbor = track.getConnected(dir);
        if (neighbor == null)
            return false;
        return neighbor.getRouter().getDir(dir.inverse()) != null;
    }
}
