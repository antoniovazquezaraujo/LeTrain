package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The 3D presenter cannot be built without the GL stack (its constructor creates renderers, the
 * camera and the audio mixer), so its program-load wrapper is driven on a mock: the real
 * {@code onLoadCommands} only touches the {@code model} field, which is injected here.
 */
@DisplayName("3D presenter: program load wrapper")
class GraphicPresenterProgramLoadTest {

    @Test
    @DisplayName("onLoadCommands installs a valid program file (strict syntax)")
    void onLoadCommands_installsValidProgram() throws Exception {
        Model model = new Model(1);
        GraphicPresenter presenter = mock(GraphicPresenter.class);
        Field modelField = GraphicPresenter.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(presenter, model);
        doCallRealMethod().when(presenter).onLoadCommands(any(File.class));

        Path file = Files.createTempFile("program", ".txt");
        String program = "create itinerary \"r\" {\n"
                + "  add station 1 arrival 9:20, load, departure 9:30\n}\n";
        Files.writeString(file, program);
        try {
            presenter.onLoadCommands(file.toFile());

            assertEquals(program, model.getProgram(),
                    "the program file must be installed verbatim");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
