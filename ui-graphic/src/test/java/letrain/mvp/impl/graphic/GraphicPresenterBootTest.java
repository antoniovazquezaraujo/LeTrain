package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Map;
import letrain.mvp.impl.Model;
import letrain.settings.NewGameForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Issue #777: the 3D interactive boot (cover → File menu → New) mirrors the 2D presenter. The
 * presenter cannot be built without the GL stack, so the real methods run on a mock whose
 * {@code model}/{@code hud} fields are injected and whose state flags are set through reflection.
 */
@DisplayName("3D interactive boot: presenter state")
class GraphicPresenterBootTest {

    private GraphicPresenter presenter;
    private Model model;
    private Gdx3DHud hud;

    @BeforeEach
    void setUp() throws Exception {
        model = new Model(1);
        hud = mock(Gdx3DHud.class);
        presenter = mock(GraphicPresenter.class);
        setField("model", model);
        setField("hud", hud);
        doCallRealMethod().when(presenter).isCoverActive();
        doCallRealMethod().when(presenter).hasActiveGame();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GraphicPresenter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(presenter, value);
    }

    @Test
    @DisplayName("dismissing the cover without a game opens the File menu (boot menu)")
    void dismissCover_withoutGame_opensTheFileMenu() throws Exception {
        setField("gameStarted", false);
        doCallRealMethod().when(presenter).dismissCover();

        presenter.dismissCover();

        assertFalse(presenter.isCoverActive());
        verify(presenter).showFileMenu();
    }

    @Test
    @DisplayName("dismissing the cover with a game running never opens the menu")
    void dismissCover_withGame_doesNotOpenTheMenu() throws Exception {
        setField("gameStarted", true);
        doCallRealMethod().when(presenter).dismissCover();

        presenter.dismissCover();

        assertFalse(presenter.isCoverActive());
        verify(presenter, never()).showFileMenu();
    }

