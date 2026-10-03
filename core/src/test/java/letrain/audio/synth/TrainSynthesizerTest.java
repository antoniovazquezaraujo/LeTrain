package letrain.audio.synth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import letrain.audio.material.MaterialBank;
import letrain.audio.material.MaterialProfile;
import letrain.audio.material.MaterialProfileParser;
import letrain.audio.material.WavFixture;
import letrain.audio.synth.TrainSynthesizer.SoundMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TrainSynthesizer - sound modes, legacy regression and voice mix bus")
class TrainSynthesizerTest {

    private static final String NOTCH_PROFILE = """
            profile = test
            notch.1 = sound/train/generic/notch-1.wav loop=0.0,0.005
            """;

    @TempDir
    Path tempDir;

    /**
     * {@link TrainSynthesizer#setSample(AudioSample)} replaces the static shared sample (test
     * seam). Restore the real classpath sample afterwards so later test classes keep today's
     * assets.
     */
    @AfterAll
    static void restoreSharedSample() throws Exception {
        URL url = TrainSynthesizer.class.getResource("/sound/train-sound.wav");
        if (url == null) {
            return;
        }
        TrainSynthesizer restorer = new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY);
        restorer.setSample(new AudioSample(url));
    }

    // =====================================================================
    // SoundMode resolution
    // =====================================================================

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"auto, AUTO", "material, MATERIAL", "legacy, LEGACY", "AuTo, AUTO",
            "' material ', MATERIAL", "bogus, LEGACY"})
    @DisplayName("parses letrain.audio.trainSound values")
    void should_ParseSoundMode_When_PropertyValueProvided(String value, SoundMode expected) {
        assertEquals(expected, TrainSynthesizer.resolveModeFromProperty(value));
    }

    @Test
    @DisplayName("defaults to LEGACY when the property is missing or blank")
    void should_DefaultToLegacy_When_PropertyMissing() {
        assertEquals(SoundMode.LEGACY, TrainSynthesizer.resolveModeFromProperty(null));
        assertEquals(SoundMode.LEGACY, TrainSynthesizer.resolveModeFromProperty("   "));
    }

    @Test
    @DisplayName("reads the sound mode from the system property at construction")
    void should_UseSystemProperty_When_Constructed() {
        String previous = System.getProperty(TrainSynthesizer.SOUND_MODE_PROPERTY);
        try {
            System.setProperty(TrainSynthesizer.SOUND_MODE_PROPERTY, "material");

            assertEquals(SoundMode.MATERIAL, new TrainSynthesizer().getSoundMode());
        } finally {
            if (previous == null) {
                System.clearProperty(TrainSynthesizer.SOUND_MODE_PROPERTY);
            } else {
                System.setProperty(TrainSynthesizer.SOUND_MODE_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("honours the injected mode and resolves AUTO against the profile")
    void should_ResolveMode_When_Injected() throws Exception {
        AudioSample fixture = WavFixture.sample(tempDir, "auto.wav");

        assertEquals(SoundMode.LEGACY,
                new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY).getSoundMode());
        assertEquals(SoundMode.MATERIAL,
                new TrainSynthesizer(TrainSynthesizer.SoundMode.MATERIAL, emptyBank())
                        .getSoundMode());
        assertEquals(SoundMode.LEGACY,
                new TrainSynthesizer(TrainSynthesizer.SoundMode.AUTO, emptyBank()).getSoundMode());
        assertEquals(SoundMode.MATERIAL,
                new TrainSynthesizer(TrainSynthesizer.SoundMode.AUTO, bankWithNotch(fixture))
                        .getSoundMode());
    }

    // =====================================================================
    // LEGACY regression: today's behaviour must not change
    // =====================================================================

    @Test
    @DisplayName("LEGACY keeps today's notch pitches and 2 s ramps")
    void should_KeepLegacyNotches_When_LegacyMode() {
        TrainSynthesizer synth = new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY);

        assertEquals(SoundMode.LEGACY, synth.getSoundMode());
        assertEquals(11, synth.getNotches().length);
        assertEquals(1.0f, synth.getNotch(0).cruiseSpeed, 1e-6f);
        for (int i = 1; i <= 10; i++) {
            assertEquals(1.1f + (i - 1) * (0.9f / 9f), synth.getNotch(i).cruiseSpeed, 1e-6f);
            assertEquals(2.0f, synth.getNotch(i).rampTime, 1e-6f);
        }
    }

    @Test
    @DisplayName("LEGACY climbs one notch per ramp and applies the destination loop")
    void should_ClimbOneNotchPerRamp_When_LegacyThrottleRaised() throws Exception {
        TrainSynthesizer synth = new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY);
        AudioSample fixture = WavFixture.sample(tempDir, "legacy.wav");
        synth.setSample(fixture);

        for (int i = 0; i <= 10; i++) {
            float pitch = (i == 0) ? 1.0f : 1.1f + (i - 1) * (0.9f / 9f);
            synth.setNotch(i,
                    new SpeedNotch("Notch " + i, pitch, pitch, pitch, 64, 192, 64, 192, 0.05f));
        }

        AtomicInteger reachedNotch = new AtomicInteger(-1);
        synth.addListener(new TrainSynthesizer.SynthesizerListener() {
            @Override
            public void onSpeedUpdate(float displaySpeed) {}

            @Override
            public void onNotchChanged(int notchIndex) {
                reachedNotch.set(notchIndex);
            }
        });

        synth.startAudio();
        synth.update(10f);
        synth.setThrottle(2);
        assertTrue(synth.isTransitioning(), "0 -> 2 must start a ramp");

        Thread.sleep(100L); // production ramps last 2 s; this fixture uses 50 ms
        synth.update(0f);

        assertEquals(1, reachedNotch.get(), "the ramp must reach notch 1 first, not 2");
        assertEquals(1.1f, synth.getLocoEngine().getSpeed(), 1e-6f);
        assertEquals(0.25, synth.getLocoEngine().getLoopStart(), 1e-6);
        assertEquals(0.75, synth.getLocoEngine().getLoopEnd(), 1e-6);
        assertTrue(synth.isTransitioning(), "the pending 1 -> 2 step starts right after");
    }

    @Test
    @DisplayName("LEGACY never mixes the transition voice (default sound unchanged)")
    void should_NotMixTransitionVoice_When_LegacyMode() throws Exception {
        AudioSample fixture = WavFixture.sample(tempDir, "legacy-silent.wav");
        TrainSynthesizer synth = new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY);
        synth.startAudio();
        synth.update(10f);
        synth.getLocoEngine().setVolume(0f);
        synth.getCoachEngine().setVolume(0f);

        Voice trans = synth.getTransVoice();
        trans.setSample(fixture);
        trans.setGain(1.0f);

        float[] buffer = new float[256];
        assertTrue(synth.read(buffer));
        assertArrayEquals(new float[256], buffer, 0.0f);
    }

    // =====================================================================
    // MATERIAL mix bus
    // =====================================================================

    @Test
    @DisplayName("MATERIAL reproduces the legacy path byte-for-byte (no audible change)")
    void should_RenderSampleIdentical_When_MaterialRunsTheLegacyStateMachine() {
        TrainSynthesizer legacy = new TrainSynthesizer(TrainSynthesizer.SoundMode.LEGACY);
        TrainSynthesizer material =
                new TrainSynthesizer(TrainSynthesizer.SoundMode.MATERIAL, emptyBank());
        legacy.setLocoRandomness(0f, 1f);
        legacy.setCoachRandomness(0f, 1f);
        material.setLocoRandomness(0f, 1f);
        material.setCoachRandomness(0f, 1f);
        legacy.startAudio();
        material.startAudio();
        legacy.update(10f);
        material.update(10f);

        float[] legacyBuffer = new float[1024];
        float[] materialBuffer = new float[1024];
        assertTrue(legacy.read(legacyBuffer));
        assertTrue(material.read(materialBuffer));
        assertArrayEquals(legacyBuffer, materialBuffer, 0.0f,
                "the mix bus at gain 1 must equal today's direct engine sum");
    }

    @Test
    @DisplayName("MATERIAL mixes the transition voice and applies masterVolume after the sum")
    void should_MixTransitionVoiceAndMasterVolume_When_MaterialMode() throws Exception {
        AudioSample fixture = WavFixture.sample(tempDir, "material.wav");
        MaterialBank bank = bankWithNotch(fixture);

        TrainSynthesizer full = new TrainSynthesizer(TrainSynthesizer.SoundMode.MATERIAL, bank);
        TrainSynthesizer half = new TrainSynthesizer(TrainSynthesizer.SoundMode.MATERIAL, bank);
        prepareTransitionVoice(full, fixture);
        prepareTransitionVoice(half, fixture);
        full.setMasterVolume(1.0f);
        half.setMasterVolume(0.5f);

        float[] fullBuffer = new float[256];
        float[] halfBuffer = new float[256];
        assertTrue(full.read(fullBuffer));
        assertTrue(half.read(halfBuffer));

        boolean anySound = false;
        for (int i = 0; i < fullBuffer.length; i++) {
            assertEquals(fullBuffer[i] * 0.5f, halfBuffer[i], 1e-6f);
            anySound |= fullBuffer[i] != 0.0f;
        }
        assertTrue(anySound, "the transition voice must contribute to the mix");
    }

    @Test
    @DisplayName("setSample forces LEGACY so the injected-sample seam keeps today's path")
    void should_ForceLegacy_When_SetSampleCalled() throws Exception {
        AudioSample fixture = WavFixture.sample(tempDir, "forced-legacy.wav");
        TrainSynthesizer synth =
                new TrainSynthesizer(TrainSynthesizer.SoundMode.MATERIAL, emptyBank());

        assertEquals(SoundMode.MATERIAL, synth.getSoundMode());

        synth.setSample(fixture);

        assertEquals(SoundMode.LEGACY, synth.getSoundMode());
    }

    private static void prepareTransitionVoice(TrainSynthesizer synth, AudioSample fixture) {
        synth.startAudio();
        synth.update(10f);
        synth.getLocoEngine().setVolume(0f);
        synth.getCoachEngine().setVolume(0f);
        Voice trans = synth.getTransVoice();
        trans.setSample(fixture);
        trans.setGain(1.0f);
    }

    private static MaterialBank emptyBank() {
        return new MaterialBank(MaterialProfile.empty("test"), id -> Optional.empty());
    }

    private static MaterialBank bankWithNotch(AudioSample sample) {
        MaterialProfile profile = MaterialProfileParser.parse(NOTCH_PROFILE);
        return new MaterialBank(profile, id -> Optional.of(sample));
    }
}
