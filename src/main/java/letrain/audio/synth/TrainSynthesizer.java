package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;

import letrain.audio.core.AudioSource;

public class TrainSynthesizer implements AudioSource {

    private GrainEngine locoEngine;
    private GrainEngine locoIdleEngine;
    private GrainEngine coachEngine;
    private GrainEngine brakeEngine;
    private GrainEngine loadEngine;
    private float filterSensitivity = 1.0f;

    // Configuration
    private SpeedNotch[] notches = new SpeedNotch[11];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0;

    // State
    private boolean isTransitioning = false;
    private Thread transitionThread;
    private boolean engineStarting = false;
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
        locoIdleEngine = new GrainEngine();
        coachEngine = new GrainEngine();

        // Defaults
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        locoIdleEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        coachEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);

        // 20% probability of flip
        locoEngine.setTurnProbability(0.2f);
        locoIdleEngine.setTurnProbability(0.2f);
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
                brakeEngine.setSpeed(1.0f); // 1.0x playback
                brakeEngine.setVolume(0.0f);
                brakeEngine.setSampleRate(44100.0f);
            }
            if (sharedLoadSample != null) {
                loadEngine.setSample(sharedLoadSample);
                loadEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
                loadEngine.setSpeed(1.0f); // 1.0x playback
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
            // Load Brakes WAV
            java.net.URL brakeUrl = getClass().getResource("/sound/train-brakes.wav");
            if (brakeUrl != null) {
                sharedBrakeSample = new AudioSample(brakeUrl);
                brakeEngine.setSample(sharedBrakeSample);
                brakeEngine.setLoopPoints(0, 1.0f); // Loop entire braking sample
                brakeEngine.setSpeed(1.0f); // 1.0x playback
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
                loadEngine.setSpeed(1.0f); // 1.0x playback rate
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

            setSample(sharedSample);
            resourcesLoaded = true;

        } catch (Exception e) {
            e.printStackTrace();
            initDefaultNotches();
        }
    }

    // Start-stop labels
    private double startStopStart = 0, startStopEnd = 0;

    private void initNotchesFromLabels(List<letrain.audio.util.AudacityLabelParser.Label> labels, AudioSample sample) {
        // Find regions
        double ralentiStart = 0, ralentiEnd = 0;
        double cruiseStart = 0, cruiseEnd = 0;
        double wagonsStart = 0, wagonsEnd = 0;

        for (letrain.audio.util.AudacityLabelParser.Label l : labels) {
            if (l.name.equalsIgnoreCase("start-stop")) {
                startStopStart = l.startTime;
                startStopEnd = l.endTime;
            } else if (l.name.equalsIgnoreCase("ralenti")) {
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
        for (int i = 0; i < 11; i++) {
            float pitch = (i == 0) ? 1.0f : 1.0f + 0.05f * (i - 1);

            // Map loop points
            float lStart = (float) ((i == 0) ? ralentiStart : cruiseStart) * 44100f;
            float lEnd = (float) ((i == 0) ? ralentiEnd : cruiseEnd) * 44100f;

            // Wagons alway same loop
            float cStart = (float) wagonsStart * 44100f;
            float cEnd = (float) wagonsEnd * 44100f;

            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch,
                    lStart, lEnd, cStart, cEnd, 2.0f); // 2.0s to ramp up/down to next notch
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

        for (int i = 0; i < 11; i++) {
            float pitch = (i == 0) ? 1.0f : 1.0f + 0.05f * (i - 1);

            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch,
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
        locoIdleEngine.read(buffer);
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
        locoIdleEngine.setSample(sample);
        coachEngine.setSample(sample);
        // Apply initial loop points for current notch
        if (sample != null) {
            SpeedNotch current = notches[currentNotchIndex];

            // If notches are not yet initialized (e.g. during construction), skip
            if (current != null) {
                if (startStopEnd > 0) {
                    float lStart = convertSamplesToNorm((float) startStopStart * 44100f, sample);
                    float lEnd = convertSamplesToNorm((float) startStopEnd * 44100f, sample);
                    engineStarting = true;

                    locoIdleEngine.setLoopPoints(lStart, lEnd);

                    new Thread(() -> {
                        try {
                            Thread.sleep((long) ((startStopEnd - startStopStart) * 1000));
                            float normStart = convertSamplesToNorm(notches[0].loopStart, sample);
                            float normEnd = convertSamplesToNorm(notches[0].loopEnd, sample);
                            locoIdleEngine.setLoopPoints(normStart, normEnd);
                            engineStarting = false;
                        } catch (Exception e) {
                        }
                    }).start();
                } else {
                    float lStart = convertSamplesToNorm(notches[0].loopStart, sample);
                    float lEnd = convertSamplesToNorm(notches[0].loopEnd, sample);
                    locoIdleEngine.setLoopPoints(lStart, lEnd);
                }

                float cStartLoco = convertSamplesToNorm(notches[1].loopStart, sample);
                float cEndLoco = convertSamplesToNorm(notches[1].loopEnd, sample);
                locoEngine.setLoopPoints(cStartLoco, cEndLoco);

                idleCrossfade = (currentNotchIndex == 0) ? 0.0f : 1.0f;
                applyLocoVolumes();

                float cStart = convertSamplesToNorm(current.coachLoopStart, sample);
                float cEnd = convertSamplesToNorm(current.coachLoopEnd, sample);

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
    private float idleCrossfade = 0.0f; // 0 = idle engine only, 1 = cruise engine only

    public void setLocoVolume(float vol) {
        this.baseLocoVolume = vol;
        applyLocoVolumes();
    }

    private void applyLocoVolumes() {
        if (locoIdleEngine != null) {
            locoIdleEngine.setVolume(baseLocoVolume * (1.0f - idleCrossfade));
        }
        if (locoEngine != null) {
            locoEngine.setVolume(baseLocoVolume * idleCrossfade);
        }
    }

    public void setCoachVolume(float vol) {
        this.baseCoachVolume = vol;
    }

    private float targetBrakeVolume = 0.0f;

    public boolean isEngineStarting() {
        return engineStarting;
    }

    public boolean isTransitioning() {
        return isTransitioning;
    }

    public void setBraking(boolean braking) {
        float newTarget = braking ? 0.8f : 0.0f;
        if (newTarget != this.targetBrakeVolume) {
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
        locoIdleEngine.setDistanceFilter(effective);
        locoEngine.setDistanceFilter(effective);
        coachEngine.setDistanceFilter(effective);
        if (brakeEngine != null) {
            brakeEngine.setDistanceFilter(effective);
        }
    }

    public void setLocoRandomness(float prob, float duration) {
        locoIdleEngine.setTurnProbability(prob);
        locoIdleEngine.setReverseDuration(duration);
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

        if (index == currentNotchIndex && !isTransitioning)
            return;

        // Detect rapid reduction to trigger braking
        if (isTransitioning && index < targetNotchIndex && index < currentNotchIndex) {
            setBraking(true);
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

                    SpeedNotch target = notches[next];

                    // Strict sequence: 1) Ramp audio pitch and blend idle/cruise engines
                    float startSpeed = locoEngine.getSpeed();
                    float targetSpeed = target.cruiseSpeed;
                    float duration = target.rampTime;

                    float startCrossfade = idleCrossfade;
                    float targetCrossfade = (next == 0) ? 0.0f : 1.0f;

                    performRampSync(startSpeed, targetSpeed, duration, startCrossfade, targetCrossfade);

                    // Strict sequence: 2) Audio completed, confirm notch
                    currentNotchIndex = next;
                    notifyNotch(next); // 3) Locomotive & UI will handle this notification
                }
            } finally {
                synchronized (this) {
                    isTransitioning = false;
                    engineStarting = false;
                    setBraking(false);
                }
            }
        });
        transitionThread.setDaemon(true);
        transitionThread.start();
    }

    private void performRampSync(float startSpeed, float targetSpeed, float durationSec, float startCrossfade,
            float targetCrossfade) {
        int interval = 33;
        int steps = (int) ((durationSec * 1000) / interval);
        if (steps < 1)
            steps = 1;

        float speedStep = (targetSpeed - startSpeed) / steps;
        float fadeStep = (targetCrossfade - startCrossfade) / steps;

        for (int i = 1; i <= steps; i++) {
            if (Thread.currentThread().isInterrupted())
                return;

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return;
            }

            float newSpeed = startSpeed + (speedStep * i);
            float newFade = startCrossfade + (fadeStep * i);

            // clamps
            if (speedStep > 0 && newSpeed > targetSpeed)
                newSpeed = targetSpeed;
            if (speedStep < 0 && newSpeed < targetSpeed)
                newSpeed = targetSpeed;

            if (newFade < 0.0f)
                newFade = 0.0f;
            if (newFade > 1.0f)
                newFade = 1.0f;

            idleCrossfade = newFade;
            applyLocoVolumes();

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

        float volumeFactor = (speed == 0) ? 0.0f : Math.min(1.0f, (float) speed / 5.0f);
        coachEngine.setVolume(baseCoachVolume * volumeFactor);
    }

    // --- Internal Engine Access ---
    public GrainEngine getLocoEngine() {
        return locoEngine;
    }

    public GrainEngine getCoachEngine() {
        return coachEngine;
    }
}
