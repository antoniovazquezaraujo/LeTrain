package letrain.ground;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Turns a listening position into zone weights, following ADR-025: the primary zone is the tile
 * under the focus and the secondary one is the non-primary zone with the most influence inside a
 * fixed radius. Influence sums a linear distance falloff per sampled tile, so both proximity and
 * extent count.
 */
public class ZoneSensor {

    public static final float SECONDARY_MAX = 0.45f;

    public record Result(String primary, Map<String, Float> influence,
            Map<String, Float> proximity, Map<String, Float> weights) {

        public Result {
            influence = Map.copyOf(influence);
            proximity = Map.copyOf(proximity);
            weights = Map.copyOf(weights);
        }

        public float weightOf(String zone) {
            return weights.getOrDefault(zone, 0f);
        }
    }

    private final int radius;
    private final int stride;

    public ZoneSensor(int radius, int stride) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be >= 1");
        }
        if (stride < 1) {
            throw new IllegalArgumentException("stride must be >= 1");
        }
        this.radius = radius;
        this.stride = stride;
    }

    public int radius() {
        return radius;
    }

    public int stride() {
        return stride;
    }

    public Result sense(IntBinaryOperator terrainAt, int x, int y) {
        String primary = zoneOf(terrainAt.applyAsInt(x, y));
        if (primary == null) {
            return new Result(null, Map.of(), Map.of(), Map.of());
        }
        Map<String, Float> summed = new LinkedHashMap<>();
        Map<String, Float> proximity = new LinkedHashMap<>();
        float total = 0f;
        for (int dy = -radius; dy <= radius; dy += stride) {
            for (int dx = -radius; dx <= radius; dx += stride) {
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > radius) {
                    continue;
                }
                String zone = zoneOf(terrainAt.applyAsInt(x + dx, y + dy));
                if (zone == null) {
                    continue;
                }
                float falloff = 1f - distance / radius;
                summed.merge(zone, falloff, Float::sum);
                proximity.merge(zone, falloff, Math::max);
                total += falloff;
            }
        }
        Map<String, Float> influence = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : summed.entrySet()) {
            influence.put(entry.getKey(), total > 0f ? entry.getValue() / total : 0f);
        }
        String secondary = null;
        float bestProximity = 0f;
        float bestInfluence = 0f;
        for (Map.Entry<String, Float> entry : proximity.entrySet()) {
            if (entry.getKey().equals(primary)) {
                continue;
            }
            float zoneInfluence = influence.getOrDefault(entry.getKey(), 0f);
            if (entry.getValue() > bestProximity
                    || (entry.getValue() == bestProximity && zoneInfluence > bestInfluence)) {
                bestProximity = entry.getValue();
                bestInfluence = zoneInfluence;
                secondary = entry.getKey();
            }
        }
        Map<String, Float> weights = new LinkedHashMap<>();
        float secondaryWeight = Math.min(SECONDARY_MAX, SECONDARY_MAX * bestProximity);
        weights.put(primary, 1f - secondaryWeight);
        if (secondary != null) {
            weights.put(secondary, secondaryWeight);
        }
        return new Result(primary, influence, proximity, weights);
    }

    public static String zoneOf(int terrain) {
        return switch (terrain) {
            case GroundMap.GROUND -> "fields";
            case GroundMap.WATER -> "sea";
            case GroundMap.ROCK -> "mountain";
            case GroundMap.GOLD_MINE -> "gold-mine";
            case GroundMap.MINE -> "coal-mine";
            case GroundMap.RUBY_MINE -> "ruby-mine";
            case GroundMap.JEWELRY_STORE -> "gold-factory";
            case GroundMap.POWER_PLANT -> "coal-factory";
            case GroundMap.RUBY_STORE -> "ruby-factory";
            default -> null;
        };
    }
}
