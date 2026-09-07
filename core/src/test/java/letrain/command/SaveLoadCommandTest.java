package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
