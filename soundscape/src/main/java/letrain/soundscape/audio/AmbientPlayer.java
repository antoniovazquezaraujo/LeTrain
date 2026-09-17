package letrain.soundscape.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import letrain.soundscape.Composition;
import letrain.soundscape.SoundDef;
import letrain.soundscape.SoundscapeStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays the composed mix: resolves and loads the style materials, keeps one looping voice per
 * audible sound and writes the mixed stereo stream to the sound device.
 *
 * <p>
 * Missing materials are not fatal: they are reported and stay silent. The voice randomness comes
 * from a seed, so the same sessions are reproducible.
 */
public class AmbientPlayer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AmbientPlayer.class);
    private static final float SAMPLE_RATE = 44100f;
    private static final int BUFFER_FRAMES = 1024;
    private static final float MIN_VOLUME = 0.005f;
    /** Master gain; the soft limiter smooths any busy mix on top. */
    private static final float MASTER_GAIN = 1.0f;

    private final Map<String, SoundSample> samples = new LinkedHashMap<>();
    private final List<String> missingSounds = new ArrayList<>();
    private final Map<String, AmbientVoice> voices = new HashMap<>();
    private final Random random;

    private volatile Map<String, Float> targets = Map.of();
    private volatile boolean running;
    private SourceDataLine line;
    private Thread thread;

    public AmbientPlayer(SoundscapeStyle style, long seed) {
        this.random = new Random(seed);
        SampleResolver resolver = new SampleResolver();
        SampleLoader loader = new SampleLoader();
        style.sounds().forEach((name, def) -> load(name, def, resolver, loader));
        style.weatherSounds().forEach((key, def) -> load("weather-" + key, def, resolver, loader));
        style.heightSounds().forEach((key, def) -> load("height-" + key, def, resolver, loader));
    }

    /** Number of sounds that could be loaded. */
    public int sampleCount() {
        return samples.size();
    }

    /** Sounds declared in the style whose materials could not be resolved or decoded. */
    public List<String> missingSounds() {
        return List.copyOf(missingSounds);
    }

    public void start() throws LineUnavailableException {
        if (running) {
            return;
        }
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        line = AudioSystem.getSourceDataLine(format);
        line.open(format, BUFFER_FRAMES * 4 * 4);
        line.start();
        running = true;
        thread = new Thread(this::loop, "soundscape-audio");
        thread.setDaemon(true);
        thread.start();
    }

    /** Sets the target volumes of the current composition. */
    public void updateTargets(Composition composition) {
        targets = composition == null ? Map.of() : composition.volumes();
    }

    /**
     * Mixes one mono buffer without touching the audio device; used by tests and offline rendering.
     */
    public void renderBuffer(float[] mono) {
        mixBuffer(mono);
    }

    @Override
    public void close() {
        running = false;
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        voices.clear();
    }

    private void load(String key, SoundDef def, SampleResolver resolver, SampleLoader loader) {
        List<java.net.URL> urls = new ArrayList<>();
        for (String material : def.material()) {
            urls.addAll(resolver.resolve(material));
        }
        if (urls.isEmpty()) {
            missingSounds.add(key);
            log.warn("no audio found for '{}' ({})", key, def.material());
            return;
        }
        java.net.URL chosen = urls.get(random.nextInt(urls.size()));
        try {
            samples.put(key, loader.load(chosen));
        } catch (java.io.IOException e) {
            missingSounds.add(key);
            log.warn("could not load {}: {}", chosen, e.getMessage());
        }
    }

    private void loop() {
        float[] mono = new float[BUFFER_FRAMES];
        byte[] output = new byte[BUFFER_FRAMES * 4];
        while (running) {
            mixBuffer(mono);
            toStereo16(mono, output);
            line.write(output, 0, output.length);
        }
    }

    private void mixBuffer(float[] mono) {
        java.util.Arrays.fill(mono, 0f);
        Map<String, Float> current = targets;
        for (Map.Entry<String, Float> entry : current.entrySet()) {
            SoundSample sample = samples.get(entry.getKey());
            if (sample == null) {
                continue;
            }
            float volume = entry.getValue() == null ? 0f : entry.getValue();
            AmbientVoice voice =
                    voices.computeIfAbsent(entry.getKey(), key -> new AmbientVoice(sample));
            voice.setTarget(volume > MIN_VOLUME ? volume : 0f);
        }
        for (Iterator<Map.Entry<String, AmbientVoice>> it = voices.entrySet().iterator(); it
                .hasNext();) {
            Map.Entry<String, AmbientVoice> entry = it.next();
            if (!current.containsKey(entry.getKey())) {
                entry.getValue().setTarget(0f);
            }
            entry.getValue().render(mono, mono.length);
            if (entry.getValue().isFinished()) {
                it.remove();
            }
        }
    }

    private void toStereo16(float[] mono, byte[] output) {
        for (int i = 0; i < mono.length; i++) {
            short sample = (short) Math.round(masterSample(mono[i]) * 32767f);
            output[i * 4] = (byte) (sample & 0xFF);
            output[i * 4 + 1] = (byte) ((sample >> 8) & 0xFF);
            output[i * 4 + 2] = output[i * 4];
            output[i * 4 + 3] = output[i * 4 + 1];
        }
    }

    /** Master gain plus soft limiter: never clips, even with several loud layers. */
    static float masterSample(float value) {
        return (float) Math.tanh(value * MASTER_GAIN);
    }
}
