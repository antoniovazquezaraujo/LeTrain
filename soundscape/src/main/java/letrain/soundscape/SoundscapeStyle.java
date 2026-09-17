package letrain.soundscape;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed style file: the soundscape definition of a map. Everything is data: climate presets,
 * sound catalog, zones, presence curves, height sensitivities, weather sounds and height-provided
 * sounds.
 */
public final class SoundscapeStyle {

    private final Map<String, ClimatePreset> climatePresets;
    private final Map<String, SoundDef> sounds;
    private final Map<String, List<String>> zones;
    private final Map<String, List<Float>> presence;
    private final Map<String, Float> heightSensitivity;
    private final Map<String, List<Float>> climateSensitivity;
    private final Map<String, SoundDef> weatherSounds;
    private final Map<String, SoundDef> heightSounds;
    private final Map<String, Float> gains;
    private final Map<String, Float> airSensitivity;

    public SoundscapeStyle(Map<String, ClimatePreset> climatePresets, Map<String, SoundDef> sounds,
            Map<String, List<String>> zones, Map<String, List<Float>> presence,
            Map<String, Float> heightSensitivity, Map<String, List<Float>> climateSensitivity,
            Map<String, SoundDef> weatherSounds, Map<String, SoundDef> heightSounds,
            Map<String, Float> gains, Map<String, Float> airSensitivity) {
        this.climatePresets = Collections.unmodifiableMap(new LinkedHashMap<>(climatePresets));
        this.sounds = Collections.unmodifiableMap(new LinkedHashMap<>(sounds));
        this.zones = immutableListValues(zones);
        this.presence = immutableListValues(presence);
        this.heightSensitivity =
                Collections.unmodifiableMap(new LinkedHashMap<>(heightSensitivity));
        this.climateSensitivity = immutableListValues(climateSensitivity);
        this.weatherSounds = Collections.unmodifiableMap(new LinkedHashMap<>(weatherSounds));
        this.heightSounds = Collections.unmodifiableMap(new LinkedHashMap<>(heightSounds));
        this.gains = Collections.unmodifiableMap(new LinkedHashMap<>(gains));
        this.airSensitivity = Collections.unmodifiableMap(new LinkedHashMap<>(airSensitivity));
    }

    public Map<String, ClimatePreset> climatePresets() {
        return climatePresets;
    }

    public Map<String, SoundDef> sounds() {
        return sounds;
    }

    public Map<String, List<String>> zones() {
        return zones;
    }

    public Map<String, List<Float>> presence() {
        return presence;
    }

    public Map<String, Float> heightSensitivity() {
        return heightSensitivity;
    }

    public Map<String, List<Float>> climateSensitivity() {
        return climateSensitivity;
    }

    public Map<String, SoundDef> weatherSounds() {
        return weatherSounds;
    }

    public Map<String, SoundDef> heightSounds() {
        return heightSounds;
    }

    /** Per-sound loudness multipliers; a sound with no entry sounds unchanged (1.0). */
    public Map<String, Float> gains() {
        return gains;
    }

    /** Global loudness multiplier of a sound; 1 when the style declares none. */
    public float gainOf(String sound) {
        return gains.getOrDefault(sound, 1f);
    }

    /** A copy of this style with a different gain table (used by the GUI calibration). */
    public SoundscapeStyle withGains(Map<String, Float> gains) {
        return new SoundscapeStyle(climatePresets, sounds, zones, presence, heightSensitivity,
                climateSensitivity, weatherSounds, heightSounds, gains, airSensitivity);
    }

    /** Per-sound air sensitivity: 0 stays next to the listener, 1 recedes fully with the zoom. */
    public Map<String, Float> airSensitivity() {
        return airSensitivity;
    }

    /**
     * Air sensitivity of a sound; defaults to 1 (the sound is produced away from the listener).
     * Height-provided sounds default to 0 in the engine unless they declare one here.
     */
    public float airSensitivityOf(String sound) {
        return airSensitivity.getOrDefault(sound, 1f);
    }

    /** Sounds of a zone, or an empty list when the zone is unknown. */
    public List<String> zoneSounds(String zone) {
        return zones.getOrDefault(zone, List.of());
    }

    /** Height sensitivity of a sound; 0 when the sound declares none. */
    public float heightSensitivityOf(String sound) {
        return heightSensitivity.getOrDefault(sound, 0f);
    }

    /** Climate sensitivities (rain, wind, storm) of a sound; zeros when the sound declares none. */
    public List<Float> climateSensitivityOf(String sound) {
        return climateSensitivity.getOrDefault(sound, List.of(0f, 0f, 0f));
    }

    /** Presence curve (one value per band) of a sound; zeros when the sound declares none. */
    public List<Float> presenceOf(String sound) {
        List<Float> curve = presence.get(sound);
        return curve != null ? curve : zeroPresence();
    }

    private List<Float> zeroPresence() {
        return Collections.nCopies(Band.values().length, 0f);
    }

    private static <V> Map<String, List<V>> immutableListValues(Map<String, List<V>> map) {
        Map<String, List<V>> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
