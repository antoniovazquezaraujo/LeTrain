package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;

import letrain.audio.core.AudioSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TrainSynthesizer — motor de síntesis de sonido de tren.
 *
 * Arquitectura de un único GrainEngine para la locomotora (locoEngine),
 * más un GrainEngine de vagones (coachEngine).
 *
 * Estados:
 * OFF → sin sonido
 * STARTING → reproduce segmento 'start' una vez, luego → RALENTI
 * RALENTI → loop del segmento 'ralenti' (notch 0)
 * CRUISE_N → loop del segmento 'cruise' con pitch del notch N, con ramp entre
 * notches
 * STOPPING → reproduce segmento 'stop' una vez, luego → OFF
 */
public class TrainSynthesizer implements AudioSource {

    private static final Logger log = LoggerFactory.getLogger(TrainSynthesizer.class);

    // --- Engines ---
    private GrainEngine locoEngine; // Motor: start / ralenti / cruise / stop
    private GrainEngine coachEngine; // Vagones
    private GrainEngine brakeEngine;
    private GrainEngine loadEngine;

    private float filterSensitivity = 1.0f;

    // --- Notches (0 = ralenti, 1-10 = cruise a distintos pitchs) ---
    private SpeedNotch[] notches = new SpeedNotch[11];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0;

    private enum State {
        OFF,
        STARTING,
        IDLE,
        CRUISING,
        TRANSITIONING_UP,
        TRANSITIONING_DOWN,
        STOPPING
    }

    private State state = State.OFF;
    private float stateTimer = 0.0f;
    private long lastUpdateTime = 0;
    private Runnable onStopFinished;
    private float rampStartSpeed;
    private float rampTargetSpeed;
    private float rampDuration;
    private long rampStartTime;
    private float rampStartCoachVol;
    private float rampTargetCoachVol;

    // --- Estado de transición ---
    private boolean engineStarting = false; // bloquea movimiento durante arranque
    private boolean isStopping = false; // bloquea comandos durante apagado
    private boolean loading = false;
    private float targetLoadVolume = 0.0f;

    // --- Posición espacial ---
    private float x, y, z;
    private float refDistance = 100.0f;
    private float maxDistance = 2000.0f;

    // --- Flag activo para Mixer ---
    private boolean audioRunning = false;

    // --- Listeners ---
    private List<SynthesizerListener> listeners = new ArrayList<>();

    public interface SynthesizerListener {
        void onSpeedUpdate(float displaySpeed);

        void onNotchChanged(int notchIndex);
    }

    // --- Volúmenes base ---
    private float baseLocoVolume = 1.0f;
    private float baseCoachVolume = 1.0f;

    // --- Segmentos (en segundos, de las labels) ---
    private double startSegStart = 0, startSegEnd = 0;
    private double stopSegStart = 0, stopSegEnd = 0;
    private double ralentiStart = 0, ralentiEnd = 0;
    private double cruiseStart = 0, cruiseEnd = 0;
    private double wagonsStart = 0, wagonsEnd = 0;

    // =====================================================================
    // Constructor
    // =====================================================================

    public TrainSynthesizer() {
        locoEngine = new GrainEngine();
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        locoEngine.setTurnProbability(0.15f); // random reverse
        locoEngine.setReverseDuration(1.5f);

        coachEngine = new GrainEngine();
        coachEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        coachEngine.setTurnProbability(0.1f);
        coachEngine.setReverseDuration(2.0f);

        brakeEngine = new GrainEngine();
        brakeEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
        brakeEngine.setTurnProbability(0f);

        loadEngine = new GrainEngine();
        loadEngine.setLoopMode(GrainEngine.LoopMode.WRAP);
        loadEngine.setTurnProbability(0f);

        loadResources();
    }

    // =====================================================================
    // Carga de recursos (estáticos compartidos)
    // =====================================================================

    private static AudioSample sharedSample;
    private static AudioSample sharedBrakeSample;
    private static AudioSample sharedLoadSample;
    private static boolean resourcesLoaded = false;
    private static List<letrain.audio.util.AudacityLabelParser.Label> sharedLabels;

