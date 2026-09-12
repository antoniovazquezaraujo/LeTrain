package letrain.economy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import letrain.mvp.impl.EventLogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
