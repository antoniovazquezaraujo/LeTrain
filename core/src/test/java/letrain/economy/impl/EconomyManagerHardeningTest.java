package letrain.economy.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.mvp.impl.EventLogManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #812: fuel is charged per distance actually travelled (not per consist length), the Jackson
 * constructor restores the default tables so minimal/old savegames load, missing enum prices fall
 * back to zero and a crash without an event log no longer aborts the pipeline.
 */
@DisplayName("EconomyManager hardening (#812)")
class EconomyManagerHardeningTest {

    @Test
    @DisplayName("chargeFuel bills rate × distance and ignores the number of linkers")
    void should_ChargeFuelByDistanceNotByConsistLength() {
        EconomyManager manager = new EconomyManager(new EventLogManager());
        manager.applyConfig(Map.of("fuelCostPerMeter", "2.0"));
        Train shortTrain = trainWithLinkers(1);
        Train longTrain = trainWithLinkers(5);
        float start = manager.getBalance();

        manager.chargeFuel(shortTrain, 5);
        manager.chargeFuel(longTrain, 5);

        // 2.0 per unit × 5 units, twice. A per-linker multiplier would make the long train cost 5x.
        assertEquals(20f, manager.getTotalExpenses(), 0.001f);
        assertEquals(start - 20f, manager.getBalance(), 0.001f);
    }

    @Test
    @DisplayName("chargeFuel clamps a negative distance to zero and uses the absolute rate")
    void should_ClampNegativeDistanceAndUseTheAbsoluteRate() {
        EconomyManager manager = new EconomyManager(new EventLogManager());
        manager.applyConfig(Map.of("fuelCostPerMeter", "-1.5"));
        Train train = trainWithLinkers(2);

        manager.chargeFuel(train, -5);
        assertEquals(0f, manager.getTotalExpenses(), 0.001f,
                "travelling backwards must not credit fuel");

        manager.chargeFuel(train, 4);
        assertEquals(6f, manager.getTotalExpenses(), 0.001f,
                "the rate sign must not turn a cost into income");
    }

    @Test
    @DisplayName("a minimal savegame without prices/cargoBaseValues loads with the defaults")
    void should_LoadAMinimalSavegameWithDefaultTables() throws Exception {
        // Arrange: an old save shape that omits prices, cargoBaseValues and delays.
        EconomyManager manager =
                new ObjectMapper().readValue("{\"balance\":123.0}", EconomyManager.class);

        // Act/Assert: defaults are present, so the first spend/earn cannot NPE.
        assertEquals(123f, manager.getBalance(), 0.001f);
        assertEquals(1000f, manager.getCost(ExpenseType.CONSTRUCTED_FORK), 0.001f);
        manager.spend(ExpenseType.CONSTRUCTED_FORK);
        manager.earn(ExpenseType.LOAD_PASSENGERS);
        assertEquals(123f, manager.getBalance(), 0.001f);
    }

    @Test
    @DisplayName("getCost/spend/earn tolerate a price table missing an enum key")
    void should_TolerateAnAbsentPrice() {
        EconomyManager manager = new EconomyManager(new EventLogManager());
        float start = manager.getBalance();
        manager.prices.clear();

        assertEquals(0f, manager.getCost(ExpenseType.CONSTRUCTED_FORK), 0.001f);
        manager.spend(ExpenseType.CONSTRUCTED_FORK);
        manager.spend(ExpenseType.CONSTRUCTED_FORK, 4);
        manager.earn(ExpenseType.LOAD_PASSENGERS);
        manager.earn(ExpenseType.LOAD_PASSENGERS, 4);

        assertEquals(start, manager.getBalance(), 0.001f, "an unknown price is free, never an NPE");
    }

    @Test
    @DisplayName("onTrainCrashed without an event log does not throw")
    void should_NotCrashWhenTheEventLogIsMissing() {
        EconomyManager manager = new EconomyManager();

        assertDoesNotThrow(() -> manager.onTrainCrashed(new Train(7)));
        assertEquals(1_000_000f, manager.getTotalExpenses(), 0.001f, "the crash is still charged");
    }

    private static Train trainWithLinkers(int count) {
        Train train = new Train(1);
        for (int i = 0; i < count; i++) {
            train.pushBack(new Locomotive(i + 1, 'A'));
        }
        return train;
    }
}
