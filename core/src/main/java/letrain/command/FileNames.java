package letrain.command;

import java.io.File;

/**
 * Filename helpers that make sure saved games and exported scenarios always carry their extension
 * ({@code .json} and {@code .ltr} respectively).
 *
 * <p>
 * A name that already has an extension is left untouched, so {@code mi-red.ltr} is respected
 * instead of being turned into {@code mi-red.ltr.json}.
 */
public final class FileNames {

    /** Savegame extension. */
    public static final String SAVEGAME_EXTENSION = ".json";

    /** Default savegame base name when none is given. */
    public static final String DEFAULT_SAVEGAME = "quicksave";

    /** Default scenario base name when none is given. */
    public static final String DEFAULT_SCENARIO = "scenario";

    private FileNames() {}

    /** Appends the savegame extension ({@code .json}) when the name has no extension. */
    public static String withSavegameExtension(String filename) {
        return ensureExtension(filename, SAVEGAME_EXTENSION, DEFAULT_SAVEGAME);
    }

    /** Appends the scenario extension ({@code .ltr}) when the name has no extension. */
    public static String withScenarioExtension(String filename) {
        return ensureExtension(filename, ScenarioFile.EXTENSION, DEFAULT_SCENARIO);
    }

    /** Appends the savegame extension to a file when needed; {@code null} is returned as-is. */
    public static File withSavegameExtension(File file) {
        return file == null ? null : new File(withSavegameExtension(file.getPath()));
    }

    /** Appends the scenario extension to a file when needed; {@code null} is returned as-is. */
    public static File withScenarioExtension(File file) {
        return file == null ? null : new File(withScenarioExtension(file.getPath()));
    }

    private static String ensureExtension(String filename, String extension, String defaultBase) {
        if (filename == null || filename.isEmpty()) {
            return defaultBase + extension;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        return base.contains(".") ? filename : filename + extension;
    }
}
