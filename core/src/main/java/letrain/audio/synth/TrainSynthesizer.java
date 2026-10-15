package letrain.audio.synth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import letrain.audio.core.AudioSource;
import letrain.audio.material.LoopPoints;
import letrain.audio.material.MaterialBank;
import letrain.audio.material.MaterialId;
import letrain.audio.material.MaterialId.Role;
import letrain.audio.material.MaterialProfile;
import letrain.audio.material.MaterialRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TrainSynthesizer — motor de síntesis de sonido de tren.
 *
 * <p>
 * Arquitectura de un único GrainEngine para la locomotora (locoEngine), más un GrainEngine de
 * vagones (coachEngine), uno de frenos, uno de carga y uno de transiciones (transEngine, quinta
 * voz, todavía sin material: lo alimenta la PR D).
 *
 * <p>
 * Estados: OFF → sin sonido STARTING → reproduce segmento 'start' una vez, luego → RALENTI RALENTI
 * → loop del segmento 'ralenti' (notch 0) CRUISE_N → loop del segmento 'cruise' con pitch del notch
 * N, con ramp entre notches STOPPING → reproduce segmento 'stop' una vez, luego → OFF
 *
 * <p>
 * Desde la PR C de ADR-029 el synth también posee un bus de mezcla de {@link Voice}s (loco, coach,
 * brake, load, trans) y un selector {@link SoundMode}. {@link SoundMode#LEGACY} ejecuta el código
 * de siempre sin cambios; {@link SoundMode#MATERIAL} usa los dos motores de locomotora como pareja
 * de voces: una suena el loop/one-shot actual y la otra queda silenciosa para preparar el siguiente
 * sample (regla "nunca cambiar el sample de una voz con gain &gt; 0").
 *
 * <p>
 * PR D (integración event-driven): {@link #AUTO} es el modo por defecto, {@code AudioController}
 * reporta {@code Locomotive.currentSpeed} con {@link #setCurrentNotch(int)} junto al target, y el
 * synth resuelve el snapshot en un único punto por frame ({@link #updateState}). La selección usa
 * {@link TransitionPlanner} (cadena de fallbacks de ADR-029 §3), coalescing de 400 ms, fade-in de
 * onset, crossfade al loop destino y corte corto cuando la física abandona el tramo (ADR-029 §4).
 */
public class TrainSynthesizer implements AudioSource {

    private static final Logger log = LoggerFactory.getLogger(TrainSynthesizer.class);

    /** System property that selects the train sound mode: {@code auto|material|legacy}. */
    public static final String SOUND_MODE_PROPERTY = "letrain.audio.trainSound";

    /**
     * Mode used when the property is absent or invalid. PR D flips the default to
     * {@link SoundMode#AUTO}: material when the generic profile declares notch material, legacy
     * otherwise (ADR-029 §3).
     */
    private static final SoundMode DEFAULT_SOUND_MODE = SoundMode.AUTO;

    /** ADR-029 §4 onset fade-in for one-shot transitions (range 10-20 ms). */
    static final float ONSET_FADE_SECONDS = 0.015f;

    /** ADR-029 §4 crossfade to a loop or between voices (range 150-300 ms). */
    static final float CROSSFADE_SECONDS = 0.200f;

    /** ADR-029 §4 cut when the physical state abandons the segment (range 50-150 ms). */
    static final float CUT_SECONDS = 0.100f;

    /** ADR-029 §2 debounce window for the {@code currentNotch != targetNotch} edge. */
    public static final float COALESCE_WINDOW_SECONDS = 0.400f;

    /**
     * Audited pitch ratio between adjacent notches (f0 39,95 -> 110,25 Hz over 9 steps ≈ 1,12x,
     * ADR-029 §5). Only used by the emergency loop fallback when neither the notch material nor the
     * legacy segment exists.
     */
    static final float NOTCH_PITCH_RATIO = 1.12f;

    /**
     * Sound engine selection (ADR-029).
     *
     * <ul>
     * <li>{@code AUTO}: MATERIAL when the profile declares at least one {@code notch.*} material,
     * LEGACY otherwise. Default since PR D.</li>
     * <li>{@code MATERIAL}: event-driven material engine (notch loops, ordered transitions,
     * level-3/4 fallbacks).</li>
     * <li>{@code LEGACY}: today's {@code train-sound.wav} behaviour, unchanged.</li>
     * </ul>
     */
    public enum SoundMode {
        AUTO, MATERIAL, LEGACY
    }

    // --- Engines ---
    private GrainEngine locoEngine; // Motor: start / ralenti / cruise / stop
    private GrainEngine coachEngine; // Vagones
    private GrainEngine brakeEngine;
    private GrainEngine loadEngine;
    private GrainEngine transEngine; // Quinta voz: one-shots de transición (PR D)

    // --- Bus de mezcla (ADR-029 §2/§4, modo MATERIAL) ---
    private Voice locoVoice;
    private Voice coachVoice;
    private Voice brakeVoice;
    private Voice loadVoice;
    private Voice transVoice;

    private float filterSensitivity = 1.0f;

    // --- Notches (0 = ralenti, 1-10 = cruise a distintos pitchs) ---
    private SpeedNotch[] notches = new SpeedNotch[11];
    private int currentNotchIndex = 0;
    private int targetNotchIndex = 0;

    private enum State {
        OFF, STARTING, IDLE, CRUISING, TRANSITIONING_UP, TRANSITIONING_DOWN, STOPPING, LOAD_ONLY
    }

    private State state = State.OFF;
    private float stateTimer = 0.0f;
    private long lastUpdateTime = 0;
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

    /**
     * Master gain applied to the final mixed buffer of every engine. Volatile because it is written
     * from the game/render thread (pause mute) and read from the audio mixer thread. Set to 0 to
     * silence the whole synthesizer reversibly (paused editing); back to 1 to resume.
     */
    private volatile float masterVolume = 1.0f;

    /** Mutes/unmutes the whole synthesizer via {@link #masterVolume}. */
    public void setMasterVolume(float masterVolume) {
        this.masterVolume = Math.max(0f, Math.min(1f, masterVolume));
    }

    // --- Modo de sonido (ADR-029, PR C) ---
    /** Injected bank for tests; a null value falls back to {@link MaterialBank#shared()}. */
    private final MaterialBank materialBank;
    private SoundMode soundMode;

    // --- Motor event-driven de MATERIAL (ADR-029 §2/§3/§4, PR D) ---
    /**
     * True when the material engine drives the locomotive: {@link SoundMode#MATERIAL} with at least
     * one declared material. An explicitly requested MATERIAL mode over an empty profile
     * degenerates to the legacy paths (nothing can be selected).
     */
    private boolean materialActive;

    /**
     * Physical notch reported by {@link #setCurrentNotch(int)} ({@code Locomotive.currentSpeed}).
     */
    private int materialCurrentNotch = 0;

    /** Notch seen by the previous material evaluation, to detect physical step completions. */
    private int lastEvaluatedNotch = 0;

    /** Set by the snapshot setters; consumed once per frame by the material evaluation. */
    private boolean materialSnapshotDirty = false;

    /** Voice currently sounding the engine (loop or one-shot); the other one is the spare. */
    private Voice engineVoice;
    private Voice spareVoice;

    /** Notch of the loop loaded on the engine voice, or -1 when it carries a one-shot. */
    private int engineLoopNotch = -1;

    /** Loop handover deferred because neither engine voice could swap its sample yet. */
    private int pendingLoopNotch = -1;

    /** One-shot stage in flight; null while a loop/wait state owns the engine. */
    private TransitionPlanner.Step activeStage;
    private float stageElapsed = 0f;
    private float stageDuration = 0f;

    /** One-shot stage waiting for a silent voice to swap its sample. */
    private TransitionPlanner.Step pendingStage;

    /** Coalescing timer for a fresh {@code currentNotch != targetNotch} edge. */
    private float pendingStepSeconds = 0f;

    /** True while a transition sequence is under way (including the wait-for-step gaps). */
    private boolean riding = false;

    /** True when no one-shot sounds but a physical step is still pending (wait loop). */
    private boolean waitingForStep = false;

    /** True while the material 'start' one-shot runs the STARTING lifecycle. */
    private boolean materialStartPlaying = false;

    /** Stop one-shot deferred because both engine voices were mid-fade when the engine stopped. */
    private AudioSample pendingStopSample;
    private double pendingStopGain;

    /** Start one-shot deferred because both engine voices were mid-fade when the engine started. */
    private AudioSample pendingStartSample;
    private double pendingStartGain;

    /** Resolved loop audio: material, nearest material (re-pitched) or legacy segment. */
    private record LoopSource(AudioSample sample, float loopStart, float loopEnd, float speed,
            double gainDb, int notch, boolean legacy) {
    }

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
        this(resolveModeFromProperty(), null);
    }

    /** Test/integration seam: fixes the sound mode instead of reading the system property. */
    public TrainSynthesizer(SoundMode requestedMode) {
        this(requestedMode, null);
    }

    /**
     * Full constructor: injects a material bank (tests) and resolves {@link SoundMode#AUTO} against
     * its profile. A null bank falls back to {@link MaterialBank#shared()} when needed.
     */
    TrainSynthesizer(SoundMode requestedMode, MaterialBank materialBank) {
        this.materialBank = materialBank;
        this.soundMode = resolveMode(requestedMode != null ? requestedMode : DEFAULT_SOUND_MODE);
        this.materialActive = resolveMaterialActive();

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

        transEngine = new GrainEngine();
        transEngine.setLoopMode(GrainEngine.LoopMode.PLAY_ONCE);
        transEngine.setTurnProbability(0f);

        locoVoice = new Voice("loco", locoEngine);
        coachVoice = new Voice("coach", coachEngine);
        brakeVoice = new Voice("brake", brakeEngine);
        loadVoice = new Voice("load", loadEngine);
        transVoice = new Voice("trans", transEngine);

        loadResources();

        // The mix bus is the only gain stage in MATERIAL mode: the legacy sub-voices (coach, brake,
        // load) pass through at gain 1 and their engines keep shaping volume as today. The two
        // locomotive voices start silent because the material engine fades them in/out per event;
        // in LEGACY they stay at gain 1 (readLegacy bypasses the voices anyway, the mix bus is
        // byte-identical to the direct engine sum).
        locoVoice.setGain(materialActive ? 0.0f : 1.0f);
        coachVoice.setGain(1.0f);
        brakeVoice.setGain(1.0f);
        loadVoice.setGain(1.0f);
        engineVoice = locoVoice;
        spareVoice = transVoice;
    }

    /**
     * True when the material engine can select something: MATERIAL mode over a non-empty profile.
     */
    private boolean resolveMaterialActive() {
        return soundMode == SoundMode.MATERIAL && !materialBank().profile().materials().isEmpty();
    }

    // =====================================================================
    // Modo de sonido
    // =====================================================================

    /** Effective mode after resolving {@link SoundMode#AUTO}. */
    public SoundMode getSoundMode() {
        return soundMode;
    }

    /** Resolves the mode from the {@link #SOUND_MODE_PROPERTY} system property. */
    public static SoundMode resolveModeFromProperty() {
        return resolveModeFromProperty(System.getProperty(SOUND_MODE_PROPERTY));
    }

    /** Pure property parser, visible for tests; null/blank/unknown values use the default mode. */
    static SoundMode resolveModeFromProperty(String value) {
        if (value == null) {
            return DEFAULT_SOUND_MODE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "auto":
                return SoundMode.AUTO;
            case "material":
                return SoundMode.MATERIAL;
            case "legacy":
                return SoundMode.LEGACY;
            default:
                if (!normalized.isEmpty()) {
                    log.warn("unknown {}='{}'; using {}", SOUND_MODE_PROPERTY, value,
                            DEFAULT_SOUND_MODE);
                }
                return DEFAULT_SOUND_MODE;
        }
    }

    private SoundMode resolveMode(SoundMode requestedMode) {
        if (requestedMode != SoundMode.AUTO) {
            return requestedMode;
        }
        MaterialProfile profile = materialBank().profile();
        for (MaterialId id : profile.materials().keySet()) {
            if (id.role() == MaterialId.Role.NOTCH) {
                log.debug("AUTO sound mode -> MATERIAL (profile '{}')", profile.name());
                return SoundMode.MATERIAL;
            }
        }
        log.debug("AUTO sound mode -> LEGACY (profile '{}' has no notch material)", profile.name());
        return SoundMode.LEGACY;
    }

    /** Material bank in use: the injected one, or the shared classpath bank. */
    MaterialBank materialBank() {
        if (materialBank != null) {
            return materialBank;
        }
        return MaterialBank.shared();
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
            java.io.InputStream labelsStream =
                    getClass().getResourceAsStream("/sound/train-sound-labels.txt");
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
     * Permite inyectar un AudioSample externo (usado por TestSynth). Actualiza los engines y
     * re-inicializa los notches con las labels existentes.
     *
     * <p>
     * Test seam: an injected single sample only makes sense on the legacy path, so this forces
     * {@link SoundMode#LEGACY} (ADR-029, PR C).
     */
    public void setSample(AudioSample sample) {
        soundMode = SoundMode.LEGACY;
        materialActive = false;
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

    private void initNotchesFromLabels(List<letrain.audio.util.AudacityLabelParser.Label> labels,
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
     * Pitchs: notch 0 = ralenti (1.0), notch 1..10 = cruise a distintas velocidades. Rango 0.7 →
     * 1.5 (80% de rango tonal, claramente audible).
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

        notches[0] = new SpeedNotch("Ralenti", 1.0f, 1.0f, 1.0f, rStart, rEnd, wStart, wEnd, 2.0f);

        // Notchs 1-10 — pitch range 1.1 → 2.0
        for (int i = 1; i <= 10; i++) {
            float pitch = 1.1f + (i - 1) * (0.9f / 9f); // 1.10 … 2.00
            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch, cStart, cEnd, wStart,
                    wEnd, 2.0f);
        }
    }

    private void initDefaultNotches() {
        float locoStart = 7302f, locoEnd = 21125f;
        float coachStart = 95715f, coachEnd = 204992f;
        for (int i = 0; i < 11; i++) {
            float pitch = (i == 0) ? 1.0f : 1.1f + (i - 1) * (0.9f / 9f);
            notches[i] = new SpeedNotch("Notch " + i, pitch, pitch, pitch, locoStart, locoEnd,
                    coachStart, coachEnd, 2.0f);
        }
        notches[0].name = "Ralenti";

        // Defaults for start/stop if labels fail
        ralentiStart = locoStart / 44100.0;
        ralentiEnd = locoEnd / 44100.0;
        startSegStart = 0;
        startSegEnd = ralentiStart;
        stopSegStart = ralentiEnd;
        stopSegEnd = sharedSample != null ? sharedSample.getLength() / sharedSample.getSampleRate()
                : ralentiEnd + 1.0;
    }

    // =====================================================================
    // Loop points
    // =====================================================================

    /** Aplica los puntos de bucle del notch N al locoEngine (modo legado). */
    private void applyLoopForNotch(int notchIdx) {
        if (materialActive) {
            playLoop(notchIdx, CROSSFADE_SECONDS, CROSSFADE_SECONDS);
            return;
        }
        if (sharedSample == null) {
            return;
        }
        SpeedNotch n = notches[notchIdx];
        float ls = convertSamplesToNorm(n.loopStart, sharedSample);
        float le = convertSamplesToNorm(n.loopEnd, sharedSample);
        locoEngine.setLoopPoints(ls, le);
    }

    /** Aplica puntos de bucle en segundos (p.ej. start/stop). */
    private void applyLoopSeconds(double startSec, double endSec) {
        if (sharedSample == null) {
            return;
        }
        float rate = sharedSample.getSampleRate();
        float ls = convertSamplesToNorm((float) (startSec * rate), sharedSample);
        float le = convertSamplesToNorm((float) (endSec * rate), sharedSample);
        locoEngine.setLoopPoints(ls, le);
    }

    // =====================================================================
    // AudioSource rail
    // =====================================================================

    @Override
    public boolean read(float[] buffer) {
        if (state == State.OFF) {
            return false;
        }
        if (!audioRunning) {
            return false;
        }
        if (soundMode == SoundMode.LEGACY) {
            readLegacy(buffer);
        } else {
            readMixed(buffer);
        }
        return true;
    }

    /**
     * Today's path, byte-for-byte: engines accumulate directly into the mix buffer and
     * {@code masterVolume} is applied at the end.
     */
    private void readLegacy(float[] buffer) {
        if (state != State.LOAD_ONLY) {
            updateBrakeVolume();
            locoEngine.read(buffer);
            coachEngine.read(buffer);
            if (brakeEngine != null) {
                brakeEngine.read(buffer);
            }
        }
        updateLoadVolume();
        if (loadEngine != null) {
            loadEngine.read(buffer);
        }
        applyMasterVolume(buffer);
    }

    /**
     * MATERIAL path (ADR-029 §2/§4): every voice reads into its own scratch buffer and is summed
     * with its per-sample gain envelope plus its material gain. Since PR D the two locomotive
     * voices carry notch loops and transition one-shots selected by the event-driven evaluation;
     * the legacy sub-voices (coach, brake, load) keep their old engines.
     */
    private void readMixed(float[] buffer) {
        if (state != State.LOAD_ONLY) {
            updateBrakeVolume();
            locoVoice.mixInto(buffer);
            coachVoice.mixInto(buffer);
            brakeVoice.mixInto(buffer);
            transVoice.mixInto(buffer);
        }
        updateLoadVolume();
        loadVoice.mixInto(buffer);
        applyMasterVolume(buffer);
    }

    private void applyMasterVolume(float[] buffer) {
        if (masterVolume != 1f) {
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] *= masterVolume;
            }
        }
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
        if (state != State.OFF && state != State.LOAD_ONLY && state != State.STOPPING) {
            return;
        }
        // From STOPPING (engine restarted before the stop sound ended) or LOAD_ONLY (load still
        // running): restart the engine voice while leaving the load loop untouched.
        isStopping = false;
        audioRunning = true;
        lastUpdateTime = 0;
        materialStartPlaying = false;

        if (materialActive && startMaterialAudio()) {
            coachEngine.setVolume(0f);
            return;
        }

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

    /**
     * Material start (ADR-029 §1/§4): the {@code start} one-shot fades in on a free engine voice
     * while the previous loop (if any) fades out. Returns false when there is no start material or
     * no free voice, so the caller can use the legacy segment.
     */
    private boolean startMaterialAudio() {
        MaterialId id = MaterialId.of(Role.START);
        AudioSample start = materialSample(id);
        if (start == null) {
            return false;
        }
        materialStartPlaying = true;
        state = State.STARTING;
        stateTimer = start.getLength() / start.getSampleRate();
        engineStarting = true;
        if (!playOneShot(start, materialGain(id), ONSET_FADE_SECONDS, CUT_SECONDS)) {
            pendingStartSample = start;
            pendingStartGain = materialGain(id);
        }
        return true;
    }

    public void stopAudio() {
        audioRunning = false;
        locoEngine.setVolume(0f);
        coachEngine.setVolume(0f);
        pendingStopSample = null;
        pendingStartSample = null;
        if (materialActive) {
            silenceEngineVoices();
        }
    }

    /**
     * Reproduce el segmento STOP una vez; el controller retira el synth cuando termina. En MATERIAL
     * usa el one-shot {@code stop} del perfil y deja el motor al silencio al terminar el timer.
     */
    public void playStopSound() {
        if (state == State.STOPPING) {
            return;
        }

        if (materialActive) {
            MaterialId id = MaterialId.of(Role.STOP);
            AudioSample stop = materialSample(id);
            if (stop != null) {
                isStopping = true;
                state = State.STOPPING;
                stateTimer = stop.getLength() / stop.getSampleRate();
                lastUpdateTime = 0;
                clearStage();
                pendingStepSeconds = 0f;
                pendingStartSample = null;
                if (!playOneShot(stop, materialGain(id), ONSET_FADE_SECONDS, CUT_SECONDS)) {
                    // Both engine voices are mid-fade; retry from the next updateState() call.
                    pendingStopSample = stop;
                    pendingStopGain = materialGain(id);
                }
                coachEngine.setVolume(0f);
                return;
            }
        }

        if (stopSegEnd <= stopSegStart || sharedSample == null) {
            audioRunning = false;
            state = State.OFF;
            return;
        }

        isStopping = true;
        state = State.STOPPING;
        stateTimer = (float) (stopSegEnd - stopSegStart);
        lastUpdateTime = 0;

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
        if (audioRunning) {
            locoEngine.setVolume(vol);
            if (materialActive) {
                transVoice.engine().setVolume(vol);
            }
        }
    }

    public void setCoachVolume(float vol) {
        this.baseCoachVolume = vol;
    }

    private float targetBrakeVolume = 0.0f;

    public void setBraking(boolean braking) {
        this.targetBrakeVolume = braking ? 0.8f : 0.0f;
    }

    private void updateBrakeVolume() {
        if (brakeEngine == null) {
            return;
        }
        float cur = brakeEngine.getVolume();
        float step = (targetBrakeVolume > cur) ? 0.02f : 0.01f;
        if (Math.abs(cur - targetBrakeVolume) < 0.01f) {
            brakeEngine.setVolume(targetBrakeVolume);
        } else
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
        if (loadEngine == null) {
            return;
        }
        float cur = loadEngine.getVolume();
        float step = (targetLoadVolume > cur) ? 0.05f : 0.02f;
        if (Math.abs(cur - targetLoadVolume) < 0.01f) {
            loadEngine.setVolume(targetLoadVolume);
        } else
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
        if (brakeEngine != null) {
            brakeEngine.setDistanceFilter(eff);
        }
        if (transEngine != null) {
            transEngine.setDistanceFilter(eff);
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

    // =====================================================================
    // Notches
    // =====================================================================

    public void setNotch(int index, SpeedNotch notch) {
        if (index >= 0 && index < notches.length) {
            notches[index] = notch;
        }
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

    /** True while the engine stop sound is playing, before the synth reaches {@code OFF}. */
    public boolean isStopping() {
        return isStopping;
    }

    /** True while only the load/unload loop is sounding (engine off, load still running). */
    public boolean isLoadOnly() {
        return state == State.LOAD_ONLY;
    }

    /** True while the engine voice is running, starting or transitioning. */
    public boolean isEngineRunning() {
        return state == State.STARTING || state == State.IDLE || state == State.CRUISING
                || state == State.TRANSITIONING_UP || state == State.TRANSITIONING_DOWN;
    }

    /** True while the load loop is audible (volume above zero). */
    public boolean isLoadSoundActive() {
        return loadEngine != null && loadEngine.getVolume() > 0f;
    }

    public boolean isTransitioning() {
        if (state == State.TRANSITIONING_UP || state == State.TRANSITIONING_DOWN) {
            return true;
        }
        return materialActive && activeStage != null;
    }

    /**
     * Fuerza una transición inmediata a ralentí (notch 0), saltándose cualquier rampa en curso. Se
     * usa cuando un tren choca o llega a un fin de vía.
     */
    public synchronized void forceIdle() {
        if (state == State.OFF || state == State.STOPPING || state == State.LOAD_ONLY) {
            return;
        }

        if (materialActive) {
            materialCurrentNotch = 0;
            lastEvaluatedNotch = 0;
            currentNotchIndex = 0;
            targetNotchIndex = 0;
            pendingStepSeconds = 0f;
            pendingLoopNotch = -1;
            pendingStartSample = null;
            clearStage();
            engineStarting = false;
            materialStartPlaying = false;
            if (engineVoice != null && !engineVoice.isSilent()) {
                engineVoice.fadeTo(0f, CUT_SECONDS);
            }
            playLoop(0, CROSSFADE_SECONDS, CUT_SECONDS);
            state = State.IDLE;
            setBraking(false);
            notifyNotch(0);
            return;
        }

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

    /**
     * Reports the physical notch (Locomotive.currentSpeed) for the event-driven material engine.
     * Only stores the snapshot and marks it pending: the single evaluation point is the start of
     * {@link #updateState(float, long)} (ADR-029 §2). Ignored in LEGACY mode.
     */
    public synchronized void setCurrentNotch(int notch) {
        if (!materialActive) {
            return;
        }
        if (notch < 0 || notch > 10) {
            return;
        }
        if (notch == materialCurrentNotch) {
            return;
        }
        materialCurrentNotch = notch;
        materialSnapshotDirty = true;
    }

    /**
     * Solicita cambiar al notch 'index'. En LEGACY arranca la rampa de hoy; en MATERIAL solo guarda
     * el snapshot y la evaluación por frame lanza la transición con coalescing (ADR-029 §2).
     */
    public synchronized void setThrottle(int index) {
        if (state == State.STOPPING || state == State.STARTING || state == State.LOAD_ONLY) {
            return;
        }
        if (index < 0 || index >= notches.length) {
            return;
        }
        if (index == targetNotchIndex) {
            return;
        }

        if (materialActive) {
            targetNotchIndex = index;
            materialSnapshotDirty = true;
            return;
        }

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
        state = (targetNotchIndex > currentNotchIndex) ? State.TRANSITIONING_UP
                : State.TRANSITIONING_DOWN;

        SpeedNotch target = notches[nextNotch];
        rampStartSpeed = locoEngine.getSpeed();
        rampTargetSpeed = target.cruiseSpeed;
        rampDuration = target.rampTime;
        rampStartTime = System.nanoTime();

        rampStartCoachVol = coachEngine.getVolume();
        rampTargetCoachVol = (nextNotch > 0) ? baseCoachVolume : 0.0f;
    }

    public void update() {
        if (state == State.OFF) {
            return;
        }

        if (lastUpdateTime == 0) {
            lastUpdateTime = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        float deltaTime = (now - lastUpdateTime) / 1_000_000_000.0f;
        lastUpdateTime = now;

        updateState(deltaTime, now);
    }

    /**
     * Advances the state machine by a fixed delta. Used by deterministic tests and fixed-step
     * callers; production drives {@link #update()} with wall-clock time.
     */
    public void update(float deltaSeconds) {
        if (state == State.OFF) {
            return;
        }
        lastUpdateTime = System.nanoTime();
        updateState(deltaSeconds, lastUpdateTime);
    }

    private void updateState(float deltaTime, long now) {
        if (materialActive) {
            evaluateMaterial(deltaTime);
        }
        switch (state) {
            case STARTING:
                stateTimer -= deltaTime;
                if (stateTimer <= 0) {
                    engineStarting = false;
                    if (materialStartPlaying) {
                        materialStartPlaying = false;
                        currentNotchIndex = 0;
                        applyLoopForNotch(0);
                        state = State.IDLE;
                        break;
                    }
                    locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
                    locoEngine.setTurnProbability(0.15f);
                    applyLoopForNotch(0);
                    locoEngine.setSpeed(notches[0].cruiseSpeed);
                    state = State.IDLE;
                    if (targetNotchIndex != currentNotchIndex) {
                        startTransition();
                    }
                }
                break;

            case STOPPING:
                stateTimer -= deltaTime;
                if (stateTimer <= 0) {
                    isStopping = false;
                    if (materialActive) {
                        silenceEngineVoices();
                    }
                    if (loading) {
                        // The load/unload process is still running: mute the engine voices and
                        // keep the synth alive so the load loop keeps playing (issue #360).
                        locoEngine.setVolume(0f);
                        coachEngine.setVolume(0f);
                        if (brakeEngine != null) {
                            brakeEngine.setVolume(0f);
                        }
                        state = State.LOAD_ONLY;
                    } else {
                        loadEngine.setVolume(0f);
                        targetLoadVolume = 0f;
                        audioRunning = false;
                        state = State.OFF;
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
                    float newCoachVol =
                            rampStartCoachVol + (rampTargetCoachVol - rampStartCoachVol) * progress;
                    coachEngine.setVolume(newCoachVol);
                }
                break;
        }
    }

    /**
     * Rampa de pitch durante durationSec segundos. Simultáneamente notifica la velocidad para que
     * la locomotora y la palanca se actualicen.
     */
    /** Actualiza el loopPoint de los vagones según velocidad de movimiento. */
    public void setMotionSpeed(int speed) {
        if (isStopping) {
            return;
        }
        if (sharedSample == null) {
            return;
        }
        // Los vagones usan siempre el mismo segmento pero con volumen proporcional a la
        // velocidad.
        // El loop ya está configurado en los notches.
        if (notches[0] == null) {
            return;
        }
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
    // Motor event-driven de MATERIAL (ADR-029 §2/§3/§4, PR D)
    // =====================================================================

    /**
     * Único punto de evaluación por frame (ADR-029 §2): consume el snapshot que dejaron
     * {@link #setCurrentNotch(int)} / {@link #setThrottle(int)}, avanza el timer del tramo activo y
     * resuelve la cadena de fallbacks con {@link TransitionPlanner}.
     */
    private void evaluateMaterial(float deltaTime) {
        if (pendingStartSample != null) {
            if (playOneShot(pendingStartSample, pendingStartGain, ONSET_FADE_SECONDS,
                    CUT_SECONDS)) {
                pendingStartSample = null;
            }
            return;
        }
        if (pendingStopSample != null) {
            if (playOneShot(pendingStopSample, pendingStopGain, ONSET_FADE_SECONDS, CUT_SECONDS)) {
                pendingStopSample = null;
            }
            return;
        }
        if (state != State.IDLE && state != State.CRUISING) {
            return; // STARTING/STOPPING/LOAD_ONLY lifecycle owns the voices
        }

        boolean snapshotChanged = materialSnapshotDirty;
        materialSnapshotDirty = false;
        flushPendingMaterialActions();

        int current = materialCurrentNotch;
        int target = targetNotchIndex;
        boolean physicalStepCompleted = current != lastEvaluatedNotch;
        lastEvaluatedNotch = current;

        if (activeStage != null) {
            stageElapsed += deltaTime;
            if (activeStage.kind() == TransitionPlanner.Kind.LEGACY_RAMP) {
                updateLegacyRampSpeed();
            }
        }

        if (!snapshotChanged && activeStage == null && pendingStage == null && pendingLoopNotch < 0
                && !waitingForStep && !riding && current == target) {
            syncMaterialState(current);
            return;
        }

        boolean stageHandled = false;
        if (activeStage != null && current == activeStage.to() && target == activeStage.to()) {
            // Arrived: crossfade to the loop of the reached notch (ADR-029 §4).
            applyLoopForNotch(current);
            clearStage();
            stageHandled = true;
        } else if (activeStage != null && current == activeStage.to()) {
            // The physical step completed and the lever wants more: chain the next stage.
            startStage(firstStage(current, target));
            stageHandled = true;
        } else if (activeStage != null && !covers(activeStage, current, target)) {
            // The physical state abandoned the segment: cut short and re-evaluate (ADR-029 §4).
            abandonStage(current);
        } else if (activeStage != null && stageElapsed >= stageDuration - CROSSFADE_SECONDS) {
            // One-shot/ramp ends before the physical step: wait on the reached loop (ADR-029 §4).
            applyLoopForNotch(current);
            activeStage = null;
            waitingForStep = true;
        }

        if (!stageHandled && activeStage == null && waitingForStep && current == target) {
            applyLoopForNotch(current);
            clearStage();
        } else if (!stageHandled && activeStage == null && waitingForStep
                && physicalStepCompleted) {
            startStage(firstStage(current, target));
        } else if (activeStage == null && !waitingForStep && !riding && current == target
                && physicalStepCompleted) {
            // Steady notch change with no transition to sound (e.g. the immediate 0 -> 1 step):
            // the loop follows the physics with a plain crossfade.
            applyLoopForNotch(current);
        }

        if (activeStage == null && !waitingForStep && !riding && current != target) {
            pendingStepSeconds += deltaTime;
            if (pendingStepSeconds >= COALESCE_WINDOW_SECONDS) {
                riding = true;
                startStage(firstStage(current, target));
            }
        } else if (current == target) {
            pendingStepSeconds = 0f;
        }

        syncMaterialState(current);
    }

    /** Retries a loop/stage handover that had to wait for a silent voice. */
    private void flushPendingMaterialActions() {
        if (pendingLoopNotch >= 0) {
            int notch = pendingLoopNotch;
            pendingLoopNotch = -1;
            playLoop(notch, CROSSFADE_SECONDS, CROSSFADE_SECONDS);
        }
        if (pendingStage != null) {
            TransitionPlanner.Step stage = pendingStage;
            pendingStage = null;
            startStage(stage);
        }
    }

    /** First stage of the planner's fallback chain for the ride {@code from -> to}. */
    private TransitionPlanner.Step firstStage(int from, int to) {
        TransitionPlanner.Plan plan = TransitionPlanner.plan(from, to,
                id -> materialBank().profile().has(id), legacyRampAvailable());
        return plan.first();
    }

    /** True while the legacy {@code train-sound.wav} cruise segment can still serve level 3. */
    private boolean legacyRampAvailable() {
        return sharedSample != null && cruiseEnd > cruiseStart && notches[0] != null;
    }

    /** Starts a planned stage, deferring it when no engine voice can swap its sample yet. */
    private void startStage(TransitionPlanner.Step stage) {
        if (stage == null) {
            clearStage();
            return;
        }
        switch (stage.kind()) {
            case MATERIAL -> startMaterialStage(stage);
            case LEGACY_RAMP -> startLegacyRampStage(stage);
            case LOOP_CROSSFADE -> {
                // Level 4: no one-shot material, the loop of the reached notch keeps sounding while
                // the physical steps complete; the arrival logic crossfades to the destination.
                activeStage = null;
                stageElapsed = 0f;
                stageDuration = 0f;
                waitingForStep = true;
            }
        }
    }

    private void startMaterialStage(TransitionPlanner.Step stage) {
        AudioSample sample = materialBank().transition(stage.from(), stage.to()).orElse(null);
        if (sample == null) {
            startStage(downgrade(stage));
            return;
        }
        if (!playOneShot(sample, materialGain(stage.materialId()), ONSET_FADE_SECONDS,
                CUT_SECONDS)) {
            pendingStage = stage;
            return;
        }
        activeStage = stage;
        stageElapsed = 0f;
        stageDuration = sample.getLength() / sample.getSampleRate();
        waitingForStep = false;
    }

    private void startLegacyRampStage(TransitionPlanner.Step stage) {
        if (!legacyRampAvailable()) {
            startStage(downgrade(stage));
            return;
        }
        float rate = sharedSample.getSampleRate();
        float loopStart = convertSamplesToNorm((float) (cruiseStart * rate), sharedSample);
        float loopEnd = convertSamplesToNorm((float) (cruiseEnd * rate), sharedSample);
        if (!playSource(sharedSample, loopStart, loopEnd, true, notches[stage.from()].cruiseSpeed,
                0.0, ONSET_FADE_SECONDS, CUT_SECONDS)) {
            pendingStage = stage;
            return;
        }
        activeStage = stage;
        stageElapsed = 0f;
        stageDuration = Math.max(0.001f, notches[stage.to()].rampTime);
        waitingForStep = false;
    }

    /** Level 3 when possible, level 4 otherwise. */
    private TransitionPlanner.Step downgrade(TransitionPlanner.Step stage) {
        TransitionPlanner.Kind kind = legacyRampAvailable() ? TransitionPlanner.Kind.LEGACY_RAMP
                : TransitionPlanner.Kind.LOOP_CROSSFADE;
        return new TransitionPlanner.Step(stage.from(), stage.to(), kind);
    }

    /** Legacy pitch ramp over the retained {@code cruise} segment (ADR-029 §3 level 3). */
    private void updateLegacyRampSpeed() {
        if (sharedSample == null || engineVoice == null || activeStage == null
                || engineVoice.engine().getSample() != sharedSample) {
            return;
        }
        float progress = Math.min(1f, stageElapsed / Math.max(0.001f, stageDuration));
        float fromPitch = notches[activeStage.from()].cruiseSpeed;
        float toPitch = notches[activeStage.to()].cruiseSpeed;
        engineVoice.engine().setSpeed(fromPitch + (toPitch - fromPitch) * progress);
    }

    /**
     * True while the active stage still covers the physical state: physics inside the stage span,
     * same direction, and the stage destination still on the way to the lever target.
     */
    private boolean covers(TransitionPlanner.Step stage, int current, int target) {
        if (current == target) {
            return false;
        }
        int direction = target > current ? 1 : -1;
        int stageDirection = stage.to() > stage.from() ? 1 : -1;
        if (direction != stageDirection) {
            return false;
        }
        if (direction > 0) {
            return current >= stage.from() && current <= stage.to() && target >= stage.to();
        }
        return current <= stage.from() && current >= stage.to() && target <= stage.to();
    }

    /** Cuts the active stage with a short fade and re-evaluates from the new snapshot. */
    private void abandonStage(int current) {
        activeStage = null;
        pendingStage = null;
        waitingForStep = false;
        riding = false;
        pendingStepSeconds = 0f;
        if (engineVoice != null && !engineVoice.isSilent()) {
            engineVoice.fadeTo(0f, CUT_SECONDS);
        }
        if (current == targetNotchIndex) {
            // Dwell: the lever stopped on the reached notch; restore its loop.
            playLoop(current, CROSSFADE_SECONDS, CUT_SECONDS);
        }
    }

    private void clearStage() {
        activeStage = null;
        pendingStage = null;
        stageElapsed = 0f;
        stageDuration = 0f;
        riding = false;
        waitingForStep = false;
    }

    private void syncMaterialState(int current) {
        if (state == State.IDLE && current > 0) {
            state = State.CRUISING;
        } else if (state == State.CRUISING && current == 0) {
            state = State.IDLE;
        }
    }

    // --- Handover de las dos voces de locomotora ---

    /**
     * Plays a one-shot on a free engine voice. The other voice is used as the incoming voice when
     * the current one is still audible, so the outgoing sample is never swapped in place.
     */
    private boolean playOneShot(AudioSample sample, double gainDb, float fadeIn, float fadeOut) {
        if (!playSource(sample, 0f, 1f, false, 1.0f, gainDb, fadeIn, fadeOut)) {
            return false;
        }
        engineLoopNotch = -1;
        return true;
    }

    private boolean playSource(AudioSample sample, float loopStart, float loopEnd, boolean loop,
            float speed, double gainDb, float fadeIn, float fadeOut) {
        if (sample == null || engineVoice == null || spareVoice == null) {
            return false;
        }
        Voice target;
        if (engineVoice.isSilent()) {
            target = engineVoice;
        } else if (spareVoice.isSilent()) {
            target = spareVoice;
        } else {
            return false;
        }
        prepareVoice(target, sample, loopStart, loopEnd, loop, speed, gainDb, fadeIn);
        if (target == spareVoice) {
            engineVoice.fadeTo(0f, fadeOut);
            Voice previous = engineVoice;
            engineVoice = spareVoice;
            spareVoice = previous;
        }
        return true;
    }

    private void prepareVoice(Voice voice, AudioSample sample, float loopStart, float loopEnd,
            boolean loop, float speed, double gainDb, float fadeIn) {
        GrainEngine engine = voice.engine();
        voice.setSample(sample);
        engine.setSampleRate(sample.getSampleRate());
        engine.setLoopMode(loop ? GrainEngine.LoopMode.WRAP : GrainEngine.LoopMode.PLAY_ONCE);
        engine.setTurnProbability(0f);
        engine.setReverse(false);
        engine.setLoopPoints(clamp01(loopStart), clamp01(loopEnd));
        engine.setSpeed(speed);
        engine.setVolume(baseLocoVolume);
        voice.setMaterialGainDb(gainDb);
        voice.setGain(0f);
        voice.fadeTo(1f, fadeIn);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    // --- Resolución de loops ---

    /** Plays (or keeps) the loop of a physical notch: material, legacy segment or nearest notch. */
    private void playLoop(int notch, float fadeIn, float fadeOut) {
        if (notch < 0 || notch > 10 || playingLoop(notch)) {
            return;
        }
        LoopSource source = resolveLoop(notch);
        if (source == null) {
            log.debug("no loop material or legacy segment for notch {}", notch);
            return;
        }
        if (source.legacy() && engineVoice == locoVoice && locoVoice.isSilent()) {
            playLegacyLoopInPlace(source, fadeIn);
            return;
        }
        if (playSource(source.sample(), source.loopStart(), source.loopEnd(), true, source.speed(),
                source.gainDb(), fadeIn, fadeOut)) {
            engineLoopNotch = notch;
        } else {
            pendingLoopNotch = notch;
        }
    }

    private boolean playingLoop(int notch) {
        return engineVoice != null && engineLoopNotch == notch && !engineVoice.isSilent()
                && engineVoice.getTargetGain() > 0f;
    }

    /**
     * Loop resolution for a physical notch: material first, then the retained legacy segment
     * (re-pitched {@code cruise}/ralenti), then the nearest declared notch re-pitched by the
     * audited ratio, and finally nothing (the caller keeps whatever is sounding).
     */
    private LoopSource resolveLoop(int notch) {
        MaterialId id = loopMaterialId(notch);
        Optional<AudioSample> sample = materialBank().resolve(id);
        if (sample.isPresent()) {
            LoopSource source = materialLoopSource(id, sample.get(), notch, 1.0f);
            if (source != null) {
                return source;
            }
        }
        if (sharedSample != null && notches[notch] != null) {
            return legacyLoopSource(notch);
        }
        return nearestMaterialLoop(notch);
    }

    private static MaterialId loopMaterialId(int notch) {
        return notch == 0 ? MaterialId.of(Role.IDLE) : MaterialId.notch(notch);
    }

    private LoopSource materialLoopSource(MaterialId id, AudioSample sample, int notch,
            float speed) {
        MaterialRef ref = materialBank().material(id).orElse(null);
        if (ref == null || ref.loop().isEmpty() || sample.getLength() <= 0) {
            return null;
        }
        LoopPoints points = ref.loop().get();
        float rate = sample.getSampleRate();
        float length = sample.getLength();
        float loopStart = (float) (points.startSeconds() * rate / length);
        float loopEnd = (float) (points.endSeconds() * rate / length);
        return new LoopSource(sample, loopStart, loopEnd, speed, ref.gainDb(), notch, false);
    }

    private LoopSource legacyLoopSource(int notch) {
        SpeedNotch notchDef = notches[notch];
        return new LoopSource(sharedSample, convertSamplesToNorm(notchDef.loopStart, sharedSample),
                convertSamplesToNorm(notchDef.loopEnd, sharedSample), notchDef.cruiseSpeed, 0.0,
                notch, true);
    }

    private LoopSource nearestMaterialLoop(int notch) {
        for (int distance = 1; distance <= 10; distance++) {
            for (int candidate : new int[] {notch - distance, notch + distance}) {
                if (candidate < 0 || candidate > 10) {
                    continue;
                }
                MaterialId id = loopMaterialId(candidate);
                Optional<AudioSample> sample = materialBank().resolve(id);
                if (sample.isPresent()) {
                    float speed = (float) Math.pow(NOTCH_PITCH_RATIO, notch - candidate);
                    LoopSource source = materialLoopSource(id, sample.get(), notch, speed);
                    if (source != null) {
                        return source;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Exact legacy loop in place (same {@code locoEngine} calls as today's path): used only when
     * the loco voice is the silent engine voice, so no sample is swapped under an audible voice.
     */
    private void playLegacyLoopInPlace(LoopSource source, float fadeIn) {
        int notch = source.notch();
        SpeedNotch notchDef = notches[notch];
        if (locoEngine.getSample() != sharedSample) {
            locoEngine.setSample(sharedSample);
        }
        locoEngine.setSampleRate(sharedSample.getSampleRate());
        locoEngine.setLoopMode(GrainEngine.LoopMode.PING_PONG);
        locoEngine.setTurnProbability(0.15f);
        locoEngine.setLoopPoints(convertSamplesToNorm(notchDef.loopStart, sharedSample),
                convertSamplesToNorm(notchDef.loopEnd, sharedSample));
        locoEngine.setSpeed(notchDef.cruiseSpeed);
        locoEngine.setVolume(baseLocoVolume);
        locoVoice.setMaterialGainDb(0.0);
        locoVoice.setGain(0f);
        locoVoice.fadeTo(1f, fadeIn);
        engineVoice = locoVoice;
        spareVoice = transVoice;
        engineLoopNotch = notch;
    }

    private void silenceEngineVoices() {
        pendingStopSample = null;
        pendingStartSample = null;
        if (locoVoice != null) {
            locoVoice.setGain(0f);
        }
        if (transVoice != null) {
            transVoice.setGain(0f);
        }
        engineVoice = locoVoice;
        spareVoice = transVoice;
        engineLoopNotch = -1;
        pendingLoopNotch = -1;
        clearStage();
        pendingStepSeconds = 0f;
    }

    private AudioSample materialSample(MaterialId id) {
        return materialBank().resolve(id).orElse(null);
    }

    private double materialGain(MaterialId id) {
        return materialBank().material(id).map(MaterialRef::gainDb).orElse(0.0);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private float convertSamplesToNorm(float samples, AudioSample sample) {
        if (sample == null || sample.getLength() == 0) {
            return 0.0f;
        }
        return samples / sample.getLength();
    }

    // --- Acceso a engines para debug ---
    public GrainEngine getLocoEngine() {
        return locoEngine;
    }

    public GrainEngine getCoachEngine() {
        return coachEngine;
    }

    // --- Acceso a las voces del bus de mezcla (tests y selector de la PR D) ---
    Voice getLocoVoice() {
        return locoVoice;
    }

    Voice getCoachVoice() {
        return coachVoice;
    }

    Voice getBrakeVoice() {
        return brakeVoice;
    }

    Voice getLoadVoice() {
        return loadVoice;
    }

    Voice getTransVoice() {
        return transVoice;
    }

    // --- Seams de test del motor event-driven de MATERIAL (PR D) ---

    /** Voice currently sounding the engine (loop or one-shot); the other one is the spare. */
    Voice getEngineVoice() {
        return engineVoice;
    }

    /** Notch of the loop loaded on the engine voice, or -1 when it carries a one-shot. */
    int getEngineLoopNotch() {
        return engineLoopNotch;
    }

    TransitionPlanner.Step getActiveStage() {
        return activeStage;
    }

    boolean isMaterialSnapshotDirty() {
        return materialSnapshotDirty;
    }
}
