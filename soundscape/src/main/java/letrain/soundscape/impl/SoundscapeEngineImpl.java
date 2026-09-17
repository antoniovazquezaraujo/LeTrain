package letrain.soundscape.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import letrain.soundscape.Band;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;

/**
 * Composes the target mix as {@code zone weight x presence x climate x height x gain x distance}
 * for every sound of every audible zone, plus the weather sounds (volume = intensity x gain x
 * distance). Distance comes from the listening height times the sound's air sensitivity:
 * {@code distance = 1 - 0.6 x height x airSensitivity}, and the air part is handed to the player as
 * a low-pass, so zooming out muffles and lowers the world produced away from the listener.
 * Height-provided sounds stay next to the listener unless they declare an air sensitivity.
 *
 * <p>
 * A sound that belongs to several close zones accumulates; a sound whose zone weight is zero is
 * silent regardless of its curves.
 */
public class SoundscapeEngineImpl implements SoundscapeEngine {

    /** Volume attenuation at full air distance (1.0 = -infinity, 0.0 = no attenuation). */
    private static final float AIR_ATTENUATION = 0.6f;

    @Override
    public Composition compose(SoundscapeStyle style, CompositionInput input) {
        Map<String, Float> volumes = new TreeMap<>();
        Map<String, Float> air = new HashMap<>();
        float height = Math.max(0f, Math.min(1f, input.height()));
        for (Map.Entry<String, Float> zone : input.zoneWeights().entrySet()) {
            float weight = zone.getValue() == null ? 0f : zone.getValue();
            if (weight <= 0f) {
                continue;
            }
            for (String sound : style.zoneSounds(zone.getKey())) {
                float presence = Band.interpolate(style.presenceOf(sound), input.time());
                float distance = distanceFactor(style, sound, height);
                float value = weight * presence * climateFactor(style, sound, input)
                        * heightFactor(style, sound, input) * distance;
                volumes.merge(sound, value, Float::sum);
                air.merge(sound, soundAir(style, sound, height), Math::max);
            }
        }
        for (String name : style.heightSounds().keySet()) {
            if (input.height() > 0f) {
                // Prefixed so a zone sound can never collide with a height-provided sound.
                String key = "height-" + name;
                float value = input.height();
                if (style.airSensitivity().containsKey(key)) {
                    value *= distanceFactor(style, key, height);
                    air.merge(key, soundAir(style, key, height), Math::max);
                }
                volumes.merge(key, value, Float::sum);
            }
        }
        addWeatherSound(volumes, air, style, "rain", input.rain(), height);
        addWeatherSound(volumes, air, style, "wind", input.wind(), height);
        addWeatherSound(volumes, air, style, "storm", input.storm(), height);
        volumes.replaceAll((sound, volume) -> Math.max(0f, volume * style.gainOf(sound)));
        return new Composition(volumes, air);
    }

    private float climateFactor(SoundscapeStyle style, String sound, CompositionInput input) {
        List<Float> sensitivity = style.climateSensitivityOf(sound);
        float factor = 1f + sensitivity.get(0) * input.rain() + sensitivity.get(1) * input.wind()
                + sensitivity.get(2) * input.storm();
        return Math.max(0f, factor);
    }

    private float heightFactor(SoundscapeStyle style, String sound, CompositionInput input) {
        return Math.max(0f, 1f + style.heightSensitivityOf(sound) * input.height());
    }

    private void addWeatherSound(Map<String, Float> volumes, Map<String, Float> air,
            SoundscapeStyle style, String key, float intensity, float height) {
        if (style.weatherSounds().containsKey(key) && intensity > 0f) {
            // Prefixed so a zone sound can never collide with the weather itself.
            String sound = "weather-" + key;
            volumes.merge(sound, intensity * distanceFactor(style, sound, height), Float::sum);
            air.merge(sound, soundAir(style, sound, height), Math::max);
        }
    }

    private float soundAir(SoundscapeStyle style, String sound, float height) {
        return Math.max(0f, Math.min(1f, height * style.airSensitivityOf(sound)));
    }

    private float distanceFactor(SoundscapeStyle style, String sound, float height) {
        return 1f - AIR_ATTENUATION * soundAir(style, sound, height);
    }
}
