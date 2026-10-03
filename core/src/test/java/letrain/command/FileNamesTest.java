package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FileNames: savegames and scenarios always get an extension")
class FileNamesTest {

    @Test
    @DisplayName("savegames get .json when the name has none")
    void savegame_appendsJsonWhenMissing() {
        assertEquals("partida.json", FileNames.withSavegameExtension("partida"));
        assertEquals("quicksave.json", FileNames.withSavegameExtension(""));
        assertEquals("quicksave.json", FileNames.withSavegameExtension((String) null));
    }

    @Test
    @DisplayName("savegames keep an explicit extension")
    void savegame_respectsExistingExtension() {
        assertEquals("partida.json", FileNames.withSavegameExtension("partida.json"));
        assertEquals("mi-red.ltr", FileNames.withSavegameExtension("mi-red.ltr"));
    }

    @Test
    @DisplayName("scenarios get .ltr when the name has none")
    void scenario_appendsLtrWhenMissing() {
        assertEquals("mi-red.ltr", FileNames.withScenarioExtension("mi-red"));
        assertEquals("scenario.ltr", FileNames.withScenarioExtension(""));
        assertEquals("scenario.ltr", FileNames.withScenarioExtension((String) null));
    }

    @Test
    @DisplayName("the directory part is preserved")
    void directory_isPreserved() {
        assertEquals("save/partida.json", FileNames.withSavegameExtension("save/partida"));
        assertEquals("save\\partida.ltr", FileNames.withScenarioExtension("save\\partida"));
    }

    @Test
    @DisplayName("File overloads append the extension and pass null through")
    void fileOverloads() {
        assertNull(FileNames.withSavegameExtension((File) null));
        assertNull(FileNames.withScenarioExtension((File) null));
        assertEquals(new File("partida.json"),
                FileNames.withSavegameExtension(new File("partida")));
        assertEquals(new File("mi-red.ltr"), FileNames.withScenarioExtension(new File("mi-red")));
    }
}
