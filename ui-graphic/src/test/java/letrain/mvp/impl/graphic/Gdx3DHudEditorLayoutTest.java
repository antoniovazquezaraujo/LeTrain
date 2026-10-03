package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for #652: the hidden diagnostics panel must not reserve height, otherwise the editor
 * and the quick reference stop short and leave an empty band above the footer.
 */
@DisplayName("3D editor window layout")
class Gdx3DHudEditorLayoutTest {

    private static final float FOOTER_PAD = 8f;

    @BeforeAll
    static void setUpGdx() {
        // scene2d's Cell.defaults() needs Gdx.files to finish its lazy initialization.
        Gdx.files = mock(Files.class);
    }

    @Test
    @DisplayName("a hidden collapsible panel reports no height")
    void hiddenCollapsiblePanel_hasNoHeight() {
        Gdx3DHud.CollapsiblePanel panel = new Gdx3DHud.CollapsiblePanel();
        panel.add(fixed(200, 120)).height(120);
        assertTrue(panel.getPrefHeight() > 0, "a visible panel keeps its preferred height");

        panel.setVisible(false);

        assertEquals(0f, panel.getPrefHeight(), 0.01f);
        assertEquals(0f, panel.getMinHeight(), 0.01f);
    }

    @Test
    @DisplayName("no errors: the editor fills the window down to the footer")
    void hiddenErrorPanel_leavesNoBand() {
        EditorLayout layout = buildEditorLayout(false);

        assertEquals(0f, layout.errorPanel.getHeight(), 0.01f, "the error panel row must collapse");
        assertEquals(FOOTER_PAD, gapBetweenEditorAndFooter(layout), 0.5f,
                "only the footer's own padding may remain above it");
    }

    @Test
    @DisplayName("with errors: the diagnostics panel takes its own row again")
    void visibleErrorPanel_occupiesItsRow() {
        EditorLayout layout = buildEditorLayout(true);

        assertTrue(layout.errorPanel.getHeight() >= 120f, "diagnostics keep room for the messages");
        assertEquals(FOOTER_PAD + layout.errorPanel.getHeight(), gapBetweenEditorAndFooter(layout),
                0.5f, "the panel must fill the gap instead of the editor leaving one");
    }

    /** Mirrors the row structure of the editor window in {@code Gdx3DHud.showIDE()}. */
    private static EditorLayout buildEditorLayout(boolean errors) {
        Table window = new Table();
        window.padTop(35);

        Table tabBar = new Table();
        tabBar.add(fixed(420, 34));
        Table mainContent = new Table();
        mainContent.add(fixed(600, 5000)).grow(); // long text: huge preferred height
        Gdx3DHud.CollapsiblePanel errorPanel = new Gdx3DHud.CollapsiblePanel();
        errorPanel.add(fixed(240, 22)).left().padLeft(5).row();
        errorPanel.add(fixed(600, 120)).growX().height(120).pad(5);
        errorPanel.setVisible(errors);
        Table footer = new Table();
        footer.add(fixed(500, 42));
        Table statusLabel = new Table();
        statusLabel.add(fixed(120, 20));

        window.add(tabBar).left().pad(5).row();
        window.add(mainContent).grow().row();
        window.add(errorPanel).growX().row();
        window.add(footer).growX().pad(8).row();
        window.add(statusLabel).left().padLeft(10).padBottom(5);

        window.setSize(1200, 800);
        window.layout();
        return new EditorLayout(mainContent, errorPanel, footer);
    }

    private static float gapBetweenEditorAndFooter(EditorLayout layout) {
        return layout.mainContent.getY() - (layout.footer.getY() + layout.footer.getHeight());
    }

    private static Actor fixed(float width, float height) {
        Actor actor = new Actor();
        actor.setSize(width, height);
        return actor;
    }

    private static class EditorLayout {
        final Table mainContent;
        final Gdx3DHud.CollapsiblePanel errorPanel;
        final Table footer;

        EditorLayout(Table mainContent, Gdx3DHud.CollapsiblePanel errorPanel, Table footer) {
            this.mainContent = mainContent;
            this.errorPanel = errorPanel;
            this.footer = footer;
        }
    }
}
