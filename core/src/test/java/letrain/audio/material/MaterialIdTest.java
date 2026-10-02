package letrain.audio.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.audio.material.MaterialId.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MaterialId - canonical ids, roles and legacy aliases (ADR-029 §1)")
class MaterialIdTest {

    @Test
    @DisplayName("parses a canonical notch id")
    void should_ParseNotch_When_CanonicalToken() {
        MaterialId id = MaterialId.parse("notch-3").orElseThrow();

        assertEquals(Role.NOTCH, id.role());
        assertEquals(3, id.notch().orElseThrow());
        assertEquals("notch-3", id.canonical());
        assertEquals("notch-3", id.toString());
    }

    @Test
    @DisplayName("accepts the dotted profile key for notches")
    void should_ParseNotch_When_DottedProfileKey() {
        assertEquals(MaterialId.notch(7), MaterialId.parse("notch.7").orElseThrow());
    }

    @Test
    @DisplayName("parses directional transitions and keeps the direction")
    void should_KeepDirection_When_ParsingTransitions() {
        MaterialId up = MaterialId.parse("trans-2-5").orElseThrow();
        MaterialId down = MaterialId.parse("trans-5-2").orElseThrow();

        assertEquals(2, up.from().orElseThrow());
        assertEquals(5, up.to().orElseThrow());
        assertEquals(5, down.from().orElseThrow());
        assertEquals(2, down.to().orElseThrow());
        assertNotEquals(up, down, "trans-2-5 and trans-5-2 are different materials");
    }

    @Test
    @DisplayName("accepts the dotted profile key for transitions")
    void should_ParseTransition_When_DottedProfileKey() {
        assertEquals(MaterialId.transition(9, 10), MaterialId.parse("trans.9-10").orElseThrow());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"idle, IDLE", "start, START", "stop, STOP", "rolling, ROLLING",
            "brakes, BRAKES", "horn, HORN"})
    @DisplayName("parses every singleton role")
    void should_ParseSingletonRole_When_TokenMatches(String token, Role expectedRole) {
        MaterialId id = MaterialId.parse(token).orElseThrow();

        assertEquals(expectedRole, id.role());
        assertEquals(token, id.canonical());
        assertTrue(id.notch().isEmpty());
        assertTrue(id.from().isEmpty());
        assertTrue(id.to().isEmpty());
    }

    @Test
    @DisplayName("normalizes the legacy aliases wagons and train-brakes")
    void should_NormalizeLegacyAliases() {
        assertEquals(MaterialId.of(Role.ROLLING), MaterialId.parse("wagons").orElseThrow());
        assertEquals(MaterialId.of(Role.BRAKES), MaterialId.parse("train-brakes").orElseThrow());
    }

    @Test
    @DisplayName("normalizes case and surrounding whitespace")
    void should_NormalizeCaseAndWhitespace() {
        assertEquals(MaterialId.notch(4), MaterialId.parse("  NOTCH-4 ").orElseThrow());
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"", " ", "turbo", "notch-0", "notch-11", "notch-x", "notch",
            "trans-1", "trans-1-1", "trans-11-1", "trans-1-11", "trans--2", "trans-1-2-3"})
    @DisplayName("returns empty for unknown or malformed tokens")
    void should_ReturnEmpty_When_TokenIsUnknownOrMalformed(String token) {
        assertTrue(MaterialId.parse(token).isEmpty());
    }

    @Test
    @DisplayName("returns empty for null instead of throwing")
    void should_ReturnEmpty_When_TokenIsNull() {
        assertTrue(MaterialId.parse(null).isEmpty());
    }

    @Test
    @DisplayName("throws when a notch is out of range")
    void should_Throw_When_NotchOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> MaterialId.notch(0));
        assertThrows(IllegalArgumentException.class, () -> MaterialId.notch(11));
    }

    @Test
    @DisplayName("throws when a transition is invalid")
    void should_Throw_When_TransitionInvalid() {
        assertThrows(IllegalArgumentException.class, () -> MaterialId.transition(1, 1));
        assertThrows(IllegalArgumentException.class, () -> MaterialId.transition(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> MaterialId.transition(1, 11));
    }

    @Test
    @DisplayName("throws when of() is used with parameterized roles")
    void should_Throw_When_OfParameterizedRole() {
        assertThrows(IllegalArgumentException.class, () -> MaterialId.of(Role.NOTCH));
        assertThrows(IllegalArgumentException.class, () -> MaterialId.of(Role.TRANSITION));
    }

    @Test
    @DisplayName("flags exactly the roles that must loop")
    void should_FlagLoopRoles() {
        assertTrue(MaterialId.notch(1).isLoop());
        assertTrue(MaterialId.of(Role.IDLE).isLoop());
        assertTrue(MaterialId.of(Role.ROLLING).isLoop());
        assertTrue(MaterialId.of(Role.BRAKES).isLoop());

        assertFalse(MaterialId.transition(1, 2).isLoop());
        assertFalse(MaterialId.of(Role.START).isLoop());
        assertFalse(MaterialId.of(Role.STOP).isLoop());
        assertFalse(MaterialId.of(Role.HORN).isLoop());
    }
}
