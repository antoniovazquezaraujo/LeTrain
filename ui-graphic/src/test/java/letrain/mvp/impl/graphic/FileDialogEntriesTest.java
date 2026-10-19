package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import letrain.mvp.impl.graphic.FileDialogEntries.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #484: the pure file-list logic behind the keyboard-first 3D file dialog. It owns listing,
 * extension filtering, ordering, parent/name resolution and extension appending, so it is pinned
 * here with a real temporary directory and no GL context.
 */
@DisplayName("3D file dialog entries (#484)")
class FileDialogEntriesTest {

    private static List<String> labels(List<Entry> entries) {
        return entries.stream().map(Entry::label).toList();
    }

    private static File create(Path directory, String name) throws IOException {
        return Files.createFile(directory.resolve(name)).toFile();
    }

    private static File mkdir(Path directory, String name) throws IOException {
        return Files.createDirectory(directory.resolve(name)).toFile();
    }

    @Test
    @DisplayName("lists folders and only the files whose extension was requested")
    void should_ListFoldersAndFilteredFiles(@TempDir Path tempDir) throws IOException {
        mkdir(tempDir, "sub");
        create(tempDir, "b.json");
        create(tempDir, "A.JSON");
        create(tempDir, "notes.txt");
        create(tempDir, "noext");
        create(tempDir, "weird.json.bak");

        List<Entry> entries = FileDialogEntries.entries(tempDir.toFile(), new String[] {"json"});

        assertEquals(List.of("..", "sub/", "A.JSON", "b.json"), labels(entries),
                "folders always show; files are filtered case-insensitively by extension");
        assertEquals(tempDir.toFile().getAbsoluteFile().getParentFile(), entries.get(0).file(),
                "the parent row points at the real parent");
        assertTrue(entries.get(0).parent());
        assertTrue(entries.get(1).directory(), "a folder row stays even with a file filter");
        assertFalse(entries.get(1).parent());
    }

    @Test
    @DisplayName("a dotted or upper-case extension filter behaves like the bare one")
    void should_NormalizeTheRequestedExtensions(@TempDir Path tempDir) throws IOException {
        create(tempDir, "game.json");
        create(tempDir, "other.txt");

        assertEquals(List.of("..", "game.json"),
                labels(FileDialogEntries.entries(tempDir.toFile(), new String[] {".JSON"})));
    }

    @Test
    @DisplayName("with no extensions every file is listed")
    void should_AcceptEveryFileWithoutExtensions(@TempDir Path tempDir) throws IOException {
        create(tempDir, "b.json");
        create(tempDir, "notes.txt");
        create(tempDir, "noext");

        assertEquals(List.of("..", "b.json", "noext", "notes.txt"),
                labels(FileDialogEntries.entries(tempDir.toFile(), null)));
        assertEquals(List.of("..", "b.json", "noext", "notes.txt"),
                labels(FileDialogEntries.entries(tempDir.toFile(), new String[0])));
    }

    @Test
    @DisplayName("orders parent, then folders, then files, each group alphabetical ignoring case")
    void should_OrderParentFoldersThenFiles(@TempDir Path tempDir) throws IOException {
        mkdir(tempDir, "zebra");
        mkdir(tempDir, "Alpha");
        create(tempDir, "charlie.json");
        create(tempDir, "Bravo.json");
        create(tempDir, "alpha.json");

        List<Entry> entries = FileDialogEntries.entries(tempDir.toFile(), new String[] {"json"});

        assertEquals(List.of("..", "Alpha/", "zebra/", "alpha.json", "Bravo.json", "charlie.json"),
                labels(entries));
    }

    @Test
    @DisplayName("a missing directory yields only its parent row")
    void should_ReturnOnlyTheParentForAMissingDirectory(@TempDir Path tempDir) {
        File missing = tempDir.resolve("does-not-exist").toFile();

        List<Entry> entries = FileDialogEntries.entries(missing, new String[] {"json"});

        assertEquals(List.of(".."), labels(entries));
        assertFalse(FileDialogEntries.hasContent(entries));
    }

    @Test
    @DisplayName("an unlistable path (a regular file) yields only the parent row")
    void should_ReturnOnlyTheParentForAnUnlistablePath(@TempDir Path tempDir) throws IOException {
        File plainFile = create(tempDir, "plain.txt");

        List<Entry> entries = FileDialogEntries.entries(plainFile, null);

        assertEquals(List.of(".."), labels(entries), "listFiles() == null is treated as empty");
        assertFalse(FileDialogEntries.hasContent(entries));
    }

    @Test
    @DisplayName("a null directory yields no rows")
    void should_ReturnNothingForANullDirectory() {
        assertTrue(FileDialogEntries.entries(null, new String[] {"json"}).isEmpty());
    }

    @Test
    @DisplayName("hasContent ignores the parent row")
    void should_DetectContentBeyondTheParent(@TempDir Path tempDir) throws IOException {
        assertFalse(FileDialogEntries.hasContent(List.of()), "empty list");
        assertFalse(FileDialogEntries
                .hasContent(FileDialogEntries.entries(tempDir.resolve("missing").toFile(), null)));
        create(tempDir, "game.json");
        assertTrue(FileDialogEntries
                .hasContent(FileDialogEntries.entries(tempDir.toFile(), new String[] {"json"})));
    }

