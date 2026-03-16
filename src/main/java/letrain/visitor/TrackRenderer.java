package letrain.visitor;

import java.util.List;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.utils.PathGeometry;
import letrain.ground.GroundMap;

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
                
                renderMultiSegmentCurve(track.getPosition(), p1, pc, p2, d1Connected && d2Connected, 0, resourceContext.railModel);
            } else {
                drawHalfTrack(track.getPosition(), d1, d1Connected, shortenL1, shortenR1);
                drawHalfTrack(track.getPosition(), d2, d2Connected, shortenL2, shortenR2);
            }
        });

        if (modelRef != null && modelRef.getGroundMap() != null) {
            Integer terrain = modelRef.getGroundMap().getValueAt(track.getPosition());
            if (terrain != null && terrain == GroundMap.WATER) {
                ModelInstance pillar = new ModelInstance(resourceContext.bridgePillarModel);
                pillar.transform.setToTranslation(
                        track.getPosition().getX() + 0.5f, -1.05f, track.getPosition().getY() + 0.5f);
                pillar.transform.scale(1f, 1.9f, 1f);
                instances.add(pillar);
            }
        }

        if (track.getNumRoutes() == 0) {
            Dir d = getValidOrientation(track);
            if (d != null) {
                drawHalfTrack(track.getPosition(), d, true, 1.0f, 1.0f);
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

    public void drawHalfTrack(Point pos, Dir d, boolean connected, float shortenL, float shortenR) {
        drawHalfTrackElevated(pos, d, connected, 0.0f, shortenL, shortenR, resourceContext.railModel);
    }

    public void drawHalfTrackElevated(Point pos, Dir d, boolean connected, float elevation,
            float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        float dx = PathGeometry.getDirX(d);
        float dz = PathGeometry.getDirZ(d);
        renderSegment(pos, new Vector3(0, 0, 0), new Vector3(dx, 0, dz), 
                connected, elevation, shortenL, shortenR, railModelToUse);
    }

    public void renderMultiSegmentCurve(Point pos, Vector3 p0, Vector3 pc, Vector3 p2, 
            boolean connected, float elevation, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
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
            
            renderLineModel(pos, pPrev, pCurr, 0.03f + elevation, resourceContext.ballastModel);
            if (connected) {
                Vector3 startOut = new Vector3(pPrev).add(nPrev.x * 0.15f, 0, nPrev.z * 0.15f);
                Vector3 endOut = new Vector3(pCurr).add(nCurr.x * 0.15f, 0, nCurr.z * 0.15f);
                renderLineModel(pos, startOut, endOut, 0.08f + elevation, railModelToUse);
                Vector3 startIn = new Vector3(pPrev).sub(nPrev.x * 0.15f, 0, nPrev.z * 0.15f);
                Vector3 endIn = new Vector3(pCurr).sub(nCurr.x * 0.15f, 0, nCurr.z * 0.15f);
                renderLineModel(pos, startIn, endIn, 0.08f + elevation, railModelToUse);
            } else if (i == numSegments / 2) {
                renderLineModel(pos, pPrev, pCurr, 0.2f + elevation, resourceContext.invalidRailModel);
            }
        }
    }

    public void renderLineModel(Point pos, Vector3 pStart, Vector3 pEnd, float y, com.badlogic.gdx.graphics.g3d.Model model) {
        Vector3 diff = new Vector3(pEnd).sub(pStart);
        float len = diff.len();
        if (len < 0.001f) return;
        float angle = (float) Math.atan2(diff.x, diff.z) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        Vector3 mid = new Vector3(pStart).add(pEnd).scl(0.5f);
        ModelInstance instance = new ModelInstance(model);
        instance.transform.setToTranslation(pos.getX() + 0.5f + mid.x, y, pos.getY() + 0.5f + mid.z);
        instance.transform.rotate(0, 1, 0, angle);
        instance.transform.scale(1, 1, len / 0.5f);
        instances.add(instance);
    }

    public void renderSegment(Point pos, Vector3 pStart, Vector3 pEnd, 
            boolean connected, float elevation, float shortenL, float shortenR, com.badlogic.gdx.graphics.g3d.Model railModelToUse) {
        Vector3 diff = new Vector3(pEnd).sub(pStart);
        float len = diff.len();
        if (len < 0.001f) return;
        float angle = (float) Math.atan2(diff.x, diff.z) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        Vector3 mid = new Vector3(pStart).add(pEnd).scl(0.5f);
        
        float shortenB = (shortenL + shortenR) / 2f;
        float shortenBallast = connected ? shortenB : 0.95f;
        float scaleB = (len * shortenBallast) / 0.5f;
        ModelInstance ballast = new ModelInstance(resourceContext.ballastModel);
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
            ModelInstance railL = new ModelInstance(railModelToUse);
            railL.transform.setToTranslation(pos.getX() + 0.5f + mid.x + offX + shiftX_L, 0.08f + elevation, pos.getY() + 0.5f + mid.z + offZ + shiftZ_L);
            railL.transform.rotate(0, 1, 0, angle);
            railL.transform.scale(1, 1, scale * shortenL);
            instances.add(railL);

            float shiftX_R = diff.x * (1 - shortenR) / 2f;
            float shiftZ_R = diff.z * (1 - shortenR) / 2f;
            ModelInstance railR = new ModelInstance(railModelToUse);
            railR.transform.setToTranslation(pos.getX() + 0.5f + mid.x - offX + shiftX_R, 0.08f + elevation, pos.getY() + 0.5f + mid.z - offZ + shiftZ_R);
            railR.transform.rotate(0, 1, 0, angle);
            railR.transform.scale(1, 1, scale * shortenR);
            instances.add(railR);
        } else {
            ModelInstance grader = new ModelInstance(resourceContext.invalidRailModel);
            grader.transform.setToTranslation(pos.getX() + 0.5f + pStart.x + diff.x * 0.7f, 0.2f + elevation, pos.getY() + 0.5f + pStart.z + diff.z * 0.7f);
            grader.transform.rotate(0, 1, 0, angle);
            instances.add(grader);
        }
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
