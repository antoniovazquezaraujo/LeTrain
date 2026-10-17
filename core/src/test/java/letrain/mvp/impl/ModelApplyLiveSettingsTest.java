package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import letrain.settings.SettingsApplyResult;
import letrain.time.GameTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S3a (#763): the apply-by-state API. LIVE/REAPPLY keys reach the running game (clock re-synced for
 * day length and latitude), NEW_GAME and scenario-locked keys are refused with their reason.
 */
@DisplayName("Model.applyLiveSettings: apply-by-state filtering (S3a, #763)")
class ModelApplyLiveSettingsTest {

    @Test
    @DisplayName("LIVE keys reach the running game and are normalised")
    void liveKeys_apply() {
        Model model = new Model(1);

        SettingsApplyResult result = model.applyLiveSettings(Map.of("startingBalance", "777",
                "ui.highlightBlockedTracks", "FALSE", "fuelCostPerMeter", "2.5"));

        assertEquals(3, result.applied().size(), result.toString());
        assertFalse(result.hasRejections(), result.toString());
        assertEquals(777f, model.getEconomyManager().getBalance(), 0.001f);
        assertFalse(model.getEconomyManager().isHighlightBlockedTracks());
        assertEquals("2.5", model.getEconomyManager().effectiveConfig().get("fuelCostPerMeter"));
    }

    @Test
    @DisplayName("REAPPLY keys apply and re-sync the solar latitude")
    void reapplyKeys_apply() {
        Model model = new Model(1);

        SettingsApplyResult result =
                model.applyLiveSettings(Map.of("world.latitude", "-45.5", "map.VIEW_RADIUS", "30"));

        assertFalse(result.hasRejections(), result.toString());
        assertEquals(-45.5, model.getGameClock().getLatitude(), 0.001);
        assertEquals(30, model.getEconomyManager().getViewRadius());
    }

    @Test
    @DisplayName("a live day length change re-syncs the clock without jumping the instant")
    void dayDuration_reSyncsClockAndKeepsInstant() {
        Model model = new Model(1);
        model.getGameClock().setTime(new GameTime(1, 11, 0));

        SettingsApplyResult result =
                model.applyLiveSettings(Map.of("time.dayDurationSeconds", "720"));

        assertTrue(result.applied().contains("time.dayDurationSeconds"), result.toString());
        assertEquals(720, model.getEconomyManager().getDayDurationSeconds());
        assertEquals(new GameTime(1, 11, 0), model.getGameClock().now(),
                "the calendar keeps the displayed instant");

        for (int i = 0; i < 10; i++) {
            model.getGameClock().tick();
        }

        assertEquals(new GameTime(1, 11, 1), model.getGameClock().now(),
                "720 s/day means 10 ticks per game minute");
    }

    @Test
    @DisplayName("NEW_GAME keys are rejected with their reason and never applied")
    void newGameKeys_areRejected() {
        Model model = new Model(1);
        model.getGameClock().setTime(new GameTime(2, 9, 30));
        long ticksBefore = model.getGameClock().elapsedTicks();

        SettingsApplyResult result = model.applyLiveSettings(Map.of("seed", "42",
                "terrain.waterPercent", "50", "time.startDay", "300", "time.startTime", "06:30"));

        assertTrue(result.applied().isEmpty(), result.toString());
        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME, result.rejections().get("seed"));
        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME,
                result.rejections().get("terrain.waterPercent"));
        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME,
                result.rejections().get("time.startDay"));
        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME,
                result.rejections().get("time.startTime"));
        assertEquals(new GameTime(2, 9, 30), model.getGameClock().now(),
                "rejected start keys never move the clock");
        assertEquals(ticksBefore, model.getGameClock().elapsedTicks());
    }

    @Test
    @DisplayName("scenario-fixed keys are rejected with 'defined by scenario' (D10)")
    void scenarioLockedKeys_areRejected() {
        Model model = new Model(1);
        Map<String, String> scenario = new LinkedHashMap<>();
        scenario.put("time.dayDurationSeconds", "720");
        scenario.put("world.latitude", "10.0");
        scenario.put("fuelCostPerMeter", "1.0");
        model.applyScenarioConfiguration(scenario);

        SettingsApplyResult result =
                model.applyLiveSettings(Map.of("time.dayDurationSeconds", "900", "world.latitude",
                        "20.0", "fuelCostPerMeter", "2.0", "startingBalance", "5"));

        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                result.rejections().get("time.dayDurationSeconds"));
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                result.rejections().get("world.latitude"));
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                result.rejections().get("fuelCostPerMeter"));
        assertTrue(result.applied().contains("startingBalance"), result.toString());
        assertEquals(720, model.getEconomyManager().getDayDurationSeconds());
        assertEquals("1.0", model.getEconomyManager().effectiveConfig().get("fuelCostPerMeter"),
                "the scenario value stays untouched");
        assertEquals(10.0, model.getGameClock().getLatitude(), 0.001);
    }

    @Test
    @DisplayName("invalid values and unknown keys are rejected, never applied")
    void invalidAndUnknown_areRejected() {
        Model model = new Model(1);

        SettingsApplyResult result = model.applyLiveSettings(
                Map.of("world.latitude", "95.0", "not.a.setting", "1", "startingBalance", "123"));

        assertTrue(result.applied().contains("startingBalance"), result.toString());
        assertTrue(result.rejections().get("world.latitude").contains("≤ 90"), result.toString());
        assertEquals(SettingsApplyResult.UNKNOWN_SETTING, result.rejections().get("not.a.setting"));
        assertEquals(40.0, model.getGameClock().getLatitude(), 0.001,
                "the invalid value never lands");
    }

    @Test
    @DisplayName("an empty batch is a no-op")
    void emptyBatch_isNoOp() {
        Model model = new Model(1);
        long ticksBefore = model.getGameClock().elapsedTicks();

        SettingsApplyResult result = model.applyLiveSettings(Map.of());

        assertTrue(result.applied().isEmpty());
        assertFalse(result.hasRejections());
        assertEquals(ticksBefore, model.getGameClock().elapsedTicks());
    }

    @Test
    @DisplayName("a scenario rebuild wires the clock with the scenario configuration (S3a)")
    void scenarioConfiguration_wiresClock() {
        Model model = new Model(1);
        Map<String, String> scenario = new LinkedHashMap<>();
        scenario.put("time.dayDurationSeconds", "720");
        scenario.put("time.startDay", "300");
        scenario.put("time.startTime", "06:30");

        model.applyScenarioConfiguration(scenario);

        assertEquals(720, model.getEconomyManager().getDayDurationSeconds());
        assertEquals(new GameTime(300, 6, 30), model.getGameClock().now());
        for (int i = 0; i < 10; i++) {
            model.getGameClock().tick();
        }
        assertEquals(new GameTime(300, 6, 31), model.getGameClock().now(),
                "10 ticks are one game minute at 720 s/day");
    }
}
