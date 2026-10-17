package letrain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D4 (#763): "save as defaults" merges the running game's effective settings into a
 * {@code letrain.cfg}. All files are temporary: the repository's own cfg is never touched.
 */
@DisplayName("ConfigFileWriter: save-as-defaults merge (D4, S3a)")
class ConfigFileWriterTest {

    @TempDir
    Path dir;

    private static Map<String, String> defaults(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private static Properties load(Path cfg) throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(cfg)) {
            props.load(in);
        }
        return props;
    }

    @Test
    @DisplayName("existing keys are updated in place; comments and unrelated lines survive")
    void merge_updatesInPlaceAndKeepsTheRest() throws Exception {
        Path cfg = dir.resolve("letrain.cfg");
        Files.writeString(cfg, "# header comment\n" + "time.dayDurationSeconds=1440\n" + "\n"
                + "# another comment\n" + "fuelCostPerMeter=0.5\n" + "legacy.key=keep-me\n");

        ConfigFileWriter.writeDefaults(cfg.toFile(),
                defaults("time.dayDurationSeconds", "720", "world.latitude", "10.0"));

        String text = Files.readString(cfg);
        assertTrue(text.contains("# header comment"), text);
        assertTrue(text.contains("# another comment"), text);
        assertTrue(text.contains("legacy.key=keep-me"), text);
        assertTrue(text.contains("time.dayDurationSeconds=720"), text);
        assertFalse(text.contains("time.dayDurationSeconds=1440"), text);
        assertTrue(text.contains(ConfigFileWriter.APPENDED_HEADER), text);

        Properties loaded = load(cfg);
        assertEquals("720", loaded.getProperty("time.dayDurationSeconds"));
        assertEquals("10.0", loaded.getProperty("world.latitude"));
        assertEquals("0.5", loaded.getProperty("fuelCostPerMeter"));
        assertEquals("keep-me", loaded.getProperty("legacy.key"));
    }

    @Test
    @DisplayName("a missing file is created with every key under the header")
    void missingFile_isCreated() throws Exception {
        Path cfg = dir.resolve("new.cfg");

        ConfigFileWriter.writeDefaults(cfg.toFile(),
                defaults("startingBalance", "500", "seed", "1"));

        assertTrue(Files.exists(cfg));
        Properties loaded = load(cfg);
        assertEquals("500", loaded.getProperty("startingBalance"));
        assertEquals("1", loaded.getProperty("seed"));
    }

    @Test
    @DisplayName("an empty update leaves the existing file untouched")
    void emptyValues_doNotTouchTheFile() throws Exception {
        Path cfg = dir.resolve("letrain.cfg");
        Files.writeString(cfg, "fuelCostPerMeter=0.5\n");

        ConfigFileWriter.writeDefaults(cfg.toFile(), Map.of());
        ConfigFileWriter.writeDefaults(cfg.toFile(), null);

        assertEquals("fuelCostPerMeter=0.5\n", Files.readString(cfg));
    }

    @Test
    @DisplayName("a colon separator is accepted and normalised to '='")
    void colonSeparator_isNormalised() throws Exception {
        Path cfg = dir.resolve("letrain.cfg");
        Files.writeString(cfg, "world.latitude: 40.0\n");

        ConfigFileWriter.writeDefaults(cfg.toFile(), defaults("world.latitude", "10.0"));

        assertEquals("world.latitude=10.0\n", Files.readString(cfg));
    }

    @Test
    @DisplayName("a null target is rejected")
    void nullTarget_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigFileWriter.writeDefaults(null, defaults("a", "1")));
    }

    @Test
    @DisplayName("writing the same values twice does not duplicate keys")
    void writingTwice_isIdempotent() throws Exception {
        Path cfg = dir.resolve("letrain.cfg");
        File target = cfg.toFile();
        Map<String, String> values = defaults("world.latitude", "10.0", "fuelCostPerMeter", "1.5");

        ConfigFileWriter.writeDefaults(target, values);
        String first = Files.readString(cfg);
        ConfigFileWriter.writeDefaults(target, values);

        assertEquals(first, Files.readString(cfg), "a second save must not duplicate keys");
    }

    @Test
    @DisplayName("writeDefaultsOrMessage reports null on success and the failure message otherwise")
    void writeDefaultsOrMessage_reportsSuccessOrFailure() throws Exception {
        Path cfg = dir.resolve("letrain.cfg");

        assertEquals(null,
                ConfigFileWriter.writeDefaultsOrMessage(cfg.toFile(), defaults("seed", "1")));
        assertTrue(Files.readString(cfg).contains("seed=1"));

        // A directory target cannot be written: the dialog shows the reason instead of throwing.
        org.junit.jupiter.api.Assertions.assertNotNull(
                ConfigFileWriter.writeDefaultsOrMessage(dir.toFile(), defaults("seed", "1")));
    }
}
