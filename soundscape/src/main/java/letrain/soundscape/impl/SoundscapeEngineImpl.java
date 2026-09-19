package letrain.soundscape.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import letrain.soundscape.Band;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundGate;
import letrain.soundscape.SoundscapeStyle;

/**
 * Composes the target mix as {@code zone weight x presence x climate x height x gain x distance}
 * for every sound of every audible zone, plus the weather sounds (volume = intensity x gain x
 * distance). Distance comes from the listening height times the sound's distance sensitivity:
 * {@code distance = 1 - 0.6 x height x distanceSensitivity}, and the distance part is handed to the
 * player as a low-pass, so zooming out muffles and lowers the world produced away from the
 * listener. Height-provided sounds stay next to the listener unless they declare an distance
 * sensitivity. Behaviour gates ({@code [silence-when]}) silence a sound when rain, wind or storm
 * cross their threshold: crickets stop singing in the rain instead of fading out.
 *
 * <p>
 * A sound that belongs to several close zones accumulates; a sound whose zone weight is zero is
 * silent regardless of its curves.
 */
public class SoundscapeEngineImpl implements SoundscapeEngine {

    /** Volume attenuation at full distance (1.0 = -infinity, 0.0 = no attenuation). */
    private static final float DISTANCE_ATTENUATION = 0.6f;
    /** Width of the fade band around a gate threshold, in state units. */
    private static final float GATE_FEATHER = 0.05f;

    @Override
    public Composition compose(SoundscapeStyle style, CompositionInput input) {
        Map<String, Float> volumes = new TreeMap<>();
        Map<String, Float> distance = new HashMap<>();
        float height = Math.max(0f, Math.min(1f, input.height()));
        for (Map.Entry<String, Float> zone : input.zoneWeights().entrySet()) {
            float weight = zone.getValue() == null ? 0f : zone.getValue();
            if (weight <= 0f) {
                continue;
            }
            float zoneGain = style.zoneGainOf(zone.getKey());
            for (String sound : style.zoneSounds(zone.getKey())) {
                float presence = Band.interpolate(style.presenceOf(sound), input.time());
                float distanceGain = distanceFactor(style, sound, height);
                float value = weight * zoneGain * presence * climateFactor(style, sound, input)
                        * heightFactor(style, sound, input) * distanceGain
                        * gateFactor(style, sound, input);
                volumes.merge(sound, value, Float::sum);
                distance.merge(sound, soundDistance(style, sound, height), Math::max);
            }
        }
        for (String name : style.heightSounds().keySet()) {
            if (input.height() > 0f) {
                // Prefixed so a zone sound can never collide with a height-provided sound.
                String key = "height-" + name;
                float value = input.height();
                if (style.distanceSensitivity().containsKey(key)) {
                    value *= distanceFactor(style, key, height);
                    distance.merge(key, soundDistance(style, key, height), Math::max);
                }
                volumes.merge(key, value, Float::sum);
            }
        }
        addWeatherSound(volumes, distance, style, "rain", input.rain(), height);
        addWeatherSound(volumes, distance, style, "wind", input.wind(), height);
        addWeatherSound(volumes, distance, style, "storm", input.storm(), height);
        volumes.replaceAll((sound, volume) -> Math.max(0f, volume * style.gainOf(sound)));
        return new Composition(volumes, distance);
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

    private void addWeatherSound(Map<String, Float> volumes, Map<String, Float> distance,
            SoundscapeStyle style, String key, float intensity, float height) {
        if (style.weatherSounds().containsKey(key) && intensity > 0f) {
            // Prefixed so a zone sound can never collide with the weather itself.
            String sound = "weather-" + key;
            volumes.merge(sound, intensity * distanceFactor(style, sound, height), Float::sum);
            distance.merge(sound, soundDistance(style, sound, height), Math::max);
        }
    }

    private float gateFactor(SoundscapeStyle style, String sound, CompositionInput input) {
        float factor = 1f;
        for (SoundGate gate : style.gatesOf(sound)) {
            float value = switch (gate.variable()) {
                case "rain" -> input.rain();
                case "wind" -> input.wind();
                default -> input.storm();
            };
            factor *= Math.max(0f, Math.min(1f, (gate.threshold() - value) / GATE_FEATHER));
        }
        return factor;
    }

    private float soundDistance(SoundscapeStyle style, String sound, float height) {
        float sensitivity = style.distanceSensitivityOf(sound);
        float floor = style.minDistanceOf(sound);
        return Math.max(0f, Math.min(1f, Math.max(floor, height * sensitivity)));
    }

    private float distanceFactor(SoundscapeStyle style, String sound, float height) {
        return 1f - DISTANCE_ATTENUATION * soundDistance(style, sound, height);
    }
}