    private void loadResources() {
        if (resourcesLoaded && sharedSample != null) {
            applySharedResources();
            return;
        }
        try {
            // Locomotora principal
            java.net.URL wavUrl = getClass().getResource("/sound/train-sound.wav");
            if (wavUrl == null) {
                log.error("train-sound.wav not found on classpath");
                initDefaultNotches();
                return;
            }
            sharedSample = new AudioSample(wavUrl);

            // Frenos
            java.net.URL brakeUrl = getClass().getResource("/sound/train-brakes.wav");
            if (brakeUrl != null) {
                sharedBrakeSample = new AudioSample(brakeUrl);
            }

            // Carga/descarga
            java.net.URL loadUrl = getClass().getResource("/sound/load-unload.wav");
            if (loadUrl != null) {
                sharedLoadSample = new AudioSample(loadUrl);
            }

            // Labels
            java.io.InputStream labelsStream = getClass().getResourceAsStream("/sound/train-sound-labels.txt");
            if (labelsStream != null) {
                sharedLabels = letrain.audio.util.AudacityLabelParser.parse(labelsStream);
            } else {
                log.warn("train-sound-labels.txt not found, using defaults");
            }

            resourcesLoaded = true;
        } catch (Exception e) {
            log.error("Error loading train audio resources", e);
        }

        applySharedResources();
    }

    /**
     * Permite inyectar un AudioSample externo (usado por TestSynth).
     * Actualiza los engines y re-inicializa los notches con las labels existentes.
     */
    public void setSample(AudioSample sample) {
        sharedSample = sample;
        if (sharedLabels != null) {
            initNotchesFromLabels(sharedLabels, sample);
        } else {
            buildNotches(sample);
        }
        locoEngine.setSample(sample);
        locoEngine.setSampleRate(sample.getSampleRate());
        coachEngine.setSample(sample);
        coachEngine.setSampleRate(sample.getSampleRate());
        applyLoopForNotch(currentNotchIndex);
        locoEngine.setSpeed(notches[currentNotchIndex].cruiseSpeed);
    }

    private void applySharedResources() {
        if (sharedSample == null) {
            initDefaultNotches();
            return;
        }

        // Inicializar engines con el sample compartido
        locoEngine.setSample(sharedSample);
        locoEngine.setSampleRate(sharedSample.getSampleRate());

        coachEngine.setSample(sharedSample);
        coachEngine.setSampleRate(sharedSample.getSampleRate());

        if (sharedBrakeSample != null) {
            brakeEngine.setSample(sharedBrakeSample);
            brakeEngine.setLoopPoints(0f, 1.0f);
            brakeEngine.setSpeed(1.0f);
            brakeEngine.setVolume(0.0f);
            brakeEngine.setSampleRate(sharedBrakeSample.getSampleRate());
        }
        if (sharedLoadSample != null) {
            loadEngine.setSample(sharedLoadSample);
            loadEngine.setLoopPoints(0f, 1.0f);
            loadEngine.setSpeed(1.0f);
            loadEngine.setVolume(0.0f);
            loadEngine.setSampleRate(sharedLoadSample.getSampleRate());
        }

        // Parsear labels e inicializar notches
        if (sharedLabels != null) {
            initNotchesFromLabels(sharedLabels, sharedSample);
        } else {
            initDefaultNotches();
        }

        // Arrancar en estado ralentí (silencioso, volumen 0 hasta que se active)
        applyLoopForNotch(0);
        locoEngine.setSpeed(notches[0].cruiseSpeed);
        locoEngine.setVolume(0f); // silencioso hasta startAudio()
        coachEngine.setVolume(0f);
    }

    // =====================================================================
    // Inicialización de notches
    // =====================================================================

    private void initNotchesFromLabels(
            List<letrain.audio.util.AudacityLabelParser.Label> labels,
            AudioSample sample) {

        for (letrain.audio.util.AudacityLabelParser.Label l : labels) {
            switch (l.name.toLowerCase()) {
                case "start":
                    startSegStart = l.startTime;
                    startSegEnd = l.endTime;
                    break;
                case "stop":
                    stopSegStart = l.startTime;
                    stopSegEnd = l.endTime;
                    break;
                case "ralenti":
                    ralentiStart = l.startTime;
                    ralentiEnd = l.endTime;
                    break;
                case "cruise":
                    cruiseStart = l.startTime;
                    cruiseEnd = l.endTime;
                    break;
                case "wagons":
                    wagonsStart = l.startTime;
                    wagonsEnd = l.endTime;
                    break;
            }
        }

        buildNotches(sample);
    }

