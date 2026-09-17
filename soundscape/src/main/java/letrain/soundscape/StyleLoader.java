package letrain.soundscape;

import java.io.IOException;
import java.nio.file.Path;

/** Loads a soundscape style from its text definition. */
public interface StyleLoader {

    SoundscapeStyle load(Path path) throws IOException;

    /** Loads a style bundled in the classpath, e.g. {@code /styles/valle-norte.sound}. */
    SoundscapeStyle loadResource(String resource) throws IOException;
}
