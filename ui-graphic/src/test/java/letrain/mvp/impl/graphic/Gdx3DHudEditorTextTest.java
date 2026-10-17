package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import letrain.mvp.EditorText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #740: the 3D editor shows the shared {@link EditorText} labels, so both clients read the
 * same words (the HUD renders them in its own uppercase button style).
 */
@DisplayName("3D editor labels")
class Gdx3DHudEditorTextTest {

    @Test
    @DisplayName("the renamed footer buttons show the shared words in the HUD's uppercase style")
    void should_FormatTheRenamedFooterButtons() {
        assertEquals(" RELOAD FROM GAME ", Gdx3DHud.editorButtonText(EditorText.RELOAD_FROM_GAME));
        assertEquals(" APPLY PROGRAM ", Gdx3DHud.editorButtonText(EditorText.APPLY_PROGRAM));
        assertEquals(" REBUILD WORLD ", Gdx3DHud.editorButtonText(EditorText.REBUILD_WORLD));
    }

    @Test
    @DisplayName("the Programming tab uses the shared name")
    void should_FormatTheProgrammingTab() {
        assertEquals(" Programming ", Gdx3DHud.editorTabText(EditorText.TAB_PROGRAMMING));
    }

    @Test
    @DisplayName("the Alt hint tints the same mnemonic letter the 2D footer highlights")
    void should_TintTheMnemonic() {
        assertEquals(" [#42A5F5]R[]ELOAD FROM GAME ",
                Gdx3DHud.editorButtonAltText(EditorText.RELOAD_FROM_GAME, 'R'));
        assertEquals(" A[#42A5F5]P[]PLY PROGRAM ",
                Gdx3DHud.editorButtonAltText(EditorText.APPLY_PROGRAM, 'P'));
        assertEquals(" RE[#42A5F5]B[]UILD WORLD ",
                Gdx3DHud.editorButtonAltText(EditorText.REBUILD_WORLD, 'B'));
        assertEquals(" Pro[#42A5F5]g[]ramming ",
                Gdx3DHud.editorTabAltText(EditorText.TAB_PROGRAMMING, 'g'));
    }
}
