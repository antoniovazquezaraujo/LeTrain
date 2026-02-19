package letrain.audio;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import letrain.audio.core.AudioMixer;
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

    // 1 Game Unit = 20 Real Meters (Approx length of a train car)
    private static final float SCALE_FACTOR = 20.0f;

    public AudioController(Model model) {
        this.model = model;
        this.mixer = new AudioMixer();
        // TrainSynthesizer handles its own resources now
        if (enabled) {
            mixer.start();
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
                // Audio Physics Defaults
                // 50m Ref Distance, 2000m Max Distance (Silence)
                // 1.0x Filter Sensitivity (Logarithmic Falloff)
                synth.setAudioRange(50.0f, 2000.0f);
                synth.setFilterSensitivity(1.0f);
                synth.setLocoVolume(0.8f);
                synth.setCoachVolume(0.6f);

                synth.startAudio();

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
            Point pos = loco.getPosition();
            if (pos != null) {
                // Map Game Coordinates: X->X, Z->Y (Audio Depth), Y->Z (Audio Height/Elevation)
                // LeTrain is 2D grid (X, Y) -> Audio (X, Z)
                // We assume Y=0 (Gound) for trains
                // Apply Scale Factor
                synth.setPosition(
                        (float) pos.getX() * SCALE_FACTOR,
                        0,
                        (float) pos.getY() * SCALE_FACTOR);
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
