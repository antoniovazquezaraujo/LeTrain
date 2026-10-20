package letrain.mvp.impl.graphic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure file-list logic of the 3D file dialog (#484): listing, extension filtering, ordering, parent
 * resolution, name resolution and extension appending. No scene2d/GDX or IO side effects beyond
 * {@link File#listFiles()}, so it is unit-testable without a GL context.
 */
public final class FileDialogEntries {

    /** One row of the list: the file, the label shown and whether it is a folder or the parent. */
    public record Entry(File file, String label, boolean directory, boolean parent) {
    }

    /** Parent first, then folders, then files; each group alphabetical ignoring case. */
    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt((Entry entry) -> entry.parent() ? 0 : entry.directory() ? 1 : 2)
                    .thenComparing(entry -> entry.label().toLowerCase(Locale.ROOT));

    private FileDialogEntries() {}

    /**
     * Rows for {@code directory}: {@code ..} first when it has a parent, then folders (name plus
     * {@code /}) and then the files allowed by {@code extensions}, each group alphabetical. A
     * missing or unreadable directory yields just the parent row (or nothing at the root).
     */
    public static List<Entry> entries(File directory, String[] extensions) {
        List<Entry> rows = new ArrayList<>();
        if (directory == null) {
            return rows;
        }
        File canonical = directory.getAbsoluteFile();
        File parent = parentOf(canonical);
        if (parent != null) {
            rows.add(new Entry(parent, "..", true, true));
        }
        File[] files = canonical.listFiles();
        if (files == null) {
            return rows;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            if (file.isDirectory()) {
                rows.add(new Entry(file, file.getName() + "/", true, false));
            } else if (accepts(file.getName(), extensions)) {
                rows.add(new Entry(file, file.getName(), false, false));
            }
        }
        rows.sort(ORDER);
        return rows;
    }

    /**
     * Type-to-filter match: case-insensitive substring of {@code name}. An empty query matches
     * everything, so the helper can be used for both the raw list and the filtered one.
     */
    public static boolean matches(String name, String query) {
        if (name == null) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Rows that survive the type-to-filter: the parent row is always kept, the rest match their
     * file name with {@link #matches(String, String)}. An empty query returns all rows.
     */
    public static List<Entry> filter(List<Entry> entries, String query) {
        List<Entry> result = new ArrayList<>();
        if (entries == null) {
            return result;
        }
        for (Entry entry : entries) {
            if (entry.parent() || matches(entry.file().getName(), query)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Whether {@code entries} holds anything besides the parent row. */
    public static boolean hasContent(List<Entry> entries) {
        for (Entry entry : entries) {
            if (!entry.parent()) {
                return true;
            }
        }
        return false;
    }

    /** Case-insensitive extension filter; a null or empty list accepts every file. */
    public static boolean accepts(String name, String[] extensions) {
        if (name == null || extensions == null || extensions.length == 0) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            String normalized = normalizeExtension(extension);
            if (!normalized.isEmpty() && lower.endsWith("." + normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends the first allowed extension when the name does not already carry one, e.g.
     * {@code game -> game.json}; a blank name or no extensions leave it untouched.
     */
    public static String appendExtension(String name, String[] extensions) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || extensions == null || extensions.length == 0) {
            return trimmed;
        }
        if (accepts(trimmed, extensions)) {
            return trimmed;
        }
        String first = normalizeExtension(extensions[0]);
        if (first.isEmpty()) {
            return trimmed;
        }
        String base = trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return base + "." + first;
    }

    /** Normalises an extension to lower case without leading dots. */
    public static String normalizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String value = extension.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value;
    }

    /**
     * Resolves a typed name against {@code directory}: absolute names are kept, everything else is
     * joined to the directory.
     */
    public static File resolve(File directory, String name) {
        String trimmed = name == null ? "" : name.trim();
        File candidate = new File(trimmed);
        return candidate.isAbsolute() ? candidate : new File(directory, trimmed);
    }

    /** Parent of a directory, or {@code null} at a filesystem root. */
    public static File parentOf(File directory) {
        return directory == null ? null : directory.getAbsoluteFile().getParentFile();
    }
}
