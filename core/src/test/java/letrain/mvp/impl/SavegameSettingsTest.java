package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.Map;
import letrain.ground.TerrainCalibrator;
import letrain.settings.SettingsApplyResult;
import letrain.settings.SettingsCatalog;
import letrain.time.GameClock;
import letrain.time.GameTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Savegame: the game's settings travel with it")
class SavegameSettingsTest {

    private static final int GRID = TerrainCalibrator.SAMPLE_SIZE;
    private static final int STRIDE = 4;

    private final GameSaveService saves = new GameSaveService();

    /** Deterministic terrain sampling over the calibration region (no cells materialized). */
    private static int[] terrainSignature(Model model) {
        int side = (GRID + STRIDE - 1) / STRIDE;
        int[] signature = new int[side * side];
        int i = 0;
        for (int y = 0; y < GRID; y += STRIDE) {
            for (int x = 0; x < GRID; x += STRIDE) {
                signature[i++] = model.getGroundMap().getBackgroundTerrain(x, y);
            }
        }
        return signature;
    }

    @Test
    @DisplayName("serialization writes a settings section with the effective keys")
    void savegame_hasSettingsSection() throws Exception {
        Model model = new Model(123);
        model.getEconomyManager()
                .applyConfig(Map.of("terrain.waterPercent", "30", "terrain.mountainPercent", "15"));

        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(saves.toBytes(model));

        assertTrue(tree.has("settings"), tree.toPrettyString());
        assertEquals("30", tree.path("settings").path("terrain.waterPercent").asText());
        assertEquals("15", tree.path("settings").path("terrain.mountainPercent").asText());
    }

    @Test
    @DisplayName("loading applies the settings carried by the savegame")
    void roundTrip_appliesSettings() {
        Model model = new Model(123);
        model.getEconomyManager()
                .applyConfig(Map.of("terrain.waterPercent", "30", "terrain.mountainPercent", "15",
                        "startingBalance", "500", "ui.highlightBlockedTracks", "false"));

        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        assertTrue(loaded.getEconomyManager().isTerrainPercentEnabled());
        assertEquals(30, loaded.getEconomyManager().getWaterPercent());
        assertEquals(15, loaded.getEconomyManager().getMountainPercent());
        assertFalse(loaded.getEconomyManager().isHighlightBlockedTracks());
        assertEquals(500f, loaded.getEconomyManager().getBalance(), 0.001f);
    }

    @Test
    @DisplayName("the loaded world generates the same terrain as the saved one")
    void roundTrip_terrainMatches() {
        Model model = new Model(123);
        model.getEconomyManager()
                .applyConfig(Map.of("terrain.waterPercent", "40", "terrain.mountainPercent", "10"));

        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        assertArrayEquals(terrainSignature(model), terrainSignature(loaded));
    }

    @Test
    @DisplayName("a fresh new game round-trips with the D2 defaults")
    void freshGame_roundTripsWithDefaults() {
        Model loaded = saves.fromBytes(saves.toBytes(new Model(123)));

        assertNotNull(loaded);
        assertTrue(loaded.getEconomyManager().isTerrainPercentEnabled());
        assertEquals(SettingsCatalog.DEFAULT_WATER_PERCENT,
                loaded.getEconomyManager().getWaterPercent());
        assertEquals(SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT,
                loaded.getEconomyManager().getMountainPercent());
    }

    @Test
    @DisplayName("the clock origin travels with the settings and the load keeps the instant (S2b)")
    void roundTrip_restoresTheRunningClockWithCustomStart() {
        Model model = Model.newGame(123, Map.of("time.startDay", "300", "time.startTime", "06:30"));
        for (int i = 0; i < 200; i++) {
            model.getGameClock().tick();
        }
        GameTime saved = model.getGameClock().now();

        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        assertEquals(new GameTime(300, 6, 40), saved, "10 game minutes after the custom start");
        assertEquals("300", loaded.getEconomyManager().effectiveConfig().get("time.startDay"));
        assertEquals("06:30", loaded.getEconomyManager().effectiveConfig().get("time.startTime"));
        assertEquals(saved, loaded.getGameClock().now(),
                "the load restores the running clock, it never jumps back to the start");
        assertEquals(model.getGameClock().elapsedTicks(), loaded.getGameClock().elapsedTicks());
    }

    @Test
    @DisplayName("a savegame without the start keys loads at the default origin (legacy, S2b)")
    void oldSavegame_withoutStartKeys_keepsDefaultOrigin() throws Exception {
        Model model = new Model(123);
        for (int i = 0; i < 60 * GameClock.TICKS_PER_SECOND; i++) {
            model.getGameClock().tick();
        }

        // Emulate a savegame written before S2b: the settings section exists but carries no
        // start keys.
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(saves.toBytes(model));
        ObjectNode settings = (ObjectNode) tree.path("settings");
        settings.remove("time.startDay");
        settings.remove("time.startTime");
        byte[] oldSave = mapper.writeValueAsBytes(tree);

        Model loaded = saves.load(new ByteArrayInputStream(oldSave)).orElseThrow();

        assertEquals(new GameTime(1, 9, 0), loaded.getGameClock().now(),
                "day 1, 08:00 origin plus the restored hour counter");
    }

