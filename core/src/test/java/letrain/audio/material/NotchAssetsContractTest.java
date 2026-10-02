package letrain.audio.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import letrain.audio.material.MaterialId.Role;
import letrain.audio.synth.AudioSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract test for the 34 real assets shipped in {@code sound/train/generic/} and the generic
 * profile (ADR-029 §7). It freezes format (mono 44.1 kHz PCM16), exact frame counts from the audit
 * and the measured loop declarations, so a bad commit cannot silently corrupt the material.
 */
@DisplayName("Notch assets - real material contract (ADR-029 §7)")
class NotchAssetsContractTest {

    private static final String GENERIC_DIR = "sound/train/generic/";
    private static final String PROFILE_RESOURCE = "/sound/profiles/generic.profile";

    private static final int NOTCH_FRAMES = 385024;
    private static final int TRANS_UP_FRAMES = 220500;
    private static final int TRANS_DOWN_FRAMES = 88200;
    private static final int IDLE_FRAMES = 802816;
    private static final int START_FRAMES = 278528;
    private static final int STOP_FRAMES = 198041;
    private static final int ROLLING_FRAMES = 2162721;

    private static final Map<MaterialId, String> EXPECTED_MATERIALS = expectedMaterials();

    static Stream<Arguments> expectedAssets() {
        List<Arguments> assets = new ArrayList<>();
        for (int notch = 1; notch <= 10; notch++) {
            assets.add(Arguments.of("notch-" + notch + ".wav", NOTCH_FRAMES));
        }
        for (int from = 0; from <= 9; from++) {
            assets.add(Arguments.of("trans-" + from + "-" + (from + 1) + ".wav", TRANS_UP_FRAMES));
        }
        for (int from = 1; from <= 10; from++) {
            assets.add(
                    Arguments.of("trans-" + from + "-" + (from - 1) + ".wav", TRANS_DOWN_FRAMES));
        }
        assets.add(Arguments.of("idle.wav", IDLE_FRAMES));
        assets.add(Arguments.of("start.wav", START_FRAMES));
        assets.add(Arguments.of("stop.wav", STOP_FRAMES));
        assets.add(Arguments.of("rolling.wav", ROLLING_FRAMES));
        return assets.stream();
    }

    static Stream<String> expectedFileNames() {
        return expectedAssets().map(arguments -> (String) arguments.get()[0]);
    }