    @Test
    @DisplayName("resolve joins a relative/typed name to the directory and keeps absolute paths")
    void should_ResolveNamesAgainstTheDirectory(@TempDir Path tempDir) {
        File directory = tempDir.toFile();
        File absolute = tempDir.resolve("elsewhere").resolve("x.json").toAbsolutePath().toFile();

        assertEquals(new File(directory, "game.json"),
                FileDialogEntries.resolve(directory, "game.json"));
        assertEquals(new File(directory, "game.json"),
                FileDialogEntries.resolve(directory, "  game.json  "), "the name is trimmed");
        assertEquals(absolute, FileDialogEntries.resolve(directory, absolute.getAbsolutePath()),
                "a typed absolute path is kept verbatim");
    }

    @Test
    @DisplayName("parentOf returns the parent and stops at the filesystem root")
    void should_FindTheParentAndStopAtTheRoot(@TempDir Path tempDir) {
        assertEquals(tempDir.toFile().getAbsoluteFile().getParentFile(),
                FileDialogEntries.parentOf(tempDir.toFile()));
        assertNull(FileDialogEntries.parentOf(new File("/")), "the root has no parent");
        assertNull(FileDialogEntries.parentOf(null));
    }

    @Test
    @DisplayName("appendExtension adds the first allowed extension when it is missing")
    void should_AppendTheFirstAllowedExtension() {
        assertEquals("game.json", FileDialogEntries.appendExtension("game", new String[] {"json"}));
        assertEquals("game.json",
                FileDialogEntries.appendExtension("game", new String[] {".json"}));
        assertEquals("game.json", FileDialogEntries.appendExtension("game", new String[] {"JSON"}));
        assertEquals("game.json",
                FileDialogEntries.appendExtension("  game  ", new String[] {"json"}));
        assertEquals("game.yaml",
                FileDialogEntries.appendExtension("game", new String[] {"yaml", "json"}),
                "the first extension wins");
        assertEquals("game.json", FileDialogEntries.appendExtension("game.", new String[] {"json"}),
                "a trailing dot does not double the separator");
    }

    @Test
    @DisplayName("appendExtension respects a name that already ends in an allowed extension")
    void should_KeepAnExistingAllowedExtension() {
        assertEquals("game.json",
                FileDialogEntries.appendExtension("game.json", new String[] {"json"}));
        assertEquals("game.json",
                FileDialogEntries.appendExtension("game.json", new String[] {".json"}));
        assertEquals("GAME.JSON",
                FileDialogEntries.appendExtension("GAME.JSON", new String[] {"json"}),
                "the existing name is kept exactly as typed");
    }

    @Test
    @DisplayName("appendExtension does not strip an unlisted extension (foo.txt -> foo.txt.json)")
    void should_AppendAfterAnUnlistedExtension() {
        // Current contract, pinned as-is: the dialog never silently replaces a user's extension.
        // It is arguably surprising (typing foo.txt in a JSON dialog yields foo.txt.json), but the
        // task is to record it, not to change the behaviour.
        assertEquals("foo.txt.json",
                FileDialogEntries.appendExtension("foo.txt", new String[] {"json"}));
    }

    @Test
    @DisplayName("appendExtension leaves blank names and empty extension lists untouched")
    void should_LeaveBlankNamesAndEmptyExtensionListsUntouched() {
        assertEquals("", FileDialogEntries.appendExtension(null, new String[] {"json"}));
        assertEquals("", FileDialogEntries.appendExtension("   ", new String[] {"json"}));
        assertEquals("game", FileDialogEntries.appendExtension("game", null));
        assertEquals("game", FileDialogEntries.appendExtension("game", new String[0]));
        assertEquals("game", FileDialogEntries.appendExtension("game", new String[] {"", "json"}),
                "an empty first extension leaves the name alone");
    }

    @Test
    @DisplayName("normalizeExtension trims dots/case/spaces and tolerates null")
    void should_NormalizeExtensions() {
        assertEquals("json", FileDialogEntries.normalizeExtension("json"));
        assertEquals("json", FileDialogEntries.normalizeExtension(".json"));
        assertEquals("json", FileDialogEntries.normalizeExtension("..JSON"));
        assertEquals("json", FileDialogEntries.normalizeExtension("  .Json  "));
        assertEquals("", FileDialogEntries.normalizeExtension(null));
        assertEquals("", FileDialogEntries.normalizeExtension(""));
        assertEquals("", FileDialogEntries.normalizeExtension("."));
    }

    @Test
    @DisplayName("accepts is case-insensitive and only matches the final extension")
    void should_FilterByTheFinalExtension() {
        assertTrue(FileDialogEntries.accepts("A.JSON", new String[] {"json"}));
        assertTrue(FileDialogEntries.accepts("A.JSON", new String[] {".json"}));
        assertTrue(FileDialogEntries.accepts("archive.tar.gz", new String[] {"gz"}));
        assertFalse(FileDialogEntries.accepts("A.JSON.bak", new String[] {"json"}));
        assertFalse(FileDialogEntries.accepts("noext", new String[] {"json"}));
        assertTrue(FileDialogEntries.accepts("anything", null), "no filter accepts everything");
        assertTrue(FileDialogEntries.accepts("anything", new String[0]));
        assertTrue(FileDialogEntries.accepts(null, new String[] {"json"}),
                "a null name is tolerated by the pure filter");
    }
}
