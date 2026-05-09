package letrain.utils;

import com.badlogic.gdx.math.Vector3;
import letrain.map.Dir;
import letrain.track.Track;

/**
 * Centrailizes all path geometry and Bezier interpolation logic.
 */
public class PathGeometry {

    public static float getDirX(Dir dir) {
        if (dir == null) return 0;
        switch (dir) {
            case E: case NE: case SE: return 0.5f;
            case W: case NW: case SW: return -0.5f;
            default: return 0;
        }
    }

    public static float getDirZ(Dir dir) {
        if (dir == null) return 0;
        switch (dir) {
            case S: case SE: case SW: return 0.5f;
            case N: case NE: case NW: return -0.5f;
            default: return 0;
        }
    }

    public static void getQuadraticBezier(Vector3 out, Vector3 p0, Vector3 p1, Vector3 p2, float t) {
        float invT = 1f - t;
        out.set(p0).scl(invT * invT)
           .add(p1.x * 2f * invT * t, p1.y * 2f * invT * t, p1.z * 2f * invT * t)
           .add(p2.x * t * t, p2.y * t * t, p2.z * t * t);
    }

    public static void getQuadraticBezierTangent(Vector3 out, Vector3 p0, Vector3 p1, Vector3 p2, float t) {
        float invT = 1f - t;
        out.set(p1).sub(p0).scl(2f * invT)
           .add((p2.x - p1.x) * 2f * t, (p2.y - p1.y) * 2f * t, (p2.z - p1.z) * 2f * t);
    }

    public static void calculateBezierPoint(float cellX, float cellY, Dir dEntry, Dir dExit, float t,
            Vector3 outPos, Vector3 outTangent) {
        
        if (dEntry == null) dEntry = dExit.inverse();

        Vector3 pControl = new Vector3(cellX + 0.5f, 0, cellY + 0.5f);
        Vector3 pStart = new Vector3(cellX + 0.5f + getDirX(dEntry), 0, cellY + 0.5f + getDirZ(dEntry));
        Vector3 pEnd = new Vector3(cellX + 0.5f + getDirX(dExit), 0, cellY + 0.5f + getDirZ(dExit));
        
        getQuadraticBezier(outPos, pStart, pControl, pEnd, t);
        getQuadraticBezierTangent(outTangent, pStart, pControl, pEnd, t);
    }

    /**
     * Calculates the position and tangent for a two-stage path interpolation (continuity).
     * 
     * @param cellX Current cell X
     * @param cellY Current cell Y
     * @param dEntry Entry direction into current cell
     * @param dExit Exit direction from current cell
     * @param currentTrack Current track object (to find next track)
     * @param progress Progress from 0.0 to 1.0 (Center A -> Center B)
     * @param currentSpeed Current speed (to determine if we stop at center)
     * @param outPos Vector3 to store the result position
     * @param outTangent Vector3 to store the result tangent
     */
    public static void calculateTwoStagePath(float cellX, float cellY, Dir dEntry, Dir dExit, Track currentTrack, 
                                            float progress, float currentSpeed, boolean canEnterNext,
                                            Vector3 outPos, Vector3 outTangent) {
        if (progress < 0.5f) {
            // Phase 1: Current Cell (Center -> Exit)
            float t = 0.5f + (canEnterNext ? progress : 0.0f);
            calculateBezierPoint(cellX, cellY, dEntry, dExit, t, outPos, outTangent);
        } else {
            // Phase 2: Next Cell (Entry -> Center)
            Track nextTrack = (currentTrack != null) ? currentTrack.getConnected(dExit) : null;
            if (nextTrack != null && currentSpeed > 0 && canEnterNext) {
                Dir nextEntry = dExit.inverse();
                Dir nextExit = nextTrack.getDir(nextEntry);
                float nextX = nextTrack.getPosition().getX();
                float nextY = nextTrack.getPosition().getY();
                
                float t = progress - 0.5f; // [0.0, 0.5]
                calculateBezierPoint(nextX, nextY, nextEntry, nextExit, t, outPos, outTangent);
            } else {
                // Blocked or stationary: Stay at center of current cell
                calculateBezierPoint(cellX, cellY, dEntry, dExit, 0.5f, outPos, outTangent);
            }
        }
    }
}
