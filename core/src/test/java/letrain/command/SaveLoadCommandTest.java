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
    @DisplayName("save antonio -> antonio.json (no quotes stripped wrongly)")
    void save_unquotedName_keepsWholeName() {
        assertEquals("antonio.json", savedName("save antonio;"));
    }

    @Test
    @DisplayName("save \"antonio\" -> antonio.json (quotes are removed)")
    void save_quotedName_removesQuotes() {
        assertEquals("antonio.json", savedName("save \"antonio\";"));
    }

    @Test
    @DisplayName("save \"mi partida\" -> mi partida.json (spaces allowed quoted)")
    void save_quotedNameWithSpaces_keepsWholeName() {
        assertEquals("mi partida.json", savedName("save \"mi partida\";"));
    }

    @Test
    @DisplayName("load antonio -> antonio.json")
    void load_unquotedName_keepsWholeName() {
        assertEquals("antonio.json", loadedName("load antonio;"));
    }

    @Test
    @DisplayName("save with no name defaults to quicksave.json")
    void save_withoutName_defaultsToQuicksave() {
        assertEquals("quicksave.json", savedName("save;"));
    }
}
