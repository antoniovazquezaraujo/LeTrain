package letrain.audio;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.sound.sampled.UnsupportedAudioFileException;
import letrain.audio.core.AudioMixer;
import letrain.audio.synth.AudioSample;
import letrain.audio.synth.TrainSynthesizer;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.vehicle.impl.rail.Locomotive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioController {
    private static final Logger log = LoggerFactory.getLogger(AudioController.class);
    private final Model model;
    private final Map<Integer, TrainSynthesizer> synthesizers = new HashMap<>();
    private final AudioMixer mixer;
    private AudioSample defaultSample;
    private boolean enabled = true;

    public AudioController(Model model) {
        this.model = model;
        this.mixer = new AudioMixer();
        loadResources();
        if (enabled) {
            mixer.start();
        }
    }

    private void loadResources() {
        try {
            URL url = getClass().getResource("/sound/freesound_community-train-17869.wav");
            if (url == null) {
                log.error("Could not find default train sound resource");
                enabled = false;
                return;
            }
            defaultSample = new AudioSample(url);
        } catch (UnsupportedAudioFileException | IOException e) {
            log.error("Failed to load audio sample", e);
            enabled = false;
        }
    }

    public void setListenerPosition(float x, float y, float z, float angle) {
        mixer.setListenerPosition(x, y, z);
        mixer.setListenerAngle(angle);
    }

    public void update() {
        if (!enabled)
            return;

        // 1. Remove synthesizers for destroyed locomotives
        Iterator<Map.Entry<Integer, TrainSynthesizer>> it = synthesizers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrainSynthesizer> entry = it.next();
            Integer locoId = entry.getKey();
            boolean exists = false;
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.getId() == locoId) {
                    exists = true;
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
            TrainSynthesizer synth = synthesizers.get(loco.getId());
            if (synth == null) {
                synth = new TrainSynthesizer();
                if (defaultSample != null) {
                    synth.setSample(defaultSample);
                    synth.setLocoVolume(0.8f);
                    synth.setCoachVolume(0.6f);
                    synth.startAudio(); // Sets running flag
                }

                // Add to mixer
                mixer.addSource(synth);
                synthesizers.put(loco.getId(), synth);
            }

            // Sync Throttle
            // Map speed 0-10 to throttle notches 0-9
            int speed = loco.getSpeed();
            int notch = Math.min(speed, 9);
            synth.setThrottle(notch);

            // Sync Position
            // Assuming LeTrain coordinates are "blocks" or "meters".
            // AudioMixer expects generic units.
            // Let's assume 1 map unit = 10 meters for audio scale? Or 1:1?
            // LeTrain view might be 3D.
            Point pos = loco.getPosition();
            // Point is integer 2D?
            // We need to check if Locomotive has more precise position.
            // For now use Point.
            if (pos != null) {
                // If the game is 2D/3D, Z might be height or depth.
                // Let's map Map X/Y to Audio X/Z (horizontal plane) and Y is height?
                // Or Audio X/Y is map, Z is height.
                // Usually 3D audio: X=Right, Y=Up, Z=Forward/Back (-Z for OpenGL).
                // Let's use X=MapX, Y=0, Z=MapY.
                synth.setPosition((float) pos.getX(), 0, (float) pos.getY());
            }
        }
    }

    public void stop() {
        mixer.stop();
        for (TrainSynthesizer synth : synthesizers.values()) {
            synth.stopAudio();
        }
        synthesizers.clear();
    }
}
