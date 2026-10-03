package letrain.soundscape;

import java.time.LocalTime;
import java.util.List;

/**
 * The seven fixed time-of-day bands used as control points for presence curves. The enum order is
 * the column order of the {@code [presence]} table in a style file.
 *
 * <p>
 * Each band has a representative anchor time; between anchors the values are linearly interpolated,
 * wrapping around midnight. This is why a style receives the exact time and not a band name: at
 * 03:40 the mix is a blend of the surrounding anchors.
 */
public enum Band {
    DAWN("dawn", 6 * 60), MORNING("morning", 9 * 60), NOON("noon", 13 * 60), AFTERNOON("afternoon",
            16 * 60 + 30), DUSK("dusk",
                    19 * 60 + 30), NIGHT("night", 22 * 60 + 30), PREDAWN("predawn", 3 * 60);

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final String key;
    private final int anchorMinute;

    Band(String key, int anchorMinute) {
        this.key = key;
        this.anchorMinute = anchorMinute;
    }

    public String key() {
        return key;
    }

    public int anchorMinute() {
        return anchorMinute;
    }

    /**
     * Interpolates a presence curve (one value per band, in enum order) at the given time.
     */
    public static float interpolate(List<Float> curve, LocalTime time) {
        validate(curve);
        int minute = time.getHour() * 60 + time.getMinute();
        Band[] anchors = orderByClock();
        int[] minutes = anchorMinutes(anchors);

        int m = minute;
        if (m < minutes[0]) {
            m += MINUTES_PER_DAY;
        }
        for (int i = 0; i < anchors.length - 1; i++) {
            if (m >= minutes[i] && m < minutes[i + 1]) {
                float t = (m - minutes[i]) / (float) (minutes[i + 1] - minutes[i]);
                return lerp(curve.get(anchors[i].ordinal()), curve.get(anchors[i + 1].ordinal()),
                        t);
            }
        }
        // After the last anchor (night): blend towards predawn of the next day.
        int last = anchors.length - 1;
        int nextPredawn = minutes[0] + MINUTES_PER_DAY;
        float t = (m - minutes[last]) / (float) (nextPredawn - minutes[last]);
        return lerp(curve.get(anchors[last].ordinal()), curve.get(anchors[0].ordinal()), t);
    }

    private static void validate(List<Float> curve) {
        if (curve == null || curve.size() != values().length) {
            throw new IllegalArgumentException(
                    "presence curve must have " + values().length + " values (one per band)");
        }
    }

    private static Band[] orderByClock() {
        return new Band[] {PREDAWN, DAWN, MORNING, NOON, AFTERNOON, DUSK, NIGHT};
    }

    private static int[] anchorMinutes(Band[] anchors) {
        int[] minutes = new int[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            minutes[i] = anchors[i].anchorMinute();
        }
        return minutes;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