    static Stream<Arguments> expectedLoops() {
        return Stream.of(Arguments.of("notch-1", 4.477937, 4.252766),
                Arguments.of("notch-2", 6.301451, 2.429252),
                Arguments.of("notch-3", 6.684286, 2.046417),
                Arguments.of("notch-4", 6.685102, 2.045601),
                Arguments.of("notch-5", 4.734195, 3.996508),
                Arguments.of("notch-6", 3.747370, 4.983333),
                Arguments.of("notch-7", 4.629683, 4.101020),
                Arguments.of("notch-8", 7.763832, 0.966871),
                Arguments.of("notch-9", 3.643175, 5.087528),
                Arguments.of("notch-10", 7.508707, 1.221995),
                Arguments.of("idle", 14.579841, 3.624603),
                Arguments.of("rolling", 22.059252, 3.039093));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedFileNames")
    @DisplayName("ships every asset as mono 44.1 kHz PCM16")
    void should_BeMonoPcm16At44100_When_HeaderIsRead(String fileName) throws Exception {
        WavHeader header = WavHeader.read(resource(fileName));

        assertEquals(1, header.audioFormat(), fileName + " PCM format tag");
        assertEquals(1, header.channels(), fileName + " channels");
        assertEquals(44100, header.sampleRate(), fileName + " sample rate");
        assertEquals(16, header.bitsPerSample(), fileName + " bits per sample");
    }

    @ParameterizedTest(name = "{0} = {1} frames")
    @MethodSource("expectedAssets")
    @DisplayName("decodes every asset with the measured frame count")
    void should_HaveExactFrames_When_Decoded(String fileName, int expectedFrames) throws Exception {
        AudioSample sample = new AudioSample(resource(fileName));

        assertEquals(expectedFrames, sample.getLength(), fileName + " frame count");
        assertEquals(44100.0f, sample.getSampleRate(), fileName + " sample rate");
    }

    @Test
    @DisplayName("declares all 34 canonical materials and every path resolves on the classpath")
    void should_DeclareEveryAsset_When_ProfileIsParsed() throws Exception {
        MaterialProfile profile = loadGenericProfile();

        assertEquals(34, EXPECTED_MATERIALS.size(), "10 notches + 20 transitions + 4 singles");
        assertEquals(EXPECTED_MATERIALS.size(), profile.size(), "declared material count");
        for (Map.Entry<MaterialId, String> entry : EXPECTED_MATERIALS.entrySet()) {
            MaterialRef ref = profile.material(entry.getKey())
                    .orElseThrow(() -> new AssertionError("undeclared material " + entry.getKey()));
            assertTrue(ref.path().endsWith("/" + entry.getValue()),
                    entry.getKey() + " path should end with " + entry.getValue());
            assertNotNull(getClass().getResource("/" + ref.path()),
                    "missing classpath resource " + ref.path());
        }
    }

    @ParameterizedTest(name = "{0} loop {1},{2}")
    @MethodSource("expectedLoops")
    @DisplayName("declares the measured loop window for idle, rolling and every notch")
    void should_DeclareMeasuredLoop_When_ProfileIsParsed(String token, double start, double length)
            throws Exception {
        MaterialProfile profile = loadGenericProfile();
        MaterialId id = MaterialId.parse(token).orElseThrow();

        LoopPoints loop = profile.material(id).orElseThrow().loop()
                .orElseThrow(() -> new AssertionError("missing loop for " + token));

        assertEquals(start, loop.startSeconds(), 1e-9, token + " loop start");
        assertEquals(length, loop.lengthSeconds(), 1e-9, token + " loop length");
    }

    @Test
    @DisplayName("keeps every declared loop inside its WAV duration")
    void should_KeepLoopsInsideFiles_When_ProfileIsParsed() throws Exception {
        MaterialProfile profile = loadGenericProfile();

        for (Map.Entry<MaterialId, String> entry : EXPECTED_MATERIALS.entrySet()) {
            MaterialId id = entry.getKey();
            if (!id.isLoop()) {
                continue;
            }
            MaterialRef ref = profile.material(id).orElseThrow();
            if (ref.loop().isEmpty()) {
                continue;
            }
            LoopPoints loop = ref.loop().orElseThrow();
            double duration = expectedFrames(entry.getValue()) / 44100.0;
            assertTrue(loop.startSeconds() >= 0.0, id + " loop start must be >= 0");
            assertTrue(loop.endSeconds() <= duration + 1.0 / 44100.0,
                    id + " loop end " + loop.endSeconds() + " beyond file duration " + duration);
        }
    }

    @Test
    @DisplayName("keeps transitions directional and leaves reserved ids without material")
    void should_KeepTaxonomy_When_ProfileIsParsed() throws Exception {
        MaterialProfile profile = loadGenericProfile();

        assertTrue(profile.has(MaterialId.transition(1, 2)), "trans-1-2 is shipped");
        assertTrue(profile.has(MaterialId.transition(2, 1)), "trans-2-1 is shipped");
        assertFalse(profile.has(MaterialId.transition(1, 3)), "compound trans-1-3 has no material");
        assertTrue(profile.has(MaterialId.transition(0, 1)), "trans-0-1 is shipped");
        assertTrue(profile.has(MaterialId.transition(1, 0)), "trans-1-0 is shipped");
        assertFalse(profile.has(MaterialId.of(Role.BRAKES)), "brakes has no measured material yet");
        assertFalse(profile.has(MaterialId.of(Role.HORN)), "horn is reserved");
    }

    @Test
    @DisplayName("resolves the 0-1 and 1-0 start transitions as loopless one-shots with gain 1.98")
    void should_ResolveStartTransitions_When_ProfileIsParsed() throws Exception {
        MaterialProfile profile = loadGenericProfile();

        MaterialRef up = profile.material(MaterialId.transition(0, 1)).orElseThrow();
        MaterialRef down = profile.material(MaterialId.transition(1, 0)).orElseThrow();

        assertEquals("sound/train/generic/trans-0-1.wav", up.path());
        assertEquals("sound/train/generic/trans-1-0.wav", down.path());
        assertEquals(1.98, up.gainDb(), 1e-9, "trans.0-1 gain");
        assertEquals(1.98, down.gainDb(), 1e-9, "trans.1-0 gain");
        assertTrue(up.loop().isEmpty(), "trans.0-1 is a one-shot: no loop=");
        assertTrue(down.loop().isEmpty(), "trans.1-0 is a one-shot: no loop=");
        assertFalse(MaterialId.transition(0, 1).isLoop(), "0-1 transition must not be a loop role");
        assertFalse(MaterialId.transition(1, 0).isLoop(), "1-0 transition must not be a loop role");
    }

    @Test
    @DisplayName("resolves a notch through the shared classpath bank")
    void should_ResolveThroughSharedBank_When_Requested() {
        assertTrue(MaterialBank.shared().loop(1).isPresent(),
                "shared bank must resolve notch-1 from the default profile");
    }

    private static Map<MaterialId, String> expectedMaterials() {
        Map<MaterialId, String> materials = new LinkedHashMap<>();
        for (int notch = 1; notch <= 10; notch++) {
            materials.put(MaterialId.notch(notch), "notch-" + notch + ".wav");
        }
        for (int from = 0; from <= 9; from++) {
            materials.put(MaterialId.transition(from, from + 1),
                    "trans-" + from + "-" + (from + 1) + ".wav");
        }
        for (int from = 1; from <= 10; from++) {
            materials.put(MaterialId.transition(from, from - 1),
                    "trans-" + from + "-" + (from - 1) + ".wav");
        }
        materials.put(MaterialId.of(Role.IDLE), "idle.wav");
        materials.put(MaterialId.of(Role.START), "start.wav");
        materials.put(MaterialId.of(Role.STOP), "stop.wav");
        materials.put(MaterialId.of(Role.ROLLING), "rolling.wav");
        return Map.copyOf(materials);
    }

    private static int expectedFrames(String fileName) {
        if (fileName.equals("idle.wav")) {
            return IDLE_FRAMES;
        }
        if (fileName.equals("start.wav")) {
            return START_FRAMES;
        }
        if (fileName.equals("stop.wav")) {
            return STOP_FRAMES;
        }
        if (fileName.equals("rolling.wav")) {
            return ROLLING_FRAMES;
        }
        if (fileName.startsWith("notch-")) {
            return NOTCH_FRAMES;
        }
        if (fileName.startsWith("trans-")) {
            String[] endpoints =
                    fileName.substring("trans-".length(), fileName.length() - 4).split("-");
            int from = Integer.parseInt(endpoints[0]);
            int to = Integer.parseInt(endpoints[1]);
            return to > from ? TRANS_UP_FRAMES : TRANS_DOWN_FRAMES;
        }
        throw new IllegalArgumentException("unknown asset " + fileName);
    }

    private URL resource(String fileName) {
        URL url = getClass().getResource("/" + GENERIC_DIR + fileName);
        assertNotNull(url, "missing asset: " + fileName);
        return url;
    }

    private static MaterialProfile loadGenericProfile() throws Exception {
        try (InputStream in = NotchAssetsContractTest.class.getResourceAsStream(PROFILE_RESOURCE)) {
            assertNotNull(in, "missing " + PROFILE_RESOURCE);
            return MaterialProfileParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Minimal RIFF header reader: enough to freeze channels, sample rate and bit depth. */
    private record WavHeader(int audioFormat, int channels, int sampleRate, int bitsPerSample) {

        static WavHeader read(URL url) throws IOException {
            try (InputStream in = url.openStream();
                    DataInputStream data = new DataInputStream(in)) {
                byte[] chunkId = new byte[4];
                data.readFully(chunkId);
                assertEquals("RIFF", ascii(chunkId));
                readIntLe(data); // RIFF size
                data.readFully(chunkId);
                assertEquals("WAVE", ascii(chunkId));

                while (true) {
                    data.readFully(chunkId);
                    String id = ascii(chunkId);
                    int size = readIntLe(data);
                    if ("fmt ".equals(id)) {
                        int audioFormat = readShortLe(data);
                        int channels = readShortLe(data);
                        int sampleRate = readIntLe(data);
                        readIntLe(data); // byte rate
                        readShortLe(data); // block align
                        int bitsPerSample = readShortLe(data);
                        return new WavHeader(audioFormat, channels, sampleRate, bitsPerSample);
                    }
                    data.skipNBytes(size + (size % 2));
                }
            }
        }

        private static String ascii(byte[] bytes) {
            return new String(bytes, StandardCharsets.US_ASCII);
        }

        private static int readShortLe(DataInputStream in) throws IOException {
            return in.readUnsignedByte() | (in.readUnsignedByte() << 8);
        }

        private static int readIntLe(DataInputStream in) throws IOException {
            return in.readUnsignedByte() | (in.readUnsignedByte() << 8)
                    | (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 24);
        }
    }
}
