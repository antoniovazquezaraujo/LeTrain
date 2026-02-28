package letrain.audio;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import letrain.audio.core.AudioMixer;
import letrain.audio.sources.WavSource;
import letrain.audio.synth.AudioSample;
import letrain.audio.synth.TrainSynthesizer;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.vehicle.impl.rail.Locomotive;

public class AudioController {
    // Logger removed as unused
    private final Model model;
    private final Map<Integer, TrainSynthesizer> synthesizers = new HashMap<>();
    private final AudioMixer mixer;
    private boolean enabled = true;
    private letrain.audio.sources.SequencedAmbientSource jackhammerSource;

    // 1 Game Unit = 20 Real Meters (Approx length of a train car)
    private static final float SCALE_FACTOR = 20.0f;

    private final Map<String, AudioSample> samples = new HashMap<>();
    private final List<WavSource> oneShotSources = new CopyOnWriteArrayList<>();

    public void stopSynthesizer(int id) {
        TrainSynthesizer synth = synthesizers.get(id);
        if (synth != null) {
            synth.stopAudio();
            synthesizers.remove(id);
        }
    }

    public AudioController(Model model) {
        this.model = model;
        this.mixer = new AudioMixer();
        loadSamples();
        // TrainSynthesizer handles its own resources now
        if (enabled) {
            mixer.start();
        }
    }

    private void loadSamples() {
        loadSample("link", "train-link.wav");
        loadSample("fork", "fork.wav");
        loadSample("hammer", "hammer.wav");
    }

