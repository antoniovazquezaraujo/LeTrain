package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@DisplayName("3D presenter: program load wrapper (ADR-022 compatibility)")
class GraphicPresenterProgramLoadTest {

    @Test
    @DisplayName("onLoadCommands normalizes a legacy program file with CRLF line endings")
    void onLoadCommands_normalizesLegacyCrlfProgram() throws Exception {
        Model model = new Model(1);
        GraphicPresenter presenter = mock(GraphicPresenter.class);
        Field modelField = GraphicPresenter.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(presenter, model);
        doCallRealMethod().when(presenter).onLoadCommands(any(File.class));

        Path file = Files.createTempFile("legacy-program", ".txt");
        Files.writeString(file,
                "create itinerary \"x\" {\r\n  add station 2 reverse unload\r\n}\r\n");
        try {
            presenter.onLoadCommands(file.toFile());

            String program = model.getProgram();
            assertTrue(program.contains("add station 2 reverse, unload"),
                    "the legacy program file must load normalized: " + program);
            assertFalse(program.contains("\r"), "CRLF must not survive normalization: " + program);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
