package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;

import letrain.audio.core.AudioSource;

public class TrainSynthesizer implements AudioSource {

    private GrainEngine locoEngine;
    private GrainEngine coachEngine;

    // Configuration
    private SpeedNotch[] notches = new SpeedNotch[10];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0;

    // State
    private boolean isTransitioning = false;
    private Thread transitionThread;

    // Position
    private float x, y, z;
    private float refDistance = 50.0f;
    private float maxDistance = 1000.0f;

    // Active flag for Mixer
    private boolean audioRunning = false;

    // Listeners
    private List<SynthesizerListener> listeners = new ArrayList<>();

    public interface SynthesizerListener {
        void onSpeedUpdate(float displaySpeed);

        void onNotchChanged(int notchIndex);
    }

    // Base volumes
    private float baseLocoVolume = 1.0f;
    private float baseCoachVolume = 1.0f;

    public TrainSynthesizer() {
        locoEngine = new GrainEngine();
        coachEngine = new GrainEngine();

        // Defaults
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        coachEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);

        // 20% probability of flip
        locoEngine.setTurnProbability(0.2f);
        coachEngine.setTurnProbability(0.2f);

        loadResources();
    }

    private void loadResources() {
        try {
            // Load WAV
            java.net.URL wavUrl = getClass().getResource("/sound/train-sound.wav");
            if (wavUrl == null) {
                System.err.println("TrainSynthesizer: train-sound.wav not found!");
                return;
            }
            AudioSample sample = new AudioSample(wavUrl);
            setSample(sample);

            // Load Labels
            java.io.InputStream labelsStream = getClass().getResourceAsStream("/sound/train-sound-labels.txt");
            if (labelsStream == null) {
                System.err.println("TrainSynthesizer: train-sound-labels.txt not found! Using defaults.");
                initDefaultNotches(); // Fallback
                return;
            }

            // Parse Labels
            List<letrain.audio.util.AudacityLabelParser.Label> labels = letrain.audio.util.AudacityLabelParser
                    .parse(labelsStream);

            initNotchesFromLabels(labels, sample);

        } catch (Exception e) {
            e.printStackTrace();
            initDefaultNotches();
        }
    }

    private void initNotchesFromLabels(List<letrain.audio.util.AudacityLabelParser.Label> labels, AudioSample sample) {
        // Find regions
        double ralentiStart = 0, ralentiEnd = 0;
        double cruiseStart = 0, cruiseEnd = 0;
        double wagonsStart = 0, wagonsEnd = 0;

        for (letrain.audio.util.AudacityLabelParser.Label l : labels) {
            if (l.name.equalsIgnoreCase("ralenti")) {
                ralentiStart = l.startTime;
                ralentiEnd = l.endTime;
            } else if (l.name.equalsIgnoreCase("cruise")) {
                cruiseStart = l.startTime;
                cruiseEnd = l.endTime;
            } else if (l.name.equalsIgnoreCase("wagons")) {
                wagonsStart = l.startTime;
                wagonsEnd = l.endTime;
            }
        }

        // Convert seconds to samples
        float rate = sample.getSampleRate(); // defaults to 44100 usually, but getSample has it?
        // AudioSample doesn't expose rate easily? It does: sampleRate field.
        // But usually we assume 44100 if not specified. GrainEngine assumes 44100.
        // Let's assume the sample rate of the file matches system (44100).
        // Labels are in seconds.

        // Setup Notches
        for (int i = 0; i < 10; i++) {
            float start, cruise, end;

            if (i == 0) { // Idle (Ralenti)
                start = 0.0f;
                cruise = 0.0f;
                end = 0.0f;
            } else if (i == 1) { // First velocity
                start = 0.0f;
                cruise = 0.05f;
                end = 0.05f;
            } else {
                float center = 0.10f * (i - 1);
                start = center - 0.05f;
                cruise = center;
                end = center + 0.05f;
            }

            // Map loop points
            // Notch 0 uses Ralenti loop
            // Notches 1-9 use Cruise loop (pitched)
            float lStart = (float) (i == 0 ? ralentiStart : cruiseStart) * 44100f;
            float lEnd = (float) (i == 0 ? ralentiEnd : cruiseEnd) * 44100f;

            // Wagons alway same loop
            float cStart = (float) wagonsStart * 44100f;
            float cEnd = (float) wagonsEnd * 44100f;

            notches[i] = new SpeedNotch("Notch " + i, start, cruise, end,
                    lStart, lEnd, cStart, cEnd, 2.0f);
        }
        notches[0].name = "Idle";
    }

    private void initDefaultNotches() {
        // ... (Original logic as fallback)
        // Loop points (samples): Loco 7302-21125, Coach 95715-204992
        float locoStart = 7302f;
        float locoEnd = 21125f;
        float coachStart = 95715f;
        float coachEnd = 204992f;

        for (int i = 0; i < 10; i++) {
            float start, cruise, end;

            if (i == 0) { // Idle
                start = 0.0f;
                cruise = 0.0f;
                end = 0.0f;
            } else if (i == 1) { // First velocity
                start = 0.0f;
                cruise = 0.05f;
                end = 0.05f;
            } else {
                float center = 0.10f * (i - 1);
                start = center - 0.05f;
                cruise = center;
                end = center + 0.05f;
            }

            notches[i] = new SpeedNotch("Notch " + i, start, cruise, end,
                    locoStart, locoEnd, coachStart, coachEnd, 2.0f);
        }
        notches[0].name = "Idle";
    }

    // --- Audio Source Impl ---
    @Override
    public boolean read(float[] buffer) {
        if (!audioRunning)
            return false;

        // GrainEngine.read() accumulates (+=), so we just pass the buffer
        // which was cleared by the Mixer. or sourceBuffer provided by Mixer.

        locoEngine.read(buffer);
        coachEngine.read(buffer);
        return true;
    }

    @Override
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public float getReferenceDistance() {
        return refDistance;
    }

    @Override
    public float getMaxDistance() {
        return maxDistance;
    }

    public void setAudioRange(float ref, float max) {
        this.refDistance = ref;
        this.maxDistance = max;
    }

    public void setSample(AudioSample sample) {
        locoEngine.setSample(sample);
        coachEngine.setSample(sample);
        // Apply initial loop points for current notch
        if (sample != null) {
            SpeedNotch current = notches[currentNotchIndex];

            // If notches are not yet initialized (e.g. during construction), skip
            if (current != null) {
                float lStart = convertMsToNorm(current.loopStart, sample);
                float lEnd = convertMsToNorm(current.loopEnd, sample);
                float cStart = convertMsToNorm(current.coachLoopStart, sample);
                float cEnd = convertMsToNorm(current.coachLoopEnd, sample);

                locoEngine.setLoopPoints(lStart, lEnd);
                coachEngine.setLoopPoints(cStart, cEnd);
            }

            // Set sample rate to match sample or mixer?
            // Ideally Mixer tells us. For now assume 44100.
            locoEngine.setSampleRate(44100.0f);
            coachEngine.setSampleRate(44100.0f);
        }
    }

    // Kept for compatibility but now just flags
    public void startAudio() {
        audioRunning = true;
    }

    public void stopAudio() {
        audioRunning = false;
    }

    private float convertMsToNorm(float ms, AudioSample sample) {
        if (sample == null || sample.getLength() == 0)
            return 0.0f;
        float rate = sample.getSampleRate();
        float length = sample.getLength();
        // samples = ms * (rate / 1000)
        float samples = ms * (rate / 1000.0f);
        return samples / length;
    }

    // --- volume & Randomness ---
    public void setLocoVolume(float vol) {
        this.baseLocoVolume = vol;
        locoEngine.setVolume(vol);
    }

    public void setCoachVolume(float vol) {
        this.baseCoachVolume = vol;
        updateCoachVolume(coachEngine.getSpeed());
    }

    private void updateCoachVolume(float speed) {
        float dynamicFactor = Math.max(0.0f, Math.min(1.0f, speed * 1.2f));

        if (speed <= 0.001f) {
            dynamicFactor = 0.0f;
        }

        coachEngine.setVolume(baseCoachVolume * dynamicFactor);
    }

    public void setLocoRandomness(float prob, float duration) {
        locoEngine.setTurnProbability(prob);
        locoEngine.setReverseDuration(duration);
    }

    public void setCoachRandomness(float prob, float duration) {
        coachEngine.setTurnProbability(prob);
        coachEngine.setReverseDuration(duration);
    }

    // --- Notches ---
    public void setNotch(int index, SpeedNotch notch) {
        if (index >= 0 && index < notches.length) {
            notches[index] = notch;
        }
    }

    public SpeedNotch getNotch(int index) {
        if (index >= 0 && index < notches.length) {
            return notches[index];
        }
        return null;
    }

    public SpeedNotch[] getNotches() {
        return notches;
    }

    public void addListener(SynthesizerListener l) {
        listeners.add(l);
    }

    private void notifySpeed(float speed) {
        for (SynthesizerListener l : listeners)
            l.onSpeedUpdate(speed);
    }

    private void notifyNotch(int idx) {
        for (SynthesizerListener l : listeners)
            l.onNotchChanged(idx);
    }

    // --- Driving ---
    public void setThrottle(int index) {
        if (index < 0 || index >= notches.length)
            return;

        // If we are already heading to this index, do nothing
        if (index == targetNotchIndex)
            return;

        // If we essentially reached it and not transitioning
        if (index == currentNotchIndex && !isTransitioning)
            return;

        targetNotchIndex = index;

        if (isTransitioning && transitionThread != null && transitionThread.isAlive()) {
            transitionThread.interrupt();
        }

        isTransitioning = true;

        SpeedNotch current = notches[currentNotchIndex];
        SpeedNotch target = notches[index];

        boolean isUpshift = index > currentNotchIndex;

        // PHASE 1: EXIT CURRENT NOTCH
        float startSpeed1 = locoEngine.getSpeed();

        float targetSpeed1 = isUpshift ? current.endSpeed : current.startSpeed;

        float duration1 = current.rampTime / 2.0f;

        runRamp(startSpeed1, targetSpeed1, duration1, () -> {

            // PHASE 2: ENTER NEW NOTCH
            currentNotchIndex = index;
            notifyNotch(index);

            // Switch Loops
            // Normalize loop points
            AudioSample s = locoEngine.getSample();
            float lStart = convertMsToNorm(target.loopStart, s);
            float lEnd = convertMsToNorm(target.loopEnd, s);
            float cStart = convertMsToNorm(target.coachLoopStart, s);
            float cEnd = convertMsToNorm(target.coachLoopEnd, s);

            locoEngine.setLoopPoints(lStart, lEnd);
            coachEngine.setLoopPoints(cStart, cEnd);

            float startSpeed2 = isUpshift ? target.startSpeed : target.endSpeed;
            float targetSpeed2 = target.cruiseSpeed;

            // Jump
            locoEngine.setSpeed(startSpeed2);
            coachEngine.setSpeed(startSpeed2);
            updateCoachVolume(startSpeed2);

            float duration2 = target.rampTime / 2.0f;

            runRamp(startSpeed2, targetSpeed2, duration2, () -> {
                isTransitioning = false;
                // Final snap
                locoEngine.setSpeed(targetSpeed2);
                coachEngine.setSpeed(targetSpeed2);
                updateCoachVolume(targetSpeed2);
                notifySpeed(targetSpeed2);
            });
        });
    }

    private void runRamp(float startSpeed, float targetSpeed, float durationSec, Runnable onComplete) {
        if (transitionThread != null && transitionThread.isAlive() && transitionThread != Thread.currentThread()) {
            transitionThread.interrupt();
            try {
                transitionThread.join(100);
            } catch (InterruptedException e) {
            }
        }

        Thread newThread = new Thread(() -> {
            int interval = 33;
            int steps = (int) ((durationSec * 1000) / interval);
            if (steps < 1)
                steps = 1;

            float speedStep = (targetSpeed - startSpeed) / steps;

            for (int i = 1; i <= steps; i++) {
                if (Thread.currentThread().isInterrupted())
                    return;

                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    return;
                }

                float newSpeed = startSpeed + (speedStep * i);

                // Clamping
                if (speedStep > 0 && newSpeed > targetSpeed)
                    newSpeed = targetSpeed;
                if (speedStep < 0 && newSpeed < targetSpeed)
                    newSpeed = targetSpeed;

                locoEngine.setSpeed(newSpeed);
                coachEngine.setSpeed(newSpeed);
                updateCoachVolume(newSpeed);
                notifySpeed(newSpeed);
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });

        transitionThread = newThread;
        newThread.start();
    }

    // --- Internal Engine Access ---
    public GrainEngine getLocoEngine() {
        return locoEngine;
    }

    public GrainEngine getCoachEngine() {
        return coachEngine;
    }
}