    /**
     * Pitchs: notch 0 = ralenti (1.0), notch 1..10 = cruise a distintas
     * velocidades.
     * Rango 0.7 → 1.5 (80% de rango tonal, claramente audible).
     */
    private void buildNotches(AudioSample sample) {
        float sampleRate = sample.getSampleRate();

        // Notch 0 — ralenti
        float rStart = (float) ralentiStart * sampleRate;
        float rEnd = (float) ralentiEnd * sampleRate;
        float cStart = (float) cruiseStart * sampleRate;
        float cEnd = (float) cruiseEnd * sampleRate;
        float wStart = (float) wagonsStart * sampleRate;
        float wEnd = (float) wagonsEnd * sampleRate;

        notches[0] = new SpeedNotch("Ralenti", 1.0f, 1.0f, 1.0f,
                rStart, rEnd, wStart, wEnd, 2.0f);

        // Notchs 1-10 — pitch range 1.1 → 2.0
        for (int i = 1; i <= 10; i++) {
            float pitch = 1.1f + (i - 1) * (0.9f / 9f); // 1.10 … 2.00
            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch,
                    cStart, cEnd, wStart, wEnd, 2.0f);
        }
    }

    private void initDefaultNotches() {
        float locoStart = 7302f, locoEnd = 21125f;
        float coachStart = 95715f, coachEnd = 204992f;
        for (int i = 0; i < 11; i++) {
            float pitch = (i == 0) ? 1.0f : 1.1f + (i - 1) * (0.9f / 9f);
            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch,
                    locoStart, locoEnd, coachStart, coachEnd, 2.0f);
        }
        notches[0].name = "Ralenti";

        // Defaults for start/stop if labels fail
        ralentiStart = locoStart / 44100.0;
        ralentiEnd = locoEnd / 44100.0;
        startSegStart = 0;
        startSegEnd = ralentiStart;
        stopSegStart = ralentiEnd;
        stopSegEnd = sharedSample != null ? sharedSample.getLength() / sharedSample.getSampleRate() : ralentiEnd + 1.0;
    }

    // =====================================================================
    // Loop points
    // =====================================================================

    /** Aplica los puntos de bucle del notch N al locoEngine. */
    private void applyLoopForNotch(int notchIdx) {
        if (sharedSample == null)
            return;
        SpeedNotch n = notches[notchIdx];
        float ls = convertSamplesToNorm(n.loopStart, sharedSample);
        float le = convertSamplesToNorm(n.loopEnd, sharedSample);
        locoEngine.setLoopPoints(ls, le);
    }

    /** Aplica puntos de bucle en segundos (p.ej. start/stop). */
    private void applyLoopSeconds(double startSec, double endSec) {
        if (sharedSample == null)
            return;
        float rate = sharedSample.getSampleRate();
        float ls = convertSamplesToNorm((float) (startSec * rate), sharedSample);
        float le = convertSamplesToNorm((float) (endSec * rate), sharedSample);
        locoEngine.setLoopPoints(ls, le);
    }

    // =====================================================================
    // AudioSource impl
    // =====================================================================

    @Override
    public boolean read(float[] buffer) {
        if (state == State.OFF)
            return false;
        if (!audioRunning)
            return false;
        updateBrakeVolume();
        locoEngine.read(buffer);
        coachEngine.read(buffer);
        if (brakeEngine != null)
            brakeEngine.read(buffer);
        updateLoadVolume();
        if (loadEngine != null)
            loadEngine.read(buffer);
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

    // =====================================================================
    // Ciclo de vida del motor (encendido / apagado)
    // =====================================================================

    public void startAudio() {
        if (state != State.OFF)
            return;
        audioRunning = true;

        if (startSegEnd > startSegStart && sharedSample != null) {
            log.info("Starting engine engine: sequence [{}, {}]", startSegStart, startSegEnd);
            locoEngine.resetState();
            locoEngine.setLoopMode(GrainEngine.LoopMode.PLAY_ONCE);
            locoEngine.setTurnProbability(0f); // sin random durante arranque
            applyLoopSeconds(startSegStart, startSegEnd);
            locoEngine.seekSamples(startSegStart * sharedSample.getSampleRate());
            locoEngine.setSpeed(1.0f);
            locoEngine.setVolume(baseLocoVolume);
            state = State.STARTING;
            stateTimer = (float) (startSegEnd - startSegStart);
            engineStarting = true;
        } else {
            // Sin segmento start: ir directamente a ralenti
            applyLoopForNotch(0);
            locoEngine.setSpeed(notches[0].cruiseSpeed);
            locoEngine.setVolume(baseLocoVolume);
            state = State.IDLE;
        }

        coachEngine.setVolume(0f);
    }

    public void stopAudio() {
        audioRunning = false;
        locoEngine.setVolume(0f);
        coachEngine.setVolume(0f);
    }

    /**
     * Reproduce el segmento STOP una vez y luego llama a onFinished.
     */
    public void playStopSound(Runnable onFinished) {
        if (state == State.STOPPING)
            return;
        if (stopSegEnd <= stopSegStart || sharedSample == null) {
            audioRunning = false;
            state = State.OFF;
            if (onFinished != null)
                onFinished.run();
            return;
        }

        isStopping = true;
        state = State.STOPPING;
        stateTimer = (float) (stopSegEnd - stopSegStart);
        this.onStopFinished = onFinished;

        log.info("Stopping engine: segment [{}, {}]", stopSegStart, stopSegEnd);

        locoEngine.resetState();
        locoEngine.setLoopMode(GrainEngine.LoopMode.PLAY_ONCE);
        locoEngine.setTurnProbability(0f);
        applyLoopSeconds(stopSegStart, stopSegEnd);
        locoEngine.seekSamples(stopSegStart * sharedSample.getSampleRate());
        locoEngine.setSpeed(1.0f);
        locoEngine.setVolume(baseLocoVolume);
        coachEngine.setVolume(0f);
    }

    // =====================================================================
    // Volúmenes y filtros
    // =====================================================================

    public void setLocoVolume(float vol) {
        this.baseLocoVolume = vol;
        if (audioRunning)
            locoEngine.setVolume(vol);
    }

    public void setCoachVolume(float vol) {
        this.baseCoachVolume = vol;
    }

    private float targetBrakeVolume = 0.0f;

    public void setBraking(boolean braking) {
        this.targetBrakeVolume = braking ? 0.8f : 0.0f;
    }

    private void updateBrakeVolume() {
        if (brakeEngine == null)
            return;
        float cur = brakeEngine.getVolume();
        float step = (targetBrakeVolume > cur) ? 0.02f : 0.01f;
        if (Math.abs(cur - targetBrakeVolume) < 0.01f)
            brakeEngine.setVolume(targetBrakeVolume);
        else
            brakeEngine.setVolume(cur + (targetBrakeVolume > cur ? step : -step));
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        this.targetLoadVolume = loading ? 0.7f : 0.0f;
    }

    public boolean isLoading() {
        return loading;
    }

    private void updateLoadVolume() {
        if (loadEngine == null)
            return;
        float cur = loadEngine.getVolume();
        float step = (targetLoadVolume > cur) ? 0.05f : 0.02f;
        if (Math.abs(cur - targetLoadVolume) < 0.01f)
            loadEngine.setVolume(targetLoadVolume);
        else
            loadEngine.setVolume(cur + (targetLoadVolume > cur ? step : -step));
    }

    public void setFilterSensitivity(float s) {
        this.filterSensitivity = s;
    }

    @Override
    public void setDistanceFilter(float amount) {
        float eff = Math.min(0.99f, amount * filterSensitivity);
        locoEngine.setDistanceFilter(eff);
        coachEngine.setDistanceFilter(eff);
        if (brakeEngine != null)
            brakeEngine.setDistanceFilter(eff);
    }

    public void setLocoRandomness(float prob, float duration) {
        locoEngine.setTurnProbability(prob);
        locoEngine.setReverseDuration(duration);
    }

    public void setCoachRandomness(float prob, float duration) {
        coachEngine.setTurnProbability(prob);
        coachEngine.setReverseDuration(duration);
    }

    // =====================================================================
    // Notches
    // =====================================================================

    public void setNotch(int index, SpeedNotch notch) {
        if (index >= 0 && index < notches.length)
            notches[index] = notch;
    }

    public SpeedNotch getNotch(int index) {
        return (index >= 0 && index < notches.length) ? notches[index] : null;
    }

    public SpeedNotch[] getNotches() {
        return notches;
    }

    // =====================================================================
    // Listeners
    // =====================================================================

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

    // =====================================================================
    // Control de aceleración / desaceleración
    // =====================================================================

    public boolean isEngineStarting() {
        return engineStarting;
    }

    public boolean isTransitioning() {
        return state == State.TRANSITIONING_UP || state == State.TRANSITIONING_DOWN;
    }

    /**
     * Solicita cambiar al notch 'index'.
     * Si ya hay transición en curso, actualiza el target y deja que el hilo
     * la detecte en el siguiente paso.
     */
    /**
     * Fuerza una transición inmediata a ralentí (notch 0), saltándose cualquier
     * rampa en curso. Se usa cuando un tren choca o llega a un fin de vía.
     */
    public synchronized void forceIdle() {
        if (state == State.OFF || state == State.STOPPING)
            return;

        currentNotchIndex = 0;
        targetNotchIndex = 0;
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        locoEngine.setTurnProbability(0.15f);
        applyLoopForNotch(0);
        locoEngine.setSpeed(notches[0].cruiseSpeed);
        engineStarting = false;
        state = State.IDLE;
        setBraking(false);
        notifyNotch(0);
    }

    public synchronized void setThrottle(int index) {
        if (state == State.STOPPING || state == State.STARTING)
            return;
        if (index < 0 || index >= notches.length)
            return;
        if (index == targetNotchIndex)
            return;

        if (isTransitioning() && index < targetNotchIndex && index < currentNotchIndex) {
            setBraking(true);
        }

        targetNotchIndex = index;

        if (state == State.IDLE || state == State.CRUISING) {
            startTransition();
        }
    }

    private void startTransition() {
        if (currentNotchIndex == targetNotchIndex) {
            state = (currentNotchIndex == 0) ? State.IDLE : State.CRUISING;
            return;
        }

        int nextNotch = currentNotchIndex + (targetNotchIndex > currentNotchIndex ? 1 : -1);
        state = (targetNotchIndex > currentNotchIndex) ? State.TRANSITIONING_UP : State.TRANSITIONING_DOWN;

        SpeedNotch target = notches[nextNotch];
        rampStartSpeed = locoEngine.getSpeed();
        rampTargetSpeed = target.cruiseSpeed;
        rampDuration = target.rampTime;
        rampStartTime = System.nanoTime();

        rampStartCoachVol = coachEngine.getVolume();
        rampTargetCoachVol = (nextNotch > 0) ? baseCoachVolume : 0.0f;
    }

    public void update() {
        if (state == State.OFF)
            return;

        if (lastUpdateTime == 0) {
            lastUpdateTime = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        float deltaTime = (now - lastUpdateTime) / 1_000_000_000.0f;
        lastUpdateTime = now;

        switch (state) {
            case STARTING:
                stateTimer -= deltaTime;
                if (stateTimer <= 0) {
                    locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
                    locoEngine.setTurnProbability(0.15f);
                    applyLoopForNotch(0);
                    locoEngine.setSpeed(notches[0].cruiseSpeed);
                    engineStarting = false;
                    state = State.IDLE;
                    if (targetNotchIndex != currentNotchIndex) {
                        startTransition();
                    }
                }
                break;

            case STOPPING:
                stateTimer -= deltaTime;
                if (stateTimer <= 0) {
                    audioRunning = false;
                    isStopping = false;
                    state = State.OFF;
                    if (onStopFinished != null) {
                        onStopFinished.run();
                        onStopFinished = null;
                    }
                }
                break;

            case TRANSITIONING_UP:
            case TRANSITIONING_DOWN:
                long elapsed = now - rampStartTime;
                float progress = (float) elapsed / (rampDuration * 1_000_000_000.0f);

                if (progress >= 1.0f) {
                    locoEngine.setSpeed(rampTargetSpeed);
                    coachEngine.setVolume(rampTargetCoachVol);

                    int nextNotch = currentNotchIndex + (state == State.TRANSITIONING_UP ? 1 : -1);

                    if (nextNotch > 0 && currentNotchIndex == 0) {
                        applyLoopForNotch(nextNotch);
                    } else if (nextNotch == 0) {
                        applyLoopForNotch(0);
                    }

                    currentNotchIndex = nextNotch;
                    notifyNotch(currentNotchIndex);

                    if (currentNotchIndex == targetNotchIndex) {
                        state = (currentNotchIndex == 0) ? State.IDLE : State.CRUISING;
                        setBraking(false);
                    } else {
                        startTransition();
                    }
                } else {
                    float newSpeed = rampStartSpeed + (rampTargetSpeed - rampStartSpeed) * progress;
                    locoEngine.setSpeed(newSpeed);
                    float newCoachVol = rampStartCoachVol + (rampTargetCoachVol - rampStartCoachVol) * progress;
                    coachEngine.setVolume(newCoachVol);
                }
                break;
        }
    }

    /**
     * Rampa de pitch durante durationSec segundos.
     * Simultáneamente notifica la velocidad para que la locomotora y la palanca se
     * actualicen.
     */
    private void performRampSync(float startSpeed, float targetSpeed, float durationSec) {
        /*
         * int interval = 33; // ms (~30fps)
         * int steps = Math.max(1, (int)((durationSec * 1000) / interval));
         * float speedStep = (targetSpeed - startSpeed) / steps;
         * 
         * // Para los vagones: volumen proporcional a la velocidad
         * float startCoachVol = coachEngine.getVolume();
         * float targetCoachVol = (targetNotchIndex > 0) ? baseCoachVolume : 0.0f;
         * float coachStep = (targetCoachVol - startCoachVol) / steps;
         * 
         * for (int i = 1; i <= steps; i++) {
         * if (Thread.currentThread().isInterrupted()) return;
         * try { Thread.sleep(interval); } catch (InterruptedException e) { return; }
         * 
         * float newSpeed = startSpeed + speedStep * i;
         * // Clamp
         * if (speedStep > 0 && newSpeed > targetSpeed) newSpeed = targetSpeed;
         * if (speedStep < 0 && newSpeed < targetSpeed) newSpeed = targetSpeed;
         * 
         * locoEngine.setSpeed(newSpeed);
         * notifySpeed(newSpeed);
         * 
         * // Actualizar volumen de vagones proporcionalmente
         * float newCoachVol = startCoachVol + coachStep * i;
         * if (newCoachVol < 0) newCoachVol = 0;
         * if (newCoachVol > baseCoachVolume) newCoachVol = baseCoachVolume;
         * coachEngine.setVolume(newCoachVol);
         * }
         */
    }

    /** Actualiza el loopPoint de los vagones según velocidad de movimiento. */
    public void setMotionSpeed(int speed) {
        if (isStopping)
            return;
        if (sharedSample == null)
            return;
        // Los vagones usan siempre el mismo segmento pero con volumen proporcional a la
        // velocidad.
        // El loop ya está configurado en los notches.
        if (notches[0] == null)
            return;
        float wStart = convertSamplesToNorm(notches[0].coachLoopStart, sharedSample);
        float wEnd = convertSamplesToNorm(notches[0].coachLoopEnd, sharedSample);
        coachEngine.setLoopPoints(wStart, wEnd);
        coachEngine.setSpeed(1.0f + speed * 0.05f); // mismo rango que los notchs del loco

        // El volumen se gestiona en performRampSync; aquí solo si el tren está parado
        if (speed == 0 && !isTransitioning()) {
            coachEngine.setVolume(0f);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private float convertSamplesToNorm(float samples, AudioSample sample) {
        if (sample == null || sample.getLength() == 0)
            return 0.0f;
        return samples / sample.getLength();
    }

    // --- Acceso a engines para debug ---
    public GrainEngine getLocoEngine() {
        return locoEngine;
    }

    public GrainEngine getCoachEngine() {
        return coachEngine;
    }
}
