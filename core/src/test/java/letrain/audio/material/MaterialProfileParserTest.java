package letrain.audio.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import letrain.audio.material.MaterialId.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MaterialProfileParser - ADR-029 §5 descriptor DSL")
class MaterialProfileParserTest {

    private static final String SAMPLE_PROFILE = """
            # ADR-029 §5 sample profile
            profile     = generic
            packVersion = 3

            notch.1   = sound/train/generic/notch-1.wav  loop=4.477937,4.252766  gain=-0.57
            trans.2-5 = sound/train/generic/trans-2-5.wav effort=high torque=loaded
            idle      = sound/train/generic/idle.wav loop=14.579841,3.624603 gain=1.98
            start     = sound/train/generic/start.wav
            """;

    @Test
    @DisplayName("parses the profile header")
    void should_ParseHeader_When_Declared() {
        MaterialProfile profile = MaterialProfileParser.parse(SAMPLE_PROFILE);

        assertEquals("generic", profile.name());
        assertEquals(3, profile.packVersion());
        assertEquals(4, profile.size());
    }

    @Test
    @DisplayName("parses loop and gain of a notch")
    void should_ParseLoopAndGain_When_NotchDeclared() {
        MaterialProfile profile = MaterialProfileParser.parse(SAMPLE_PROFILE);
        MaterialRef ref = profile.material(MaterialId.notch(1)).orElseThrow();

        assertEquals("sound/train/generic/notch-1.wav", ref.path());
        LoopPoints loop = ref.loop().orElseThrow();
        assertEquals(4.477937, loop.startSeconds(), 1e-9);
        assertEquals(4.252766, loop.lengthSeconds(), 1e-9);
        assertEquals(4.477937 + 4.252766, loop.endSeconds(), 1e-9);
        assertEquals(-0.57, ref.gainDb(), 1e-9);
        assertTrue(ref.effort().isEmpty());
        assertTrue(ref.torque().isEmpty());
    }

    @Test
    @DisplayName("parses informational effort and torque labels of one-shots")
    void should_ParseEffortAndTorque_When_TransitionDeclared() {
        MaterialProfile profile = MaterialProfileParser.parse(SAMPLE_PROFILE);
        MaterialRef ref = profile.material(MaterialId.transition(2, 5)).orElseThrow();

        assertEquals("high", ref.effort().orElseThrow());
        assertEquals("loaded", ref.torque().orElseThrow());
        assertTrue(ref.loop().isEmpty(), "one-shots have no loop");
        assertEquals(0.0, ref.gainDb(), 1e-9);
    }

    @Test
    @DisplayName("defaults gain to 0.0 when not declared")
    void should_DefaultGainToZero_When_NotDeclared() {
        MaterialProfile profile = MaterialProfileParser.parse(SAMPLE_PROFILE);

        assertEquals(0.0, profile.material(MaterialId.of(Role.START)).orElseThrow().gainDb(), 1e-9);
    }

    @Test
    @DisplayName("ignores comments, inline comments and blank lines")
    void should_IgnoreCommentsAndBlankLines() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                # full-line comment

                profile = generic
                notch.2 = sound/train/generic/notch-2.wav loop=1.0,2.0 # inline comment

