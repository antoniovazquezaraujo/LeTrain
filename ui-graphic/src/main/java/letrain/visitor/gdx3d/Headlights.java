package letrain.visitor.gdx3d;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks which locomotive headlights get a ground pool (ADR-022 phase 1e, #690). Rendering a pool
 * for every locomotive would be wasteful and the far ones are barely visible, so only the nearest
 * ones to the camera are drawn; the rest keep the emissive lamp dot.
 *
 * <p>
 * A {@link Source} holds the <em>rendered</em> position of the locomotive (interpolated along its
 * route, the same the mesh uses), so the pool glides with the train instead of jumping cell by
 * cell.
 */
public final class Headlights {

    private Headlights() {}

    /** Rendered position and heading of one locomotive, ready to place a light. */
    public record Source(float x, float z, float dirX, float dirZ) {
    }

    /**
     * The sources nearest to {@code origin} first, at most {@code max}. A null list means no
     * locomotives at all.
     */
    public static List<Source> nearestTo(List<Source> sources, Vector3 origin, int max) {
        List<Source> candidates = new ArrayList<>();
        if (sources != null) {
            candidates.addAll(sources);
        }
        candidates.sort(Comparator.comparingDouble(s -> squaredDistance(s, origin)));
        if (candidates.size() > max) {
            candidates.subList(max, candidates.size()).clear();
        }
        return candidates;
    }

    private static double squaredDistance(Source source, Vector3 origin) {
        double dx = source.x() - origin.x;
        double dz = source.z() - origin.z;
        return dx * dx + dz * dz;
    }
}
