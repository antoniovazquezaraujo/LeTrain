package letrain.visitor.gdx3d;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import letrain.vehicle.rail.impl.Locomotive;

/**
 * Picks which locomotives get a real light (ADR-022 phase 1e). Real lights are expensive and the
 * default shader only holds a handful, so only the nearest ones to the camera are lit; the rest
 * keep the emissive lamp dot.
 */
public final class Headlights {

    private Headlights() {}

    /**
     * The locomotives nearest to {@code origin} first, at most {@code max}. A null list means no
     * locomotives at all.
     */
    public static List<Locomotive> nearestTo(List<Locomotive> locomotives, Vector3 origin,
            int max) {
        List<Locomotive> candidates = new ArrayList<>();
        if (locomotives != null) {
            candidates.addAll(locomotives);
        }
        candidates.sort(Comparator.comparingDouble(l -> squaredDistance(l, origin)));
        if (candidates.size() > max) {
            candidates.subList(max, candidates.size()).clear();
        }
        return candidates;
    }

    private static double squaredDistance(Locomotive locomotive, Vector3 origin) {
        double dx = locomotive.getPosition().getX() + 0.5 - origin.x;
        double dz = locomotive.getPosition().getY() + 0.5 - origin.z;
        return dx * dx + dz * dz;
    }
}
