package letrain.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * State of the New Game form (S2, issue #746). Presentation-neutral: holds the raw text of every
 * catalogue field plus the terrain proportions, enforces the terrain cross-field rule (water +
 * mountain ≤ {@link SettingsCatalog#MAX_TERRAIN_PERCENT} %) and validates the whole form against
 * the catalogue on {@link #toSettings()}. The 2D dialog and the 3D New Game dialog (S4, #777) only
 * has to bind widgets to {@link #value(String)} / {@link #setValue(String, String)} and to the
 * sliders.
 *
 * <p>
 * {@link #forNewGame(Map)} prefills the form: every catalogue default is overlaid with the current
 * effective configuration (so a local {@code letrain.cfg} keeps its non-terrain values), the seed
 * is drawn fresh (D3) and the terrain proportions use the catalogue defaults (D2: 25 / 10) unless
 * the effective configuration already carries percent keys. The literal {@code random} seed is
 * resolved when the dialog opens, so the field always shows a real number the player can keep, edit
 * or copy.
 */
public final class NewGameForm {

    /** Upper bound of the drawn random seeds (inclusive lower bound is 1). */
    public static final int MAX_SEED = 999_999;

    public static final String SEED_KEY = "seed";
    public static final String WATER_KEY = "terrain.waterPercent";
    public static final String MOUNTAIN_KEY = "terrain.mountainPercent";

    private final Map<String, String> values;
    private int seed;
    private int waterPercent;
    private int mountainPercent;

    private NewGameForm(Map<String, String> values) {
        this.values = values;
    }

    /** A fresh form for a new game, drawing a random seed from {@code random}. */
    public static NewGameForm forNewGame(Map<String, String> effectiveConfig, Random random) {
        Map<String, String> values = new LinkedHashMap<>();
        for (SettingDefinition def : SettingsCatalog.definitions()) {
            String current = effectiveConfig == null ? null : effectiveConfig.get(def.key());
            values.put(def.key(), current != null ? current : def.defaultValue());
        }
        NewGameForm form = new NewGameForm(values);
        form.seed = nextSeed(random);
        values.put(SEED_KEY, String.valueOf(form.seed));
        form.waterPercent =
                clampPercent(readPercent(values, WATER_KEY, SettingsCatalog.DEFAULT_WATER_PERCENT));
        form.mountainPercent = clampPercent(
                readPercent(values, MOUNTAIN_KEY, SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT));
        form.clampCrossField(WATER_KEY);
        form.syncTerrainValues();
        return form;
    }

    /** A fresh form for a new game with a system-drawn random seed. */
    public static NewGameForm forNewGame(Map<String, String> effectiveConfig) {
        return forNewGame(effectiveConfig, new Random());
    }

    /** Fresh random seed in {@code [1, MAX_SEED]}; seed 0 stays reserved for "engine picks one". */
    public static int nextSeed(Random random) {
        return 1 + random.nextInt(MAX_SEED);
    }

    /**
     * Resolves the seed text a client got from its New Game dialog: {@code random} (or a
     * missing/blank value) draws a fresh seed, anything else goes through the catalogue's seed
     * validation (integer in range). Shared by the 2D terminal and the 3D client so both clients
     * start the same world for the same text.
     *
     * @throws IllegalArgumentException when the text is neither {@code random} nor a valid seed
     */
    public static int resolveSeed(String seedText) {
        String normalized = SettingsCatalog.validate(SEED_KEY,
                seedText == null || seedText.isBlank() ? "random" : seedText);
        if (normalized.equals("random")) {
            return nextSeed(new Random());
        }
        return Integer.parseInt(normalized);
    }

    /** Current raw text of a catalogue field. */
    public String value(String key) {
        return values.getOrDefault(key, "");
    }

    /** Replaces the raw text of a catalogue field (widget binding; validated on toSettings). */
    public void setValue(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    public int seed() {
        return seed;
    }

    /** Sets the seed from a text box; a blank or malformed value is ignored (Start validates). */
    public void setSeed(String seedText) {
        try {
            seed = Integer.parseInt(seedText.trim());
        } catch (NumberFormatException e) {
            // Keep the last valid seed; the field text is validated on Start.
        }
    }

    public int waterPercent() {
        return waterPercent;
    }

    public int mountainPercent() {
        return mountainPercent;
    }

    /** Field proportion: the remainder of the 100 % bar. */
    public int fieldPercent() {
        return Math.max(0, 100 - waterPercent - mountainPercent);
    }

    /**
     * Moves the water slider. When water + mountain would exceed the catalogue maximum, the
     * mountain proportion is clamped down (the bar always adds up to 100 %).
     */
    public void setWaterPercent(int percent) {
        waterPercent = clampPercent(percent);
        clampCrossField(WATER_KEY);
        syncTerrainValues();
    }

    /** Moves the mountain slider, clamping water down when needed (mirror of the water rule). */
    public void setMountainPercent(int percent) {
        mountainPercent = clampPercent(percent);
        clampCrossField(MOUNTAIN_KEY);
        syncTerrainValues();
    }

    /**
     * Validates the whole form against the catalogue and returns the normalised settings map, ready
     * for {@code Model.newGame}. Unknown keys are ignored; the first invalid value throws.
     *
     * @throws IllegalArgumentException when a field is malformed or out of range, or when water +
     *         mountain exceeds {@link SettingsCatalog#MAX_TERRAIN_PERCENT} %
     */
    public Map<String, String> toSettings() {
        values.put(SEED_KEY, String.valueOf(seed));
        syncTerrainValues();
        return SettingsCatalog.validateAll(values);
    }

    private void syncTerrainValues() {
        values.put(WATER_KEY, String.valueOf(waterPercent));
        values.put(MOUNTAIN_KEY, String.valueOf(mountainPercent));
    }

    /** Keeps the moved proportion and clamps the other one so the pair fits the catalogue. */
    private void clampCrossField(String movedKey) {
        if (waterPercent + mountainPercent <= SettingsCatalog.MAX_TERRAIN_PERCENT) {
            return;
        }
        if (WATER_KEY.equals(movedKey)) {
            mountainPercent = Math.max(0, SettingsCatalog.MAX_TERRAIN_PERCENT - waterPercent);
        } else {
            waterPercent = Math.max(0, SettingsCatalog.MAX_TERRAIN_PERCENT - mountainPercent);
        }
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(SettingsCatalog.MAX_TERRAIN_PERCENT, percent));
    }

    private static int readPercent(Map<String, String> values, String key, int fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