    @Test
    @DisplayName("the load re-syncs the clock day length and latitude from the savegame (#761)")
    void roundTrip_restoresClockDayLengthAndLatitude() {
        Model model = Model.newGame(123,
                Map.of("time.dayDurationSeconds", "720", "world.latitude", "10.0"));
        // 3 game hours at 720 s/day: 10 ticks per game minute.
        for (int i = 0; i < 3 * 60 * 10; i++) {
            model.getGameClock().tick();
        }
        GameTime saved = model.getGameClock().now();

        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        assertEquals(new GameTime(1, 11, 0), saved, "08:00 plus three game hours");
        assertEquals(saved, loaded.getGameClock().now(),
                "the load keeps the saved instant, it does not jump");
        assertEquals(10.0, loaded.getGameClock().getLatitude(), 0.001,
                "the saved latitude reaches the running clock");

        // 14400 more ticks add exactly one game day only with the restored 720 s scale (the
        // cfg/default 1440 would advance half a day).
        for (int i = 0; i < 720 * GameClock.TICKS_PER_SECOND; i++) {
            loaded.getGameClock().tick();
        }

        assertEquals(new GameTime(2, 11, 0), loaded.getGameClock().now(),
                "the loaded clock keeps ticking at the saved 720 s day length");
    }

    @Test
    @DisplayName("a savegame without the clock keys keeps the cfg/defaults clock (#761 legacy)")
    void oldSavegame_withoutClockKeys_keepsCfgClock() throws Exception {
        Model model = new Model(123);
        for (int i = 0; i < 12 * 60 * GameClock.TICKS_PER_SECOND; i++) {
            model.getGameClock().tick();
        }
        GameTime saved = model.getGameClock().now();
        GameTime target = new GameTime(1, 21, 0);

        // Emulate a savegame whose settings section carries no clock keys (pre-settings saves).
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(saves.toBytes(model));
        ObjectNode settings = (ObjectNode) tree.path("settings");
        settings.remove("time.dayDurationSeconds");
        settings.remove("world.latitude");
        byte[] oldSave = mapper.writeValueAsBytes(tree);

        Model loaded = saves.load(new ByteArrayInputStream(oldSave)).orElseThrow();

        assertEquals(saved, loaded.getGameClock().now(),
                "without the keys the load keeps the clock wired from the cfg/defaults");
        assertEquals(model.getGameClock().getLatitude(), loaded.getGameClock().getLatitude(), 0.001,
                "the latitude is not touched without the key");
        assertEquals(model.getGameClock().ticksUntil(target),
                loaded.getGameClock().ticksUntil(target), "the day scale is not touched either");
    }

    @Test
    @DisplayName("a savegame without the settings section keeps its legacy thresholds (old saves)")
    void oldSavegame_keepsLegacyLook() throws Exception {
        Model model = new Model(123);
        model.getEconomyManager()
                .applyConfig(Map.of("threshold.WATER", "100", "threshold.ROCK", "200"));

        // Emulate a savegame written before this slice: no settings section, and no percent fields
        // in the economy block either.
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(saves.toBytes(model));
        tree.remove("settings");
        ObjectNode economy = (ObjectNode) tree.path("economyManager");
        economy.remove("terrainPercentEnabled");
        economy.remove("terrainWaterPercent");
        economy.remove("terrainMountainPercent");
        byte[] oldSave = mapper.writeValueAsBytes(tree);

        Model loaded = saves.load(new ByteArrayInputStream(oldSave)).orElseThrow();

        assertFalse(loaded.getEconomyManager().isTerrainPercentEnabled());
        assertEquals(100f, loaded.getEconomyManager().getWaterThreshold(), 0.001f);
        assertEquals(200f, loaded.getEconomyManager().getRockThreshold(), 0.001f);
        assertArrayEquals(terrainSignature(model), terrainSignature(loaded),
                "an old savegame must keep its historical look");
    }

    @Test
    @DisplayName("scenario-fixed keys travel in the settings section and survive a load (S3a)")
    void roundTrip_keepsScenarioLock() throws Exception {
        Model model = new Model(123);
        Map<String, String> scenario = new java.util.LinkedHashMap<>();
        scenario.put("time.dayDurationSeconds", "720");
        scenario.put("world.latitude", "10.0");
        model.applyScenarioConfiguration(scenario);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(saves.toBytes(model));
        assertEquals("time.dayDurationSeconds,world.latitude",
                tree.path("settings").path("scenario.keys").asText(),
                "the lock lives inside the settings section, not as its own JSON block");

        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        assertEquals(
                new java.util.LinkedHashSet<>(
                        java.util.List.of("time.dayDurationSeconds", "world.latitude")),
                loaded.getScenarioSettingKeys());
        SettingsApplyResult result =
                loaded.applyLiveSettings(Map.of("time.dayDurationSeconds", "900"));
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                result.rejections().get("time.dayDurationSeconds"),
                "the lock still refuses live changes after the round-trip");
    }

    @Test
    @DisplayName("a savegame without the lock entry loads unlocked (legacy, S3a)")
    void oldSavegame_withoutLock_isUnlocked() {
        Model loaded = saves.fromBytes(saves.toBytes(new Model(123)));

        assertNotNull(loaded);
        assertTrue(loaded.getScenarioSettingKeys().isEmpty());
        assertFalse(loaded.applyLiveSettings(Map.of("startingBalance", "10")).hasRejections(),
                "a plain game has no scenario lock");
    }
}
