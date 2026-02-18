package letrain.audio;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.antigravity.train.synth.AudioSample;
import com.antigravity.train.synth.TrainSynthesizer;
import javax.sound.sampled.UnsupportedAudioFileException;
import letrain.mvp.impl.Model;
import letrain.vehicle.impl.rail.Locomotive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioController {
    private static final Logger log = LoggerFactory.getLogger(AudioController.class);
    private final Model model;
    private final Map<Integer, TrainSynthesizer> synthesizers = new HashMap<>();
    private AudioSample defaultSample;
    private boolean enabled = true;

    public AudioController(Model model) {
        this.model = model;
        loadResources();
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
                // We might need a proper dispose here if TrainSynthesizer has resources to
                // free,
                // but currently stopAudio just stops the thread loop.
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
                    synth.startAudio();
                }
                synthesizers.put(loco.getId(), synth);
            }

            // Map speed 0-10 to throttle notches 0-9
            // LeTrain speed is 0 to 10.
            int speed = loco.getSpeed();
            // speed 0 -> Notch 0 (Idle)
            // speed 10 -> Notch 9
            int notch = Math.min(speed, 9);

            System.err.println("AudioController: Loco " + loco.getId() + " Speed: " + speed + " -> Notch: " + notch);

            synth.setThrottle(notch);
        }
    }

    public void stop() {
        for (TrainSynthesizer synth : synthesizers.values()) {
            synth.stopAudio();
        }
        synthesizers.clear();
    }
}
