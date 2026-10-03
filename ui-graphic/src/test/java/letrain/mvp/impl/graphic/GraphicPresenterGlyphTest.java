package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("3D glyph decals use the glyph's atlas page")
class GraphicPresenterGlyphTest {

    @Test
    @DisplayName("a glyph on the second atlas page takes its texture from that page (bug #603)")
    void should_UseGlyphPageTexture() {
        // Arrange: a font whose atlas has two pages.
        BitmapFont font = mock(BitmapFont.class);
        Texture page0 = mock(Texture.class);
        Texture page1 = mock(Texture.class);
        Array<TextureRegion> regions = new Array<>();
        regions.add(new TextureRegion(page0));
        regions.add(new TextureRegion(page1));
        when(font.getRegions()).thenReturn(regions);

        BitmapFont.Glyph glyph = new BitmapFont.Glyph();
        glyph.page = 1;
        glyph.u = 0.10f;
        glyph.v = 0.20f;
        glyph.u2 = 0.30f;
        glyph.v2 = 0.40f;

        // Act
        TextureRegion region = GraphicPresenter.glyphRegion(font, glyph);

        // Assert: the texture is the glyph's own page, not page 0.
        assertSame(page1, region.getTexture());
        assertEquals(0.10f, region.getU(), 1e-6);
        assertEquals(0.20f, region.getV(), 1e-6);
        assertEquals(0.30f, region.getU2(), 1e-6);
        assertEquals(0.40f, region.getV2(), 1e-6);
    }
}
