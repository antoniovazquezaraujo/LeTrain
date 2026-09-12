package letrain.economy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import letrain.mvp.impl.EventLogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("EconomyManager settings snapshot/apply")
class EconomyManagerConfigTest {

    @Test
    @DisplayName("effectiveConfig + applyConfig round-trip the settings (terrain thresholds included)")
    void config_roundTrip() {
        EconomyManager source = new EconomyManager(new EventLogManager());
        Map<String, String> config = source.effectiveConfig();
        config.put("threshold.WATER", "99.5");
        config.put("threshold.ROCK", "199.5");

        EconomyManager target = new EconomyManager(new EventLogManager());
        target.applyConfig(config);

        assertEquals(99.5f, target.getWaterThreshold(), 0.001f);
        assertEquals(199.5f, target.getRockThreshold(), 0.001f);
        assertEquals(source.getGoldThreshold(), target.getGoldThreshold(), 0.001f);
        assertEquals(config.keySet(), target.effectiveConfig().keySet(),
                "the snapshot must expose the same keys");
    }

    @Test
    @DisplayName("firstConfig returns the first directory holding a letrain.cfg")
    void firstConfig_order(@TempDir Path tmp) throws Exception {
        Path a = java.nio.file.Files.createDirectories(tmp.resolve("a"));
        Path b = java.nio.file.Files.createDirectories(tmp.resolve("b"));
        assertNull(EconomyManager.firstConfig(List.of(a.toFile(), b.toFile())), "no config anywhere");

        java.nio.file.Files.writeString(b.resolve("letrain.cfg"), "startingBalance=1");
        assertEquals(b.resolve("letrain.cfg").toFile(),
                EconomyManager.firstConfig(List.of(a.toFile(), b.toFile())));

        java.nio.file.Files.writeString(a.resolve("letrain.cfg"), "startingBalance=2");
        assertEquals(a.resolve("letrain.cfg").toFile(),
                EconomyManager.firstConfig(List.of(a.toFile(), b.toFile())),
                "the working directory must win");
    }
}
