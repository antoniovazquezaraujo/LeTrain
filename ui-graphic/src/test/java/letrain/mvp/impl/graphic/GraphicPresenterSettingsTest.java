package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import letrain.economy.EconomyManager;
import letrain.mvp.impl.Model;
import letrain.settings.NewGameForm;
import letrain.settings.SettingsApplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * S4b (#778): File → Settings in the 3D client. The presenter cannot be built without the GL stack,
 * so the real methods run on a mock whose {@code model}/{@code hud} fields are injected: the dialog
 * receives the running game's effective values (seed included) and its scenario keys, the port
 * applies to the model (S3a), New game keeps the S4a discard confirmation and Save as defaults
 * writes {@code letrain.cfg} through the shared core helper.
 */
@DisplayName("3D in-game Settings flow (#778)")
class GraphicPresenterSettingsTest {

    private GraphicPresenter presenter;
    private Model model;
    private Gdx3DHud hud;

    @BeforeEach
    void setUp() throws Exception {
        model = new Model(1);
        model.setMode(Model.GameMode.RAILS);
        hud = mock(Gdx3DHud.class);
        presenter = mock(GraphicPresenter.class);
        setField("model", model);
        setField("hud", hud);
        setField("gameStarted", true);
        doCallRealMethod().when(presenter).onSettings();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GraphicPresenter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(presenter, value);
    }

    private DialogArgs captureDialog() {
        ArgumentCaptor<Map<String, String>> current = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Set<String>> scenario = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Gdx3DHud.SettingsPort> port =
                ArgumentCaptor.forClass(Gdx3DHud.SettingsPort.class);
        ArgumentCaptor<Consumer<Map<String, String>>> onNewGame =
                ArgumentCaptor.forClass(Consumer.class);
        verify(hud).showSettingsDialog(current.capture(), scenario.capture(), port.capture(),
                onNewGame.capture());
        return new DialogArgs(current.getValue(), scenario.getValue(), port.getValue(),
                onNewGame.getValue());
    }

    private record DialogArgs(Map<String, String> current, Set<String> scenario,
            Gdx3DHud.SettingsPort port, Consumer<Map<String, String>> onNewGame) {
    }

    @Test
    @DisplayName("without a running game (no economy manager) the dialog never opens")
    void onSettings_withoutEconomyManager_doesNothing() throws Exception {
        Model headless = mock(Model.class);
        when(headless.getEconomyManager()).thenReturn(null);
        setField("model", headless);

        presenter.onSettings();

        verify(hud, never()).showSettingsDialog(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the dialog gets the effective values (seed included) and the port applies to the model")
    void settingsDialog_usesTheEffectiveValues() {
        model.setSeed(5);

        presenter.onSettings();
        DialogArgs args = captureDialog();

        assertEquals("5", args.current().get(NewGameForm.SEED_KEY), "the running world seed");
        assertTrue(args.scenario().isEmpty());

        SettingsApplyResult outcome = args.port().apply(Map.of("startingBalance", "250.0"));
        assertFalse(outcome.hasRejections(), String.valueOf(outcome.rejections()));
        assertTrue(outcome.applied().contains("startingBalance"));
    }

    @Test
    @DisplayName("the seed is overlaid on top of the running game's effective configuration")
    void settingsDialog_overlaysSeedOnTheEffectiveConfiguration() {
        Map<String, String> effective = model.getEconomyManager().effectiveConfig();

        presenter.onSettings();
        DialogArgs args = captureDialog();

        for (Map.Entry<String, String> entry : effective.entrySet()) {
            assertEquals(entry.getValue(), args.current().get(entry.getKey()),
                    "effective value of " + entry.getKey() + " must survive the overlay");
        }
        assertEquals(String.valueOf(model.getSeed()), args.current().get(NewGameForm.SEED_KEY),
                "the running world seed is added on top of the effective configuration");
    }

    @Test
    @DisplayName("the port mirrors S3a: scenario keys come back with their reason")
    void settingsPort_mirrorsApplyStates() {
        model.applyScenarioConfiguration(Map.of("startingBalance", "55"));

        presenter.onSettings();
        DialogArgs args = captureDialog();

        assertTrue(args.scenario().contains("startingBalance"),
                "the scenario keys reach the dialog");
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO, args.port()
                .apply(Map.of("startingBalance", "60")).rejections().get("startingBalance"));
    }

    @Test
    @DisplayName("Save as defaults delegates through the port to the shared writer")
    void saveDefaults_delegatesThroughThePort() {
        when(presenter.saveSettingsAsDefaults(anyMap())).thenReturn("disk full");

        presenter.onSettings();
        DialogArgs args = captureDialog();

        assertEquals("disk full", args.port().saveDefaults(Map.of("seed", "1")));
    }

    @Test
    @DisplayName("saveSettingsAsDefaults writes the form into the running game's config file")
    void saveSettingsAsDefaults_writesToTheConfigFile() throws Exception {
        Path cfg = Files.createTempFile("s4b-settings", ".cfg");
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getConfigFile()).thenReturn(cfg.toFile());
        Model configModel = mock(Model.class);
        when(configModel.getEconomyManager()).thenReturn(economy);
        setField("model", configModel);
        doCallRealMethod().when(presenter).saveSettingsAsDefaults(anyMap());

        assertNull(presenter.saveSettingsAsDefaults(Map.of("startingBalance", "12.5")),
                "a writable config file reports success");

        assertTrue(Files.readString(cfg).contains("startingBalance=12.5"),
                "the form values land in the running game's letrain.cfg");
        Files.deleteIfExists(cfg);
    }

    @Test
    @DisplayName("writeDefaults reports null on success and the failure message otherwise")
    void writeDefaults_reportsSuccessOrFailure() throws Exception {
        Path dir = Files.createTempDirectory("s4b-defaults");
        File cfg = dir.resolve("letrain.cfg").toFile();

        assertNull(GraphicPresenter.writeDefaults(cfg, Map.of("startingBalance", "12.5")),
                "a writable file reports success");
        assertTrue(Files.readString(cfg.toPath()).contains("startingBalance=12.5"));

        assertNotNull(GraphicPresenter.writeDefaults(dir.toFile(), Map.of("startingBalance", "1")),
                "an unwritable target comes back as a message for the dialog");
        Files.deleteIfExists(cfg.toPath());
        Files.deleteIfExists(dir);
    }

    @Test
    @DisplayName("getConfigurationText renders the settings catalogue (edited in the Config tab)")
    void getConfigurationText_comesFromTheCatalogue() {
        doCallRealMethod().when(presenter).getConfigurationText();

        String text = presenter.getConfigurationText();

        assertTrue(text.startsWith("configuration {"), text);
        assertTrue(text.contains("terrain.waterPercent="), text);
        assertFalse(text.contains("threshold."),
                "the Config tab must come from the catalogue: " + text);
    }

    @Test
    @DisplayName("New game keeps the S4a discard confirmation with a game running")
    void newGameShortcut_withGame_confirmsFirst() {
        doCallRealMethod().when(presenter).onNewGame(anyMap());

        presenter.onSettings();
        DialogArgs args = captureDialog();

        args.onNewGame().accept(Map.of(NewGameForm.SEED_KEY, "77"));

        verify(hud).showConfirmDialog(any(), any(), any(), any());
        verify(hud, never()).showNewGameDialog(any(), any(), any());
    }

    @Test
    @DisplayName("New game at boot opens the New Game dialog prefilled, without confirming")
    void newGameShortcut_atBoot_opensTheDialog() throws Exception {
        setField("gameStarted", false);
        doCallRealMethod().when(presenter).onNewGame(anyMap());

        presenter.onSettings();
        DialogArgs args = captureDialog();

        args.onNewGame().accept(Map.of(NewGameForm.SEED_KEY, "77"));

        verify(hud).showNewGameDialog(any(NewGameForm.class), any(), any());
        verify(hud, never()).showConfirmDialog(any(), any(), any(), any());
    }
}