    @Test
    @DisplayName("Esc on the boot File menu brings the cover back")
    void fileMenuDismissed_withoutGame_showsTheCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).onFileMenuDismissed();
        doCallRealMethod().when(presenter).showCover();

        presenter.onFileMenuDismissed();

        verify(presenter).showCover();
        assertTrue(presenter.isCoverActive());
    }

    @Test
    @DisplayName("Esc on the in-game File menu leaves the world alone")
    void fileMenuDismissed_withGame_doesNothing() throws Exception {
        setField("gameStarted", true);
        doCallRealMethod().when(presenter).onFileMenuDismissed();

        presenter.onFileMenuDismissed();

        verify(presenter, never()).showCover();
    }

    @Test
    @DisplayName("a headless run (cover disabled) has an active game and no boot menu")
    void coverDisabled_hasActiveGame_andNoBootMenu() throws Exception {
        setField("gameStarted", true);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).setCoverEnabled(anyBoolean());
        doCallRealMethod().when(presenter).dismissCover();

        presenter.setCoverEnabled(false);

        assertTrue(presenter.hasActiveGame(),
                "setCoverEnabled must not touch gameStarted: the world starts without a boot");
        assertFalse(presenter.isCoverActive(), "no cover is shown for a headless run");
        presenter.dismissCover();
        verify(presenter, never()).showFileMenu();
    }

    @Test
    @DisplayName("boot cycle: cover → menu → Esc → cover → menu, without doubled calls")
    void bootCycle_coverToMenuToCoverToMenu() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", true);
        doCallRealMethod().when(presenter).dismissCover();
        doCallRealMethod().when(presenter).onFileMenuDismissed();
        doCallRealMethod().when(presenter).showCover();

        presenter.dismissCover(); // cover -> boot menu
        presenter.onFileMenuDismissed(); // boot menu Esc -> cover
        presenter.dismissCover(); // cover -> boot menu again

        assertFalse(presenter.isCoverActive(), "the cycle ends back on the boot menu");
        verify(presenter, times(2)).showFileMenu();
        verify(presenter, times(1)).showCover();
    }

    @Test
    @DisplayName("a boot-flow cancellation with a game running never shows the cover")
    void bootCancel_withGame_doesNothing() throws Exception {
        setField("gameStarted", true);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).onBootFlowCancelled();

        presenter.onBootFlowCancelled();

        verify(presenter, never()).showCover();
        assertFalse(presenter.isCoverActive());
    }

    @Test
    @DisplayName("New at boot opens the dialog without returning to the cover")
    void newAction_atBoot_doesNotShowCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());

        presenter.onNewGame();

        verify(hud).showNewGameDialog(any(NewGameForm.class), any(), any());
        verify(presenter, never()).showCover();
        assertFalse(presenter.isCoverActive(), "an action close must not go back to the cover");
    }

    @Test
    @DisplayName("cancelling the New dialog at boot returns to the cover")
    void newDialogCancel_atBoot_showsTheCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());
        doCallRealMethod().when(presenter).onBootFlowCancelled();
        doCallRealMethod().when(presenter).showCover();
        ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);

        presenter.onNewGame();
        verify(hud).showNewGameDialog(any(NewGameForm.class), any(), onCancel.capture());
        assertFalse(presenter.isCoverActive(), "opening the dialog must not show the cover");

        onCancel.getValue().run();

        verify(presenter).showCover();
        assertTrue(presenter.isCoverActive());
    }

    @Test
    @DisplayName("Load at boot opens the chooser without returning to the cover")
    void loadAction_atBoot_doesNotShowCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).showLoadDialog();

        presenter.showLoadDialog();

        verify(hud).showFileDialog(any(), any(), any(), any(), any(), any());
        verify(presenter, never()).showCover();
        assertFalse(presenter.isCoverActive(), "an action close must not go back to the cover");
    }

    @Test
    @DisplayName("cancelling the Load chooser at boot returns to the cover")
    void loadDialogCancel_atBoot_showsTheCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).showLoadDialog();
        doCallRealMethod().when(presenter).onBootFlowCancelled();
        doCallRealMethod().when(presenter).showCover();
        ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);

        presenter.showLoadDialog();
        verify(hud).showFileDialog(any(), any(), any(), any(), any(), onCancel.capture());

        onCancel.getValue().run();

        verify(presenter).showCover();
        assertTrue(presenter.isCoverActive());
    }

    @Test
    @DisplayName("onNewGame at boot opens the dialog directly, without a discard confirmation")
    void onNewGame_withoutGame_opensTheDialog() throws Exception {
        setField("gameStarted", false);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());

        presenter.onNewGame();

        verify(hud).showNewGameDialog(any(NewGameForm.class), any(), any());
        verify(hud, never()).showConfirmDialog(any(), any(), any(), any());
    }

    @Test
    @DisplayName("onNewGame with a game running confirms the discard before opening the dialog")
    void onNewGame_withGame_confirmsFirst() throws Exception {
        setField("gameStarted", true);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());

        presenter.onNewGame();

        verify(hud).showConfirmDialog(any(), any(), any(), any());
        verify(hud, never()).showNewGameDialog(any(), any(), any());
    }

    @Test
    @DisplayName("refusing the discard confirmation opens no dialog and keeps the game")
    void onNewGame_withGame_cancelKeepsState() throws Exception {
        setField("gameStarted", true);
        setField("coverActive", false);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());
        ArgumentCaptor<Runnable> onConfirm = ArgumentCaptor.forClass(Runnable.class);

        presenter.onNewGame();
        verify(hud).showConfirmDialog(any(), any(), any(), onConfirm.capture());
        // Cancel: the confirm callback is captured but never run.

        verify(hud, never()).showNewGameDialog(any(), any(), any());
        assertTrue(presenter.hasActiveGame(), "the running game is untouched");
        assertFalse(presenter.isCoverActive(), "no return to the cover while in game");
    }

    @Test
    @DisplayName("accepting the discard confirmation opens the New Game dialog")
    void onNewGame_withGame_confirmOpensTheDialog() throws Exception {
        setField("gameStarted", true);
        doCallRealMethod().when(presenter).onNewGame();
        doCallRealMethod().when(presenter).onNewGame(anyMap());
        ArgumentCaptor<Runnable> onConfirm = ArgumentCaptor.forClass(Runnable.class);

        presenter.onNewGame();
        verify(hud).showConfirmDialog(any(), any(), any(), onConfirm.capture());

        onConfirm.getValue().run();

        verify(hud).showNewGameDialog(any(NewGameForm.class), any(), any());
    }

    @Test
    @DisplayName("startNewGame marks the game active and ends the cover")
    void startNewGame_startsTheGame_andEndsTheCover() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", true);
        doCallRealMethod().when(presenter).startNewGame(anyMap());
        doCallRealMethod().when(presenter).hasActiveGame();
        doCallRealMethod().when(presenter).isCoverActive();
        // The heavy model swap needs the GL stack; only the boot state transition is under test.
        doNothing().when(presenter).applyLoadedModel(any(), any());

        presenter.startNewGame(Map.of(NewGameForm.SEED_KEY, "123"));

        assertTrue(presenter.hasActiveGame(), "a started game counts as active");
        assertFalse(presenter.isCoverActive(), "the cover must be gone");
        verify(hud).hideCover();
    }

    @Test
    @DisplayName("startNewGame resolves the seed through the shared core before swapping the model")
    void startNewGame_resolvesSeedThroughSharedCore() throws Exception {
        setField("gameStarted", false);
        setField("coverActive", true);
        doCallRealMethod().when(presenter).startNewGame(anyMap());
        doCallRealMethod().when(presenter).hasActiveGame();
        doCallRealMethod().when(presenter).isCoverActive();
        doNothing().when(presenter).applyLoadedModel(any(), any());
        ArgumentCaptor<Model> fresh = ArgumentCaptor.forClass(Model.class);

        presenter.startNewGame(Map.of(NewGameForm.SEED_KEY, "321"));

        verify(presenter).applyLoadedModel(fresh.capture(), any());
        assertEquals(NewGameForm.resolveSeed("321"), fresh.getValue().getSeed(),
                "the 3D client must start the same world as the 2D client for the same seed text");
        assertTrue(presenter.hasActiveGame());
        assertFalse(presenter.isCoverActive());
    }
}