                """);

        assertEquals(1, profile.size());
        assertTrue(profile.material(MaterialId.notch(2)).isPresent());
    }

    @Test
    @DisplayName("skips unknown material ids without failing")
    void should_SkipUnknownMaterialId() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                turbo   = sound/train/generic/turbo.wav loop=1.0,2.0
                notch.1 = sound/train/generic/notch-1.wav loop=1.0,2.0
                """);

        assertEquals(1, profile.size());
        assertTrue(profile.has(MaterialId.notch(1)), "known id must survive");
    }

    @Test
    @DisplayName("skips a line without '='")
    void should_SkipMalformedLine_When_EqualsMissing() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                this line has no equals sign
                notch.1 = sound/train/generic/notch-1.wav loop=1.0,2.0
                """);

        assertEquals(1, profile.size());
    }

    @Test
    @DisplayName("ignores unknown metadata but keeps the material")
    void should_KeepMaterial_When_MetadataIsUnknown() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                notch.1 = sound/train/generic/notch-1.wav loop=1.0,2.0 color=red
                """);

        MaterialRef ref = profile.material(MaterialId.notch(1)).orElseThrow();
        assertEquals("sound/train/generic/notch-1.wav", ref.path());
        assertTrue(ref.hasLoop());
    }

    @Test
    @DisplayName("drops a loop material when loop is missing")
    void should_DropLoopMaterial_When_LoopMissing() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                notch.1 = sound/train/generic/notch-1.wav gain=0.0
                """);

        assertTrue(profile.material(MaterialId.notch(1)).isEmpty());
    }

    @Test
    @DisplayName("drops a loop material when the loop window is inverted")
    void should_DropLoopMaterial_When_LoopLengthIsNegative() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                notch.1 = sound/train/generic/notch-1.wav loop=5.0,-1.0
                """);

        assertTrue(profile.material(MaterialId.notch(1)).isEmpty());
    }

    @Test
    @DisplayName("drops a loop material when the loop is malformed")
    void should_DropLoopMaterial_When_LoopMalformed() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                notch.1 = sound/train/generic/notch-1.wav loop=abc
                notch.2 = sound/train/generic/notch-2.wav loop=onlyonevalue
                """);

        assertEquals(0, profile.size());
    }

    @Test
    @DisplayName("ignores loop= on one-shots but keeps the material")
    void should_IgnoreLoop_When_OneShotDeclaresIt() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                start = sound/train/generic/start.wav loop=1.0,2.0
                """);

        MaterialRef ref = profile.material(MaterialId.of(Role.START)).orElseThrow();
        assertTrue(ref.loop().isEmpty());
    }

    @Test
    @DisplayName("defaults gain to 0.0 when gain is malformed")
    void should_DefaultGain_When_GainMalformed() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                start = sound/train/generic/start.wav gain=loud
                """);

        assertEquals(0.0, profile.material(MaterialId.of(Role.START)).orElseThrow().gainDb(), 1e-9);
    }

    @Test
    @DisplayName("keeps the last definition when an id is duplicated")
    void should_KeepLastDefinition_When_Duplicate() {
        MaterialProfile profile = MaterialProfileParser.parse("""
                profile = generic
                notch.3 = sound/train/generic/notch-3.wav loop=1.0,2.0 gain=0.0
                notch.3 = sound/train/generic/notch-3.wav loop=1.0,2.0 gain=0.44
                """);

        assertEquals(1, profile.size());
        assertEquals(0.44, profile.material(MaterialId.notch(3)).orElseThrow().gainDb(), 1e-9);
    }

    @Test
    @DisplayName("returns an empty generic profile for null text")
    void should_ReturnEmptyProfile_When_TextIsNull() {
        MaterialProfile profile = MaterialProfileParser.parse((String) null);

        assertEquals("generic", profile.name());
        assertEquals(0, profile.packVersion());
        assertTrue(profile.materials().isEmpty());
    }

    @Test
    @DisplayName("defaults the header when profile and packVersion are absent")
    void should_DefaultHeader_When_Absent() {
        MaterialProfile profile = MaterialProfileParser
                .parse("notch.1 = sound/train/generic/notch-1.wav loop=1.0,2.0");

        assertEquals("generic", profile.name());
        assertEquals(0, profile.packVersion());
    }

    @Test
    @DisplayName("parses the shipped generic profile with all 34 materials")
    void should_ParseShippedGenericProfile() throws Exception {
        MaterialProfile profile = readProfile("/sound/profiles/generic.profile");

        assertEquals("generic", profile.name());
        assertEquals(0, profile.packVersion());
        assertEquals(34, profile.size(), "10 notches + 20 transitions + idle/start/stop/rolling");

        for (int notch = 1; notch <= 10; notch++) {
            assertTrue(profile.material(MaterialId.notch(notch)).orElseThrow().hasLoop(),
                    "notch-" + notch + " must declare loop points");
        }
        assertTrue(profile.has(MaterialId.transition(0, 1)), "trans.0-1 is shipped");
        assertTrue(profile.has(MaterialId.transition(1, 0)), "trans.1-0 is shipped");
        assertEquals(23.521769, profile.material(MaterialId.of(Role.ROLLING)).orElseThrow().loop()
                .orElseThrow().startSeconds(), 1e-9);
        assertFalse(profile.material(MaterialId.of(Role.START)).orElseThrow().hasLoop());
        assertFalse(profile.has(MaterialId.of(Role.HORN)), "horn is reserved, no material yet");
    }

    private static MaterialProfile readProfile(String resource) throws Exception {
        try (InputStream in = MaterialProfileParserTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing classpath resource " + resource);
            return MaterialProfileParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
}
