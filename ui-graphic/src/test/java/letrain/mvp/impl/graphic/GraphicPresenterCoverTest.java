package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the 3D title cover state. The presenter cannot be built without the GL stack, so the
 * real methods are driven on a mock with its {@code hud} field still null (the null guards are part
 * of what this test pins).
 */
@DisplayName("3D title cover: presenter state")
class GraphicPresenterCoverTest {

    @Test
    @DisplayName("showCover marks the cover active; dismissCover hands the screen back")
    void should_ToggleTheCoverState() {
        GraphicPresenter presenter = mock(GraphicPresenter.class);
        doCallRealMethod().when(presenter).showCover();
        doCallRealMethod().when(presenter).dismissCover();
        doCallRealMethod().when(presenter).isCoverActive();

        assertFalse(presenter.isCoverActive(), "no cover until it is shown");

        presenter.showCover();
        assertTrue(presenter.isCoverActive());

        presenter.dismissCover();
        assertFalse(presenter.isCoverActive());
    }
}
