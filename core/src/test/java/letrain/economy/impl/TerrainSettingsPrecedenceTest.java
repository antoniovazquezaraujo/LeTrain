package letrain.economy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import letrain.mvp.impl.EventLogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Terrain settings: new percent keys vs legacy thresholds")
class TerrainSettingsPrecedenceTest {

    @Test
    @DisplayName("with no terrain keys anywhere the D2 percent defaults apply (25 % / 10 %)")
    void defaults_areD2Percent() {
        EconomyManager manager = new EconomyManager(new EventLogManager());

        assertTrue(manager.isTerrainPercentEnabled());
        assertEquals(25, manager.getWaterPercent());
        assertEquals(10, manager.getMountainPercent());
    }

    @Test
    @DisplayName("legacy keys alone switch to the raw thresholds (old configs keep their look)")
    void legacyKeys_alone_keepLegacyMode() {
        EconomyManager manager = new EconomyManager(new EventLogManager());

        manager.applyConfig(Map.of("threshold.WATER", "99.5", "threshold.ROCK", "199.5"));

        assertFalse(manager.isTerrainPercentEnabled());
        assertEquals(99.5f, manager.getWaterThreshold(), 0.001f);
        assertEquals(199.5f, manager.getRockThreshold(), 0.001f);
    }

    @Test
    @DisplayName("when one source carries both, the percent keys win (they replace the thresholds)")
    void percentKeys_winWithinSameSource() {
        EconomyManager manager = new EconomyManager(new EventLogManager());

        manager.applyConfig(Map.of("terrain.waterPercent", "40", "terrain.mountainPercent", "20",
                "threshold.WATER", "130", "threshold.ROCK", "180"));

        assertTrue(manager.isTerrainPercentEnabled());
        assertEquals(40, manager.getWaterPercent());
        assertEquals(20, manager.getMountainPercent());
    }

    @Test
    @DisplayName("one missing percent key keeps the value from the previous source")
    void missingPercentKey_keepsPreviousValue() {
        EconomyManager manager = new EconomyManager(new EventLogManager());

        manager.applyConfig(Map.of("terrain.waterPercent", "40", "terrain.mountainPercent", "20"));
        manager.applyConfig(Map.of("terrain.mountainPercent", "5"));

        assertTrue(manager.isTerrainPercentEnabled());
        assertEquals(40, manager.getWaterPercent());
        assertEquals(5, manager.getMountainPercent());
    }

    @Test
    @DisplayName("sources apply in order: a later scenario wins over the earlier cfg, in both modes")
    void laterSource_wins() {
        EconomyManager scenarioPercent = new EconomyManager(new EventLogManager());
        scenarioPercent.applyConfig(Map.of("threshold.WATER", "100", "threshold.ROCK", "200"));
        scenarioPercent
                .applyConfig(Map.of("terrain.waterPercent", "30", "terrain.mountainPercent", "15"));
        assertTrue(scenarioPercent.isTerrainPercentEnabled(),
                "scenario percent wins over cfg legacy");
        assertEquals(30, scenarioPercent.getWaterPercent());

        EconomyManager scenarioLegacy = new EconomyManager(new EventLogManager());
        scenarioLegacy
                .applyConfig(Map.of("terrain.waterPercent", "30", "terrain.mountainPercent", "15"));
        scenarioLegacy.applyConfig(Map.of("threshold.WATER", "100", "threshold.ROCK", "200"));
        assertFalse(scenarioLegacy.isTerrainPercentEnabled(),
                "scenario legacy wins over cfg percent");
        assertEquals(100f, scenarioLegacy.getWaterThreshold(), 0.001f);
    }

    @Test
    @DisplayName("snapshots round-trip across modes without switching it by accident")
    void effectiveConfig_roundTripsAcrossModes() {
        EconomyManager legacy = new EconomyManager(new EventLogManager());
        legacy.applyConfig(Map.of("threshold.WATER", "110", "threshold.ROCK", "190"));
        Map<String, String> legacySnapshot = legacy.effectiveConfig();
        assertFalse(legacySnapshot.containsKey("terrain.waterPercent"),
                "a legacy snapshot must not carry the percent keys");

        EconomyManager fromLegacy = new EconomyManager(new EventLogManager());
        fromLegacy.applyConfig(legacySnapshot);
        assertFalse(fromLegacy.isTerrainPercentEnabled());
        assertEquals(110f, fromLegacy.getWaterThreshold(), 0.001f);
        assertEquals(legacySnapshot.keySet(), fromLegacy.effectiveConfig().keySet());

        fromLegacy
                .applyConfig(Map.of("terrain.waterPercent", "35", "terrain.mountainPercent", "15"));
        Map<String, String> percentSnapshot = fromLegacy.effectiveConfig();
        assertTrue(percentSnapshot.containsKey("terrain.waterPercent"));
        assertTrue(percentSnapshot.containsKey("threshold.WATER"),
                "legacy thresholds stay for older readers");

        EconomyManager fromPercent = new EconomyManager(new EventLogManager());
        fromPercent.applyConfig(percentSnapshot);
        assertTrue(fromPercent.isTerrainPercentEnabled());
        assertEquals(35, fromPercent.getWaterPercent());
        assertEquals(15, fromPercent.getMountainPercent());
        assertEquals(percentSnapshot.keySet(), fromPercent.effectiveConfig().keySet());
    }

    @Test
    @DisplayName("fallBackToLegacyTerrain keeps a pre-settings savegame on its old thresholds")
    void fallbackToLegacy_keepsSerializedThresholds() {
        EconomyManager manager = new EconomyManager(new EventLogManager());

        manager.fallBackToLegacyTerrain();

        assertFalse(manager.isTerrainPercentEnabled());
        assertEquals(130f, manager.getWaterThreshold(), 0.001f);
        assertEquals(180f, manager.getRockThreshold(), 0.001f);
    }
}
