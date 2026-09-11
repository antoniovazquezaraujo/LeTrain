package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import letrain.mvp.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CLI save/load command file names")
class SaveLoadCommandTest {

    private final Model model = mock(Model.class);

    private String savedName(String command) {
        AtomicReference<File> ref = new AtomicReference<>();
        PlayerCommandExecutor.execute(command, model, ref::set, f -> {
        }, null);
        return ref.get().getName();
    }

    private String loadedName(String command) {
        AtomicReference<File> ref = new AtomicReference<>();
        PlayerCommandExecutor.execute(command, model, f -> {
        }, ref::set, null);
        return ref.get().getName();
    }

    private String exportedName(String command) {
        AtomicReference<File> ref = new AtomicReference<>();
        PlayerCommandExecutor.execute(command, model, null, null, null, null, null, null, null,
                ref::set, f -> {
                }, true);
        return ref.get().getName();
    }

    private String importedName(String command) {
        AtomicReference<File> ref = new AtomicReference<>();
        PlayerCommandExecutor.execute(command, model, null, null, null, null, null, null, null,
                f -> {
                }, ref::set, true);
        return ref.get().getName();
    }

    @Test
    @DisplayName("save partida -> partida.json (no quotes stripped wrongly)")
    void save_unquotedName_keepsWholeName() {
        assertEquals("partida.json", savedName("save partida;"));
    }

    @Test
    @DisplayName("save \"partida\" -> partida.json (quotes are removed)")
    void save_quotedName_removesQuotes() {
        assertEquals("partida.json", savedName("save \"partida\";"));
    }

    @Test
    @DisplayName("save \"mi partida\" -> mi partida.json (spaces allowed quoted)")
    void save_quotedNameWithSpaces_keepsWholeName() {
        assertEquals("mi partida.json", savedName("save \"mi partida\";"));
    }

    @Test
    @DisplayName("load partida -> partida.json")
    void load_unquotedName_keepsWholeName() {
        assertEquals("partida.json", loadedName("load partida;"));
    }

    @Test
    @DisplayName("save with no name defaults to quicksave.json")
    void save_withoutName_defaultsToQuicksave() {
        assertEquals("quicksave.json", savedName("save;"));
    }

    @Test
    @DisplayName("save keeps an explicit extension or defaults to .json")
    void save_explicitExtension_isRespected() {
        assertEquals("mi-cosa.json", savedName("save \"mi-cosa.json\";"));
        assertEquals("savegame.json", savedName("save \"savegame.json\";"));
    }

    @Test
    @DisplayName("export uses the scenario extension (.ltr) by default")
    void export_scenarioExtension() {
        assertEquals("mi-red.ltr", exportedName("export \"mi-red.ltr\";"));
        assertEquals("mi-red.ltr", exportedName("export \"mi-red\";"));
        assertEquals("scenario.ltr", exportedName("export;"));
    }

    @Test
    @DisplayName("import uses the scenario extension (.ltr) by default")
    void import_scenarioExtension() {
        assertEquals("mi-red.ltr", importedName("import \"mi-red.ltr\";"));
        assertEquals("mi-red.ltr", importedName("import \"mi-red\";"));
        assertEquals("scenario.ltr", importedName("import;"));
    }

    @Test
    @DisplayName("export with an empty command journal returns an error and does not call the handler")
    void export_emptyJournal_errors() {
        letrain.mvp.Model realModel = new letrain.mvp.impl.Model();
        AtomicReference<File> ref = new AtomicReference<>();
        String error = PlayerCommandExecutor.execute("export \"x.ltr\";", realModel, null, null, null,
                null, null, null, null, ref::set, f -> {
                }, true);
        assertNotNull(error, "export must fail with an empty journal");
        assertNull(ref.get(), "the export handler must not be called");
    }
}
