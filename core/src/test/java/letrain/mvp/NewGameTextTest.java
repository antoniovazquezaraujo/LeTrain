package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Issue #746: shared New Game wording, so the target/measured lines cannot drift in 2D/3D. */
@DisplayName("New Game wording (#746)")
class NewGameTextTest {

    @Test
    @DisplayName("target and measured always show the three proportions (field is the remainder)")
    void should_ShowProportions() {
        assertEquals("Target: water 25% · mountain 10% · field 65%",
                NewGameText.targetLine(25, 10));
        assertEquals("  measured: 24%/9%/67%", NewGameText.measuredSuffix(24, 9));
        assertEquals("Target: water 25% · mountain 10% · field 65%  measured: 24%/9%/67%",
                NewGameText.terrainStatusLine(25, 10, 24, 9));
    }

    @Test
    @DisplayName("the status line fits the 70-column terminal the dialog is validated on (#749)")
    void should_FitTheMinimumTerminal() {
        assertTrue(NewGameText.terrainStatusLine(95, 0, 95, 0).length() <= 70,
                NewGameText.terrainStatusLine(95, 0, 95, 0));
        assertTrue(NewGameText.terrainStatusLine(95, 3, 95, 3).length() <= 70,
                NewGameText.terrainStatusLine(95, 3, 95, 3));
        assertTrue(NewGameText.NO_PREVIEW.length() > 0);
    }

    @Test
    @DisplayName("the started notice carries the seed")
    void should_AnnounceTheSeed() {
        assertEquals("New game started (seed 12345)", NewGameText.startedMessage(12345));
    }

    @Test
    @DisplayName("the confirm wording warns about the pending #417 save flow")
    void should_WarnAboutUnsavedChanges() {
        assertTrue(NewGameText.CONFIRM_MESSAGE.contains("Unsaved changes"),
                NewGameText.CONFIRM_MESSAGE);
    }
}