    private void loadSample(String name, String filename) {
        try {
            // Relative to classpath
            java.net.URL url = getClass().getResource("/sound/" + filename);
            if (url != null) {
                System.out.println("Loading " + filename + " from resources: " + url);
                samples.put(name, new AudioSample(url));
            } else {
                // Try direct file if not in resources (for dev environment)
                File file = new File("src/main/resources/sound/" + filename);
                if (file.exists()) {
                    System.out.println("Loading " + filename + " from file: " + file.getAbsolutePath());
                    samples.put(name, new AudioSample(file));
                } else {
                    System.err
                            .println("CRITICAL: " + filename + " not found in resources or src/main/resources/sound/");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setJackhammerActive(boolean active, float x, float y) {
        if (!enabled)
            return;
        if (active) {
            if (jackhammerSource == null) {
                AudioSample sample = samples.get("hammer");
                if (sample != null) {
                    jackhammerSource = new letrain.audio.sources.SequencedAmbientSource(sample);
                    jackhammerSource.setVolume(0.5f);
                    jackhammerSource.setRange(2.0f * SCALE_FACTOR, 50.0f * SCALE_FACTOR);
                    mixer.addSource(jackhammerSource);
                }
            }
            if (jackhammerSource != null) {
                jackhammerSource.setActive(true);
                jackhammerSource.setPosition(x * SCALE_FACTOR, y * SCALE_FACTOR, 0);
            }
        } else {
            if (jackhammerSource != null) {
                jackhammerSource.setActive(false);
            }
        }
    }

    public void playOneShot(String name, float x, float y) {
        AudioSample sample = samples.get(name);
        if (sample != null) {
            WavSource source = new WavSource(sample);
            // Z is height in our AudioMixer/Gdx3DView mapping
            source.setPosition(x * SCALE_FACTOR, y * SCALE_FACTOR, 0);

            // Make one-shots much more audible
            // Coordinates are in game units * SCALE_FACTOR (20)
            // So distance 50 -> 1000 meters.
            // Setting refDistance to 1000 means full volume up to distance 50.
            source.setVolume(1.0f);
            // We don't have direct access to set distances easily in current source,
            // but we can modify the default in constructor or here if we had methods.
            // Since WavSource is ours, I'll add methods or just modify the defaults.

            System.out.println("DEBUG: Playing one-shot '" + name + "' at game pos (" + x + "," + y + ")");

            oneShotSources.add(source);
            mixer.addSource(source);
        } else {
            System.err.println("Audio sample not found: " + name);
        }
    }

    public void setListenerPosition(float x, float y, float z, float angle) {
        float lx = x * SCALE_FACTOR;
        float ly = y * SCALE_FACTOR;
        float lz = z * SCALE_FACTOR;

        mixer.setListenerPosition(lx, ly, lz);
        mixer.setListenerAngle(angle);
    }

    public void update() {
        if (!enabled)
            return;

        // Clean up finished one-shots
        oneShotSources.removeIf(source -> {
            if (!source.isActive()) {
                mixer.removeSource(source);
                return true;
            }
            return false;
        });

        // One-shots are now played directly from the UI controller (Presenter)

        // 1. Remove synthesizers for destroyed locomotives
        Iterator<Map.Entry<Integer, TrainSynthesizer>> it = synthesizers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrainSynthesizer> entry = it.next();
            Integer locoId = entry.getKey();
            boolean exists = false;
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.getId() == locoId) {
                    if (!loco.isDestroying()) {
                        exists = true;
                    }
                    break;
                }
            }
            if (!exists) {
                TrainSynthesizer synth = entry.getValue();
                synth.stopAudio();
                mixer.removeSource(synth);
                it.remove();
            }
        }

        // 2. Add/Update synthesizers
        for (Locomotive loco : model.getLocomotives()) {
            if (loco.isDestroying()) {
                continue;
            }
            TrainSynthesizer synth = synthesizers.get(loco.getId());
            if (synth == null) {
                synth = new TrainSynthesizer();
                // Audio Physics Defaults
                // 3m Ref Distance (Full volume), 1000m Max Distance (Silence)
                // 1.0x Filter Sensitivity (Logarithmic Falloff)
                synth.setAudioRange(3.0f * SCALE_FACTOR, 1000.0f * SCALE_FACTOR);
                synth.setFilterSensitivity(1.0f);
                synth.setLocoVolume(0.8f);
                synth.setCoachVolume(0.6f);

                synth.startAudio();

                // Add to mixer
                mixer.addSource(synth);
                synthesizers.put(loco.getId(), synth);
            }

            // Sync Throttle (Engine Sound)
            int targetNotch = Math.min(loco.getTargetSpeed(), 9);
            synth.setThrottle(targetNotch);

            // Sync Motion (Rolling Sound)
            int currentSpeed = loco.getSpeed();
            synth.setMotionSpeed(currentSpeed);

            // Sync Position
            Point pos = loco.getPosition();
            if (pos != null) {
                // Map Game Coordinates: X->X, Y->Y (Audio Depth), 0->Z (Audio Height/Elevation)
                // LeTrain is 2D grid (X, Y). Camera is (X, Z=Depth, Y=Height)
                // Consistent with setListenerPosition(cam.x, cam.z, cam.y)
                synth.setPosition(
                        (float) pos.getX() * SCALE_FACTOR,
                        (float) pos.getY() * SCALE_FACTOR,
                        0);
            }

            // Sync States
            loco.setEngineStarting(synth.isEngineStarting());
            boolean braking = loco.isBraking();
            if (braking) {
                System.out.println("DEBUG: Loco " + loco.getId() + " is BRAKING (Current: " + loco.getSpeed()
                        + ", Target: " + loco.getTargetSpeed() + ")");
            }
            synth.setBraking(braking);

            // Sync Loading State
            if (loco.getTrain() != null) {
                synth.setLoading(loco.getTrain().isLoading());
            }
        }
    }

    public void stop() {
        mixer.stop();
        if (jackhammerSource != null) {
            mixer.removeSource(jackhammerSource);
            jackhammerSource = null;
        }
        for (TrainSynthesizer synth : synthesizers.values()) {
            synth.stopAudio();
        }
        synthesizers.clear();
    }
}
