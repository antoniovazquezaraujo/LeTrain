package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 compatibility policy: strict for new input, tolerant when loading text from disk. This
 * normalizer rewrites legacy comma-less waypoint actions so old scenarios, saved programs and
 * journals keep loading.
 */
@DisplayName("Legacy waypoint syntax normalizer (ADR-022 compatibility)")
class LegacyScriptNormalizerTest {

    @Test
    @DisplayName("adds commas between two or more waypoint actions")
    void addsCommasBetweenActions() {
        assertEquals("add station 2 reverse, unload",
                LegacyScriptNormalizer.normalize("add station 2 reverse unload"));
        assertEquals("add station 2 speed 0, wait 3, speed 3",
                LegacyScriptNormalizer.normalize("add station 2 speed 0 wait 3 speed 3"));
        assertEquals("add sensor 5 unload, wait 2",
                LegacyScriptNormalizer.normalize("add sensor 5 unload wait 2"));
    }

    @Test
    @DisplayName("recognizes actions case-insensitively and emits lowercase keywords")
    void normalizesCase() {
        assertEquals("add station \"B\" speed 0, wait 3, speed 3",
                LegacyScriptNormalizer.normalize("add station \"B\" SPEED 0 WAIT 3 SPEED 3"));
    }

    @Test
    @DisplayName("preserves indentation, quoted names with spaces and the trailing separator")
    void preservesLayout() {
        assertEquals("      add station \"Central Mine\" reverse, unload;",
                LegacyScriptNormalizer.normalize("      add station \"Central Mine\" reverse unload;"));
        assertEquals("add station 1 load, unload }",
                LegacyScriptNormalizer.normalize("add station 1 load unload }"));
    }

    @Test
    @DisplayName("leaves lines that do not need rewriting untouched (same reference)")
    void leavesUntouched() {
        String single = "add station 2 reverse";
        String noAction = "add station 2";
        String commaForm = "add station 2 arrival 9:00, load, departure 9:20";
        String unknown = "add station 2 frobnicate";
        String otherCommand = "go 0,0; face e; write 2;";
        String malformedWait = "add station 2 wait";

        assertSame(single, LegacyScriptNormalizer.normalize(single));
        assertSame(noAction, LegacyScriptNormalizer.normalize(noAction));
        assertSame(commaForm, LegacyScriptNormalizer.normalize(commaForm));
        assertSame(unknown, LegacyScriptNormalizer.normalize(unknown));
        assertSame(otherCommand, LegacyScriptNormalizer.normalize(otherCommand));
        assertSame(malformedWait, LegacyScriptNormalizer.normalize(malformedWait));
    }

    @Test
    @DisplayName("normalization is idempotent and line-based")
    void isIdempotent() {
        String text = "create itinerary \"x\" {\n  add station 2 reverse unload\n}\n";
        String once = LegacyScriptNormalizer.normalize(text);

        assertEquals("create itinerary \"x\" {\n  add station 2 reverse, unload\n}\n", once);
        assertSame(once, LegacyScriptNormalizer.normalize(once));
    }
}
