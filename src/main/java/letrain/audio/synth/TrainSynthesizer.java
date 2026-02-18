package letrain.audio.synth;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class TrainSynthesizer {

    private GrainEngine locoEngine;
    private GrainEngine coachEngine;

    // Configuration
    private SpeedNotch[] notches = new SpeedNotch[10];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0; // Track desired notch to avoid restarting transition repeatedly

    // State
    private boolean isTransitioning = false;
    private Thread transitionThread;
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

        // Init default notches
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
                cruise = 0.05f; // Was 0.0f, which caused it to sound like Idle
                end = 0.05f;
            } else {
                // i >= 2
                // Notch 2: 5, 10, 15 -> 0.05, 0.10, 0.15
                // Notch 3: 15, 20, 25 -> 0.15, 0.20, 0.25
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

    // --- Audio Control ---
    public void startAudio() {
        if (audioRunning)
            return;
        audioRunning = true;
        new Thread(this::audioLoop).start();
    }

    public void stopAudio() {
        audioRunning = false;
    }

    public void setSample(AudioSample sample) {
        locoEngine.setSample(sample);
        coachEngine.setSample(sample);
        // Apply initial loop points for current notch
        if (sample != null) {
            SpeedNotch current = notches[currentNotchIndex];

            float lStart = convertMsToNorm(current.loopStart, sample);
            float lEnd = convertMsToNorm(current.loopEnd, sample);
            float cStart = convertMsToNorm(current.coachLoopStart, sample);
            float cEnd = convertMsToNorm(current.coachLoopEnd, sample);

            locoEngine.setLoopPoints(lStart, lEnd);
            coachEngine.setLoopPoints(cStart, cEnd);
        }
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
        locoEngine.setVolume(vol); // Loco volume is constant relative to speed for now
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

        System.err.println("TrainSynthesizer: setThrottle " + currentNotchIndex + " -> " + index);

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
        // If interrupting, startSpeed1 is whatever speed we are at.

        float targetSpeed1 = isUpshift ? current.endSpeed : current.startSpeed;

        float duration1 = current.rampTime / 2.0f;

        runRamp(startSpeed1, targetSpeed1, duration1, () -> {

            // PHASE 2: ENTER NEW NOTCH
            currentNotchIndex = index;
            notifyNotch(index); // Update Table selection etc

            // Switch Loops
            // Normalize loop points
            AudioSample s = locoEngine.getSample();
            float lStart = convertMsToNorm(target.loopStart, s);
            float lEnd = convertMsToNorm(target.loopEnd, s);
            float cStart = convertMsToNorm(target.coachLoopStart, s);
            float cEnd = convertMsToNorm(target.coachLoopEnd, s);

            System.err.println("TrainSynthesizer: Notch " + index + " Loops Norm: " + lStart + "-" + lEnd);

            locoEngine.setLoopPoints(lStart, lEnd);
            coachEngine.setLoopPoints(cStart, cEnd);

            float startSpeed2 = isUpshift ? target.startSpeed : target.endSpeed;
            float targetSpeed2 = target.cruiseSpeed;

            System.err.println("TrainSynthesizer: Phase 2 Target Speed: " + targetSpeed2);

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
        // Handle interruption of PREVIOUS thread if it is not the current one
        // (chaining)
        if (transitionThread != null && transitionThread.isAlive() && transitionThread != Thread.currentThread()) {
            transitionThread.interrupt();
            try {
                // Optional: wait for it to die?
                transitionThread.join(100);
            } catch (InterruptedException e) {
                // ignore
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

    // --- Audio Thread ---
    private void audioLoop() {
        try {
            float sampleRate = 44100.0f;
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            locoEngine.setSampleRate(sampleRate);
            coachEngine.setSampleRate(sampleRate);

            int bufferSize = 4096;
            float[] tempBuffer1 = new float[bufferSize];
            float[] tempBuffer2 = new float[bufferSize];
            byte[] byteBuffer = new byte[bufferSize * 2];

            while (audioRunning) {
                for (int i = 0; i < bufferSize; i++) {
                    tempBuffer1[i] = 0;
                    tempBuffer2[i] = 0;
                }

                locoEngine.read(tempBuffer1);
                coachEngine.read(tempBuffer2);

                for (int i = 0; i < bufferSize; i++) {
                    float mixed = tempBuffer1[i] + tempBuffer2[i];
                    if (mixed > 1.0f)
                        mixed = 1.0f;
                    if (mixed < -1.0f)
                        mixed = -1.0f;

                    short s = (short) (mixed * 32767.0f);
                    byteBuffer[i * 2] = (byte) ((s >> 8) & 0xFF);
                    byteBuffer[i * 2 + 1] = (byte) (s & 0xFF);
                }
                line.write(byteBuffer, 0, byteBuffer.length);
            }
            line.drain();
            line.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
}
