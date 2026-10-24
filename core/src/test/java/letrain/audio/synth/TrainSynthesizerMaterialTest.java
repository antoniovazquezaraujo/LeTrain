package letrain.audio.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import letrain.audio.material.MaterialBank;
import letrain.audio.material.MaterialId;
import letrain.audio.material.MaterialId.Role;
import letrain.audio.material.MaterialProfile;
import letrain.audio.material.MaterialProfileParser;
import letrain.audio.material.WavFixture;
import letrain.audio.synth.TrainSynthesizer.SoundMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Event-driven integration tests of the material engine (ADR-029 §2/§3/§4, PR D). The fixtures are
 * short synthetic WAVs and the physics is simulated by driving
 * {@link TrainSynthesizer#update(float)} with 50 ms steps and advancing the voice envelopes with
 * {@link TrainSynthesizer#read(float[])}.
 */
@DisplayName("TrainSynthesizer - event-driven material engine (ADR-029 PR D)")
class TrainSynthesizerMaterialTest {

    private static final MaterialId IDLE = MaterialId.of(Role.IDLE);
    private static final MaterialId START = MaterialId.of(Role.START);
    private static final MaterialId STOP = MaterialId.of(Role.STOP);

    private static final String TEST_PROFILE = """
            profile = test
            start     = sound/start.wav
            stop      = sound/stop.wav
            idle      = sound/idle.wav      loop=0.0,0.5
            notch.1   = sound/notch-1.wav   loop=0.0,0.5
            notch.2   = sound/notch-2.wav   loop=0.0,0.5
            notch.3   = sound/notch-3.wav   loop=0.0,0.5
            notch.4   = sound/notch-4.wav   loop=0.0,0.5
            trans.1-2 = sound/trans-1-2.wav
            trans.2-3 = sound/trans-2-3.wav
            trans.3-4 = sound/trans-3-4.wav
            trans.2-1 = sound/trans-2-1.wav
            trans.3-2 = sound/trans-3-2.wav
            trans.4-3 = sound/trans-4-3.wav
            """;

    @TempDir
    Path tempDir;

    private Map<MaterialId, AudioSample> samples;
    private TrainSynthesizer synth;

    @BeforeEach
    void setUp() throws Exception {
        MaterialProfile profile = MaterialProfileParser.parse(TEST_PROFILE);
        samples = new LinkedHashMap<>();
        for (MaterialId id : profile.materials().keySet()) {
            int frames = switch (id.role()) {
                case START, STOP -> 4410; // 0.1 s lifecycle one-shots
                default -> 44100; // 1 s loops and transitions
            };
            double frequencyHz = 120.0 + samples.size() * 37.0;
            Path file = WavFixture.writeSine(tempDir, id.canonical() + ".wav", frames, frequencyHz);
            samples.put(id, new AudioSample(file.toFile()));
        }
        MaterialBank bank = new MaterialBank(profile, id -> Optional.ofNullable(samples.get(id)));
        synth = new TrainSynthesizer(SoundMode.MATERIAL, bank);
    }

    @Test
    @DisplayName("AUTO default resolves to MATERIAL with the shipped generic profile (PR D)")
    void should_ResolveAutoToMaterial_When_ShippedProfileDeclaresNotches() {
        TrainSynthesizer auto = new TrainSynthesizer(SoundMode.AUTO, null);

        assertEquals(SoundMode.MATERIAL, auto.getSoundMode());
    }

    @Test
    @DisplayName("plays the start one-shot and settles on the idle loop")
    void should_PlayStartOneShot_Then_IdleLoop() {
        synth.startAudio();

        assertTrue(synth.isEngineStarting(), "the material start must run the STARTING lifecycle");
        assertSame(samples.get(START), synth.getEngineVoice().engine().getSample());

        synth.update(0.2f); // the start fixture lasts 0.1 s

        assertFalse(synth.isEngineStarting());
        assertEquals(0, synth.getEngineLoopNotch());
        assertSame(samples.get(IDLE), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("keeps the snapshot pending until the single evaluation point runs")
    void should_DeferSnapshot_Until_UpdateStateRuns() {
        startEngine();

        synth.setCurrentNotch(1);
        synth.setThrottle(2);

        assertTrue(synth.isMaterialSnapshotDirty(), "setters must only mark the snapshot");
        assertNull(synth.getActiveStage(), "no transition may start outside updateState");
        assertEquals(0, synth.getEngineLoopNotch(), "the idle loop is still the only sound");
    }

    @Test
    @DisplayName("starts the physical-step one-shot after the coalescing window")
    void should_StartOneShot_When_StepPersistsPastCoalescingWindow() {
        startEngine();
        MaterialId pair = MaterialId.transition(1, 2);
        synth.setCurrentNotch(1);
        synth.setThrottle(2);

        for (int i = 0; i < 7; i++) {
            synth.update(0.05f); // 350 ms < 400 ms
        }

        assertNull(synth.getActiveStage(), "the edge must be debounced");
        assertSame(samples.get(IDLE), synth.getEngineVoice().engine().getSample());

        synth.update(0.05f); // 400 ms -> debounce elapsed

        assertNotNull(synth.getActiveStage());
        assertEquals(pair, synth.getActiveStage().materialId());
        assertSame(samples.get(pair), synth.getEngineVoice().engine().getSample());

        synth.read(new float[256]); // advance the onset fade-in one block
        assertTrue(synth.getEngineVoice().getGain() > 0.0f, "the one-shot must fade in");
    }

    @Test
    @DisplayName("does not fire when the lever jitters inside the coalescing window")
    void should_NotFire_When_LeverJittersInsideCoalescingWindow() {
        startEngine();
        synth.setCurrentNotch(1);

        synth.setThrottle(2);
        synth.update(0.2f);
        synth.setThrottle(1);
        synth.update(0.2f);
        synth.setThrottle(2);
        synth.update(0.2f);
        synth.setThrottle(1);
        synth.update(0.5f);

        assertNull(synth.getActiveStage(), "a jittery lever must not generate one-shot bursts");
        assertEquals(0, synth.getEngineLoopNotch());
        assertSame(samples.get(IDLE), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("chains the next adjacent one-shot while the climb continues")
    void should_ChainNextAdjacentOneShot_When_ClimbContinues() {
        startEngine();
        synth.setCurrentNotch(1);
        synth.setThrottle(4);
        fireTransitionAfterCoalescing();
        assertEquals(MaterialId.transition(1, 2), synth.getActiveStage().materialId());
        settleFades();

        synth.setCurrentNotch(2); // first physical step completed, target still 4
        synth.update(0.05f);

        assertEquals(MaterialId.transition(2, 3), synth.getActiveStage().materialId());
        assertSame(samples.get(MaterialId.transition(2, 3)),
                synth.getEngineVoice().engine().getSample());
        settleFades();

        synth.setCurrentNotch(3);
        synth.update(0.05f);
        assertEquals(MaterialId.transition(3, 4), synth.getActiveStage().materialId());
        settleFades();

        synth.setCurrentNotch(4); // arrival
        synth.update(0.05f);
        settleFades();

        assertNull(synth.getActiveStage());
        assertEquals(4, synth.getEngineLoopNotch());
        assertSame(samples.get(MaterialId.notch(4)), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("crossfades to the destination loop when the target is reached")
    void should_CrossfadeToDestinationLoop_When_TargetReached() {
        startEngine();
        synth.setCurrentNotch(1);
        synth.setThrottle(2);
        fireTransitionAfterCoalescing();
        Voice oneShot = synth.getEngineVoice();
        settleFades();

        synth.setCurrentNotch(2);
        synth.update(0.05f);

        assertNull(synth.getActiveStage());
        assertEquals(2, synth.getEngineLoopNotch());
        assertSame(samples.get(MaterialId.notch(2)), synth.getEngineVoice().engine().getSample());
        assertEquals(1.0f, synth.getEngineVoice().getTargetGain(), 0.0f);
        assertEquals(0.0f, oneShot.getTargetGain(), 0.0f, "the one-shot must crossfade out");

        settleFades();
        assertEquals(0.0f, oneShot.getGain(), 0.0f);
    }

    @Test
    @DisplayName("crossfades the loop directly when the step completes inside the coalescing window")
    void should_CrossfadeLoop_When_StepCompletesInsideCoalescingWindow() {
        startEngine();

        synth.setCurrentNotch(1); // 0 -> 1 is immediate in the physics (ADR-029 §2)
        synth.setThrottle(1);
        synth.update(0.05f);
        settleFades();

        assertNull(synth.getActiveStage(), "no transition material exists for 0 -> 1");
        assertEquals(1, synth.getEngineLoopNotch());
        assertSame(samples.get(MaterialId.notch(1)), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("cuts the one-shot with the short fade when the physics abandons the segment")
    void should_CutOneShot_When_TargetReversesBeforeStepCompletes() {
        startEngine();
        synth.setCurrentNotch(1);
        synth.setThrottle(3);
        fireTransitionAfterCoalescing();
        assertEquals(MaterialId.transition(1, 2), synth.getActiveStage().materialId());
        Voice oneShot = synth.getEngineVoice();
        settleFades();

        synth.setThrottle(1); // lever back to the reached notch: abandon
        synth.update(0.05f);

        assertNull(synth.getActiveStage());
        assertEquals(0.0f, oneShot.getTargetGain(), 0.0f);

        settleFades();
        assertEquals(0.0f, oneShot.getGain(), 0.0f);
        assertEquals(1, synth.getEngineLoopNotch(), "the reached loop must be restored");
        assertSame(samples.get(MaterialId.notch(1)), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("waits on the reached loop when the one-shot ends before the physical step")
    void should_WaitOnReachedLoop_When_OneShotEndsBeforeStep() {
        startEngine();
        synth.setCurrentNotch(2);
        synth.setThrottle(1); // down step 2 -> 1
        fireTransitionAfterCoalescing();
        assertEquals(MaterialId.transition(2, 1), synth.getActiveStage().materialId());
        settleFades();

        for (int i = 0; i < 17; i++) { // 0.85 s > 1.0 s material - 0.2 s crossfade
            synth.update(0.05f);
        }

        assertNull(synth.getActiveStage());
        assertFalse(synth.isTransitioning(), "the one-shot already finished");
        assertEquals(2, synth.getEngineLoopNotch(), "the reached notch loop must be sounding");
        assertSame(samples.get(MaterialId.notch(2)), synth.getEngineVoice().engine().getSample());
        settleFades();

        synth.setCurrentNotch(1); // physical step completes
        synth.update(0.05f);
        assertEquals(1, synth.getEngineLoopNotch());
        assertSame(samples.get(MaterialId.notch(1)), synth.getEngineVoice().engine().getSample());
    }

    @Test
    @DisplayName("falls back to the legacy cruise ramp for a step with no transition material")
    void should_UseLegacyRamp_When_StepHasNoMaterial() {
        startEngine();
        synth.setCurrentNotch(0);
        synth.setThrottle(1); // there is no trans.0-1 in the profile
        fireTransitionAfterCoalescing();

        assertEquals(MaterialId.transition(0, 1), synth.getActiveStage().materialId());
        assertEquals(TransitionPlanner.Kind.LEGACY_RAMP, synth.getActiveStage().kind());
        assertFalse(samples.containsValue(synth.getEngineVoice().engine().getSample()),
                "level 3 must run on the retained train-sound.wav segment");

        float pitchBefore = synth.getEngineVoice().engine().getSpeed();
        synth.update(0.5f);
        assertTrue(synth.getEngineVoice().engine().getSpeed() > pitchBefore,
                "the legacy ramp must raise the pitch towards the destination notch");
    }

    @Test
    @DisplayName("plays the stop one-shot and silences the engine when the stop timer ends")
    void should_PlayStopOneShot_When_EngineStops() {
        startEngine();

        synth.playStopSound();

        assertTrue(synth.isStopping());
        assertSame(samples.get(STOP), synth.getEngineVoice().engine().getSample());

        synth.update(0.2f); // the stop fixture lasts 0.1 s

        assertFalse(synth.isStopping());
        assertEquals(0.0f, synth.getEngineVoice().getGain(), 0.0f);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Starts the engine with the material lifecycle and settles on the idle loop. */
    private void startEngine() {
        synth.startAudio();
        synth.update(0.2f);
        settleFades();
        assertEquals(0, synth.getEngineLoopNotch(), "precondition: idle loop");
    }

    /** Advances updates until the coalescing window fires the first stage of a transition. */
    private void fireTransitionAfterCoalescing() {
        for (int i = 0; i < 9 && synth.getActiveStage() == null; i++) {
            synth.update(0.05f);
        }
        assertNotNull(synth.getActiveStage(), "the transition must fire after 400 ms");
    }

    /** Reads enough mixer blocks to finish every running envelope fade (200 ms at 44.1 kHz). */
    private void settleFades() {
        float[] buffer = new float[1024];
        for (int i = 0; i < 12; i++) {
            synth.read(buffer);
        }
    }
}
