package letrain.audio.synth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import letrain.audio.material.WavFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Voice - per-sample envelope and independent mix gains")
class VoiceTest {

    @TempDir
    Path tempDir;

    private AudioSample sample;

    @BeforeEach
    void setUp() throws Exception {
        sample = WavFixture.sample(tempDir, "voice.wav");
    }

    @ParameterizedTest(name = "fade of {0} s")
    @ValueSource(floats = {TrainSynthesizer.ONSET_FADE_SECONDS, TrainSynthesizer.CROSSFADE_SECONDS})
    @DisplayName("fades the gain sample by sample without zipper jumps")
    void should_FadeGainPerSample_When_FadeRequested(float seconds) {
        Voice voice = new Voice("test", new GrainEngine());
        voice.fadeTo(1.0f, seconds);

        int fadeSamples = Math.round(seconds * WavFixture.SAMPLE_RATE);
        float expectedStep = 1.0f / fadeSamples;
        float previous = 0.0f;
        for (int i = 0; i < fadeSamples; i++) {
            voice.mixInto(new float[1]);
            float delta = voice.getGain() - previous;
            assertTrue(delta >= 0.0f, "the envelope must be monotonic");
            assertTrue(delta <= expectedStep * 1.001f,
                    "the per-sample step must stay bounded (no zipper): " + delta);
            previous = voice.getGain();
        }

        assertEquals(1.0f, voice.getGain(), 1e-4f, "the fade must reach its target");
        assertEquals(1.0f, voice.getTargetGain(), 0.0f);
        assertFalse(voice.isSilent());
    }

    @Test
    @DisplayName("mixInto at gain 1 reproduces the engine output byte-for-byte")
    void should_ReproduceEngineOutput_When_GainIsOne() {
        GrainEngine directEngine = engine();
        Voice voice = new Voice("test", engine());
        voice.setSample(sample);
        voice.setGain(1.0f);

        float[] direct = new float[64];
        directEngine.read(direct);

        float[] mixed = new float[64];
        voice.mixInto(mixed);

        assertArrayEquals(direct, mixed, 0.0f);
    }

    @Test
    @DisplayName("fades down to silence and then skips the engine")
    void should_FadeToSilence_When_CutRequested() {
        Voice voice = voiceWithSample();
        voice.setGain(1.0f);
        voice.fadeTo(0.0f, TrainSynthesizer.CUT_SECONDS);

        int cutSamples = Math.round(TrainSynthesizer.CUT_SECONDS * WavFixture.SAMPLE_RATE);
        for (int i = 0; i <= cutSamples; i++) {
            voice.mixInto(new float[8]);
        }

        assertEquals(0.0f, voice.getGain(), 0.0f);
        assertTrue(voice.isSilent());

        float[] silent = new float[8];
        voice.mixInto(silent);
        assertArrayEquals(new float[8], silent, 0.0f);
    }

    @Test
    @DisplayName("sums two voices with independent gains")
    void should_MixTwoVoices_When_GainsDiffer() {
        Voice one = voiceWithSample();
        Voice half = voiceWithSample();
        Voice oneAndHalf = voiceWithSample();
        one.setGain(1.0f);
        half.setGain(0.5f);
        oneAndHalf.setGain(1.5f);

        float[] mixed = new float[64];
        one.mixInto(mixed);
        half.mixInto(mixed);

        float[] reference = new float[64];
        oneAndHalf.mixInto(reference);

        assertArrayEquals(reference, mixed, 1e-6f);
    }

    @Test
    @DisplayName("applies material gain in the sum without clamping it to 0..1")
    void should_ApplyMaterialGain_When_PositiveDb() {
        Voice plain = voiceWithSample();
        Voice boosted = voiceWithSample();
        plain.setGain(1.0f);
        boosted.setGain(1.0f);
        boosted.setMaterialGainDb(20.0); // x10, impossible through GrainEngine.setVolume(0..1)

        assertEquals(10.0f, boosted.getMaterialGain(), 1e-6f);

        float[] plainMix = new float[64];
        float[] boostedMix = new float[64];
        plain.mixInto(plainMix);
        boosted.mixInto(boostedMix);

        boolean anySound = false;
        for (int i = 0; i < plainMix.length; i++) {
            assertEquals(plainMix[i] * 10.0f, boostedMix[i], 1e-6f);
            anySound |= plainMix[i] != 0.0f;
        }
        assertTrue(anySound, "the synthetic fixture must produce sound");
    }

    @Test
    @DisplayName("refuses to swap the sample of an audible voice")
    void should_Throw_When_SwappingSampleOfAudibleVoice() throws Exception {
        Voice voice = voiceWithSample();
        AudioSample other = WavFixture.sample(tempDir, "other.wav");
        voice.setGain(1.0f);

        assertThrows(IllegalStateException.class, () -> voice.setSample(other));

        voice.fadeTo(0.0f, 0.0f);
        assertTrue(voice.isSilent());
        voice.setSample(other);
        assertEquals(other, voice.engine().getSample());
    }

    @Test
    @DisplayName("rejects negative or non-finite gains")
    void should_Throw_When_GainIsInvalid() {
        Voice voice = new Voice("test", new GrainEngine());

        assertThrows(IllegalArgumentException.class, () -> voice.setGain(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> voice.fadeTo(Float.NaN, 1.0f));
    }

    private Voice voiceWithSample() {
        Voice voice = new Voice("test", engine());
        voice.setSample(sample);
        return voice;
    }

    private GrainEngine engine() {
        GrainEngine engine = new GrainEngine();
        engine.setSample(sample);
        engine.setSampleRate(sample.getSampleRate());
        engine.setLoopPoints(0f, 1f);
        engine.setSpeed(1.0f);
        engine.setTurnProbability(0f);
        return engine;
    }
}
