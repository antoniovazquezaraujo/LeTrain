package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;

import letrain.audio.core.AudioSource;

public class TrainSynthesizer implements AudioSource {

    private GrainEngine locoEngine;
    private GrainEngine coachEngine;
    private GrainEngine brakeEngine;
    private GrainEngine loadEngine;
    private float filterSensitivity = 1.0f;

    // Configuration
    private SpeedNotch[] notches = new SpeedNotch[10];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0;

    // State
    private boolean isTransitioning = false;
    private Thread transitionThread;
    private boolean engineStarting = false;
    private boolean brakingRequested = false;
    private boolean loading = false;
    private float targetLoadVolume = 0.0f;

    // Position
    private float x, y, z;
    private float refDistance = 100.0f;
    private float maxDistance = 2000.0f;

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

        brakeEngine = new GrainEngine();
        brakeEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
        brakeEngine.setTurnProbability(0f);

        loadEngine = new GrainEngine();
        loadEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
        loadEngine.setTurnProbability(0f);

        loadResources();
    }

    // Cache
    private static AudioSample sharedSample;
    private static AudioSample sharedBrakeSample;
    private static AudioSample sharedLoadSample;
    private static boolean resourcesLoaded = false;
    private static List<letrain.audio.util.AudacityLabelParser.Label> sharedLabels;

    private void loadResources() {
        if (resourcesLoaded && sharedSample != null) {
            setSample(sharedSample);
            if (sharedBrakeSample != null) {
                brakeEngine.setSample(sharedBrakeSample);
                brakeEngine.setLoopPoints(0, 1.0f);
                brakeEngine.setSpeed((1.0f - 0.8f) / 1.2f); // 1.0x playback
                brakeEngine.setVolume(0.0f);
                brakeEngine.setSampleRate(44100.0f);
            }
            if (sharedLoadSample != null) {
                loadEngine.setSample(sharedLoadSample);
                loadEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
                loadEngine.setSpeed((1.0f - 0.8f) / 1.2f); // 1.0x playback
                loadEngine.setVolume(0.0f);
                loadEngine.setSampleRate(44100.0f);
            }

            if (sharedLabels != null) {
                initNotchesFromLabels(sharedLabels, sharedSample);
            } else {
                initDefaultNotches();
            }
            return;
        }

        try {
            // Load WAV
            java.net.URL wavUrl = getClass().getResource("/sound/train-sound.wav");
            if (wavUrl == null) {
                System.err.println("TrainSynthesizer: train-sound.wav not found!");
                return;
            }
            sharedSample = new AudioSample(wavUrl);
            setSample(sharedSample);

            // Load Brakes WAV
            java.net.URL brakeUrl = getClass().getResource("/sound/train-brakes.wav");
            if (brakeUrl != null) {
                sharedBrakeSample = new AudioSample(brakeUrl);
                brakeEngine.setSample(sharedBrakeSample);
                brakeEngine.setLoopPoints(0, 1.0f); // Loop entire braking sample
                brakeEngine.setSpeed((1.0f - 0.8f) / 1.2f); // 1.0x playback
                brakeEngine.setVolume(0.0f);
                brakeEngine.setSampleRate(44100.0f);
            } else {
                System.err.println("TrainSynthesizer: train-brakes.wav not found!");
            }

            // Load Load/Unload WAV
            java.net.URL loadUrl = getClass().getResource("/sound/load-unload.wav");
            if (loadUrl != null) {
                sharedLoadSample = new AudioSample(loadUrl);
                loadEngine.setSample(sharedLoadSample);
                loadEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
                loadEngine.setSpeed((1.0f - 0.8f) / 1.2f); // Results in 1.0x playback rate
                loadEngine.setVolume(0.0f);
                loadEngine.setSampleRate(44100.0f);
            } else {
                System.err.println("TrainSynthesizer: load-unload.wav not found!");
            }

            // Load Labels
            java.io.InputStream labelsStream = getClass().getResourceAsStream("/sound/train-sound-labels.txt");
            if (labelsStream != null) {
                sharedLabels = letrain.audio.util.AudacityLabelParser.parse(labelsStream);
                initNotchesFromLabels(sharedLabels, sharedSample);
            } else {
                System.err.println("TrainSynthesizer: train-sound-labels.txt not found! Using defaults.");
                initDefaultNotches();
            }

            resourcesLoaded = true;

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

        updateBrakeVolume();

        locoEngine.read(buffer);
        coachEngine.read(buffer);
        if (brakeEngine != null) {
            brakeEngine.read(buffer);
        }
        updateLoadVolume();
        if (loadEngine != null) {
            loadEngine.read(buffer);
        }
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
                float lStart = convertSamplesToNorm(current.loopStart, sample);
                float lEnd = convertSamplesToNorm(current.loopEnd, sample);
                float cStart = convertSamplesToNorm(current.coachLoopStart, sample);
                float cEnd = convertSamplesToNorm(current.coachLoopEnd, sample);

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

    private float convertSamplesToNorm(float samples, AudioSample sample) {
        if (sample == null || sample.getLength() == 0)
            return 0.0f;
        float length = sample.getLength();
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

    private float targetBrakeVolume = 0.0f;

    public boolean isEngineStarting() {
        return engineStarting;
    }

    public void setBraking(boolean braking) {
        float newTarget = braking ? 0.8f : 0.0f;
        if (newTarget != this.targetBrakeVolume) {
            System.out.println("TrainSynth: Braking target volume changed to " + newTarget);
            this.targetBrakeVolume = newTarget;
        }
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        this.targetLoadVolume = loading ? 0.7f : 0.0f;
    }

    public boolean isLoading() {
        return loading;
    }

    private void updateBrakeVolume() {
        if (brakeEngine == null)
            return;
        float current = brakeEngine.getVolume();
        if (Math.abs(current - targetBrakeVolume) < 0.01f) {
            brakeEngine.setVolume(targetBrakeVolume);
        } else {
            // Smoothly ramp brake volume
            float step = (targetBrakeVolume > current) ? 0.02f : 0.01f;
            float nextVolume = current + (targetBrakeVolume > current ? step : -step);
            brakeEngine.setVolume(nextVolume);
        }
    }

    private void updateLoadVolume() {
        if (loadEngine == null)
            return;
        float current = loadEngine.getVolume();
        if (Math.abs(current - targetLoadVolume) < 0.01f) {
            loadEngine.setVolume(targetLoadVolume);
        } else {
            // Smoothly ramp load volume
            float step = (targetLoadVolume > current) ? 0.05f : 0.02f;
            float nextVolume = current + (targetLoadVolume > current ? step : -step);
            loadEngine.setVolume(nextVolume);
        }
    }

    public void setFilterSensitivity(float sensitivity) {
        this.filterSensitivity = sensitivity;
    }

    @Override
    public void setDistanceFilter(float amount) {
        float effective = Math.min(0.99f, amount * filterSensitivity);
        locoEngine.setDistanceFilter(effective);
        coachEngine.setDistanceFilter(effective);
        if (brakeEngine != null) {
            brakeEngine.setDistanceFilter(effective);
        }
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
    public synchronized void setThrottle(int index) {
        if (index < 0 || index >= notches.length)
            return;

        // Detect rapid reduction to trigger braking
        if (isTransitioning && index < targetNotchIndex && index < currentNotchIndex) {
            brakingRequested = true;
            setBraking(true);
        } else if (index >= currentNotchIndex) {
            brakingRequested = false;
        }

        targetNotchIndex = index;

        if (!isTransitioning) {
            startTransitionLoop();
        }
    }

    private void startTransitionLoop() {
        isTransitioning = true;
        transitionThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int next;
                    synchronized (this) {
                        if (currentNotchIndex == targetNotchIndex)
                            break;
                        next = currentNotchIndex + (targetNotchIndex > currentNotchIndex ? 1 : -1);
                    }

                    boolean isUpshift = next > currentNotchIndex;
                    SpeedNotch current = notches[currentNotchIndex];
                    SpeedNotch target = notches[next];

                    // Startup delay: Notch 0 -> 1
                    if (currentNotchIndex == 0 && next == 1) {
                        engineStarting = true;
                    }

                    // PHASE 1: EXIT CURRENT
                    float startSpeed1 = locoEngine.getSpeed();
                    float targetSpeed1 = isUpshift ? current.endSpeed : current.startSpeed;
                    float duration1 = current.rampTime / 2.0f;
                    performRampSync(startSpeed1, targetSpeed1, duration1);

                    // Switch Notch
                    currentNotchIndex = next;
                    notifyNotch(next);

                    // Switch Loops
                    AudioSample s = locoEngine.getSample();
                    float lStart = convertSamplesToNorm(target.loopStart, s);
                    float lEnd = convertSamplesToNorm(target.loopEnd, s);
                    locoEngine.setLoopPoints(lStart, lEnd);

                    // PHASE 2: ENTER NEXT
                    float startSpeed2 = isUpshift ? target.startSpeed : target.endSpeed;
                    float targetSpeed2 = target.cruiseSpeed;
                    locoEngine.setSpeed(startSpeed2);

                    float duration2 = target.rampTime / 2.0f;

                    // Deceleration Shortcut: "sin pasar por la velocidad de crucero"
                    if (!isUpshift && brakingRequested && currentNotchIndex != targetNotchIndex) {
                        locoEngine.setSpeed(targetSpeed2);
                    } else {
                        performRampSync(startSpeed2, targetSpeed2, duration2);
                    }

                    if (currentNotchIndex == 1) {
                        engineStarting = false;
                    }
                }
            } finally {
                synchronized (this) {
                    isTransitioning = false;
                    engineStarting = false;
                    setBraking(false);
                    brakingRequested = false;
                }
            }
        });
        transitionThread.setDaemon(true);
        transitionThread.start();
    }

    private void performRampSync(float startSpeed, float targetSpeed, float durationSec) {
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
            if (speedStep > 0 && newSpeed > targetSpeed)
                newSpeed = targetSpeed;
            if (speedStep < 0 && newSpeed < targetSpeed)
                newSpeed = targetSpeed;

            locoEngine.setSpeed(newSpeed);
            notifySpeed(newSpeed);
        }
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
                notifySpeed(newSpeed);
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });

        transitionThread = newThread;
        newThread.start();
    }

    public void setMotionSpeed(int speed) {
        int index = Math.min(Math.max(0, speed), notches.length - 1);
        SpeedNotch notch = notches[index];
        if (notch == null)
            return;

        AudioSample s = coachEngine.getSample();
        if (s != null) {
            float cStart = convertSamplesToNorm(notch.coachLoopStart, s);
            float cEnd = convertSamplesToNorm(notch.coachLoopEnd, s);
            coachEngine.setLoopPoints(cStart, cEnd);
        }

        float pitch = notch.cruiseSpeed;
        coachEngine.setSpeed(pitch);
        updateCoachVolume(pitch);
    }

    // --- Internal Engine Access ---
    public GrainEngine getLocoEngine() {
        return locoEngine;
    }

    public GrainEngine getCoachEngine() {
        return coachEngine;
    }
}
