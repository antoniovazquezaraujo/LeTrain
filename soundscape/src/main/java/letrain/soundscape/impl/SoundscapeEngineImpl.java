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
 * Composes the target mix as {@code zone weight x presence x climate x height x gain} for every
 * sound of every audible zone, plus the weather sounds (volume = intensity x gain). Zone sounds
 * also carry the listening height as air absorption, so zooming out muffles the ground world while
 * the height-provided sounds stay next to the listener.
 *
 * <p>
 * A sound that belongs to several close zones accumulates; a sound whose zone weight is zero is
 * silent regardless of its curves.
 */
public class SoundscapeEngineImpl implements SoundscapeEngine {

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
                float value = weight * presence * climateFactor(style, sound, input)
                        * heightFactor(style, sound, input);
                volumes.merge(sound, value, Float::sum);
                air.merge(sound, height, Math::max);
            }
        }
        for (String name : style.heightSounds().keySet()) {
            if (input.height() > 0f) {
                // Prefixed so a zone sound can never collide with a height-provided sound.
                volumes.merge("height-" + name, input.height(), Float::sum);
            }
        }
        addWeatherSound(volumes, style, "rain", input.rain());
        addWeatherSound(volumes, style, "wind", input.wind());
        addWeatherSound(volumes, style, "storm", input.storm());
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

    private void addWeatherSound(Map<String, Float> volumes, SoundscapeStyle style, String key,
            float intensity) {
        if (style.weatherSounds().containsKey(key) && intensity > 0f) {
            // Prefixed so a zone sound can never collide with the weather itself.
            volumes.merge("weather-" + key, intensity, Float::sum);
        }
    }
}
