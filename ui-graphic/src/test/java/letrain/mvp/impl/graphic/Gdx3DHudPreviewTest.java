package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.command.GrammarReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("3D HUD: quick-reference preview text (#828)")
class Gdx3DHudPreviewTest {

    @Test
    @DisplayName("COMMAND previews its snippet and scope")
    void commandPreview() {
        GrammarReference.Node command = GrammarReference.Node.command("signal limit",
                "signal <signal> set limit <limit>;", GrammarReference.Scope.BODY_ONLY);

        String preview = Gdx3DHud.previewFor(command, true);

        assertTrue(preview.contains("signal <signal> set limit <limit>;"));
        assertTrue(preview.contains("Scope: program top level"));
    }

    @Test
    @DisplayName("Scenario/Config previews show the snippet without a PROGRAM scope hint")
    void commandPreviewOutsideProgrammingTab() {
        GrammarReference.Node command = GrammarReference.Node.command("new station", "new st;",
                GrammarReference.Scope.BODY_ONLY);

        String preview = Gdx3DHud.previewFor(command, false);

        assertTrue(preview.contains("new st;"));
        assertFalse(preview.contains("Scope:"));
    }

    @Test
    @DisplayName("INFO previews its prose")
    void infoPreview() {
        GrammarReference.Node info =
                GrammarReference.Node.info("waypoint rules", "Two waypoints are required.");

        assertEquals("Two waypoints are required.", Gdx3DHud.previewFor(info, true));
    }

    @Test
    @DisplayName("structural nodes have no preview")
    void structuralNodesHaveNoPreview() {
        assertEquals("", Gdx3DHud.previewFor(GrammarReference.Node.heading("ACTIONS"), true));
        assertEquals("", Gdx3DHud.previewFor(GrammarReference.Node.group("train"), true));
    }
}
