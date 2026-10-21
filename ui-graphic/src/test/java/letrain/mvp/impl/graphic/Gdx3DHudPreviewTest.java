package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        String preview = Gdx3DHud.previewFor(command);

        assertTrue(preview.contains("signal <signal> set limit <limit>;"));
        assertTrue(preview.contains("Scope: top-level only"));
    }

    @Test
    @DisplayName("INFO previews its prose")
    void infoPreview() {
        GrammarReference.Node info =
                GrammarReference.Node.info("waypoint rules", "Two waypoints are required.");

        assertEquals("Two waypoints are required.", Gdx3DHud.previewFor(info));
    }

    @Test
    @DisplayName("structural nodes have no preview")
    void structuralNodesHaveNoPreview() {
        assertEquals("", Gdx3DHud.previewFor(GrammarReference.Node.heading("ACTIONS")));
        assertEquals("", Gdx3DHud.previewFor(GrammarReference.Node.group("train")));
    }
}
