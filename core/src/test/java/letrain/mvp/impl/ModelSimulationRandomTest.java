package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import letrain.mvp.impl.services.SimulationService;
import letrain.track.CargoTypes;
import letrain.track.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #811: the world owns one seeded simulation RNG derived from its terrain seed, so the same seed
 * yields the same stochastic cargo evolution (ADR-020 replay). The RNG is transient and re-derived
 * on load, not serialized.
 */
@DisplayName("Model simulation randomness is seeded from the world seed (#811)")
class ModelSimulationRandomTest {

    @Test
    @DisplayName("two models with the same seed expose the same RNG sequence")
    void should_DeriveTheSameStreamFromTheSameSeed() {
        Model first = new Model(42);
        Model second = new Model(42);

        assertEquals(draw(first.getSimulationRandom(), 8), draw(second.getSimulationRandom(), 8),
                "same seed ⇒ same simulation stream");
    }

    @Test
    @DisplayName("a different seed yields a different RNG sequence")
    void should_DeriveADifferentStreamFromADifferentSeed() {
        Model first = new Model(42);
        Model other = new Model(43);

        assertNotEquals(draw(first.getSimulationRandom(), 8), draw(other.getSimulationRandom(), 8));
    }

    @Test
    @DisplayName("setSeed re-derives the stream from the new seed")
    void should_ReseedOnSetSeed() {
        Model model = new Model(42);
        draw(model.getSimulationRandom(), 5); // advance the old stream

        model.setSeed(7);

        assertEquals(draw(new Random(7), 6), draw(model.getSimulationRandom(), 6),
                "after setSeed the stream restarts from the new seed");
    }

    @Test
    @DisplayName("loading a savegame re-derives the stream from the restored seed")
    void should_ReseedOnLoad() {
        // Arrange: advance the live RNG so the saved state differs from a fresh run.
        Model live = new Model(123);
        draw(live.getSimulationRandom(), 5);
        GameSaveService saves = new GameSaveService();

        // Act
        Model loaded = saves.fromBytes(saves.toBytes(live));

        // Assert: the transient RNG was not serialized; postLoadInit re-derives it from the seed.
        assertNotNull(loaded);
        assertEquals(123, loaded.getSeed());
        assertEquals(draw(new Random(123), 8), draw(loaded.getSimulationRandom(), 8),
                "a loaded world replays the same deterministic stream as a fresh build");
    }

    @Test
    @DisplayName("two models with the same seed produce the same cargo evolution across ticks")
    void should_ProduceTheSameCargoSequenceForTheSameSeed() {
        List<Integer> first = cargoSequence(42, 1000);
        List<Integer> second = cargoSequence(42, 1000);

        assertEquals(first, second, "same seed ⇒ same station storage at every tick");
    }

    @Test
    @DisplayName("a different seed produces a different cargo evolution")
    void should_ProduceADifferentCargoSequenceForADifferentSeed() {
        assertNotEquals(cargoSequence(42, 1000), cargoSequence(43, 1000));
    }

    /** Runs the industrial tick on a consumer station and records its storage after every tick. */
    private static List<Integer> cargoSequence(int seed, int ticks) {
        Model model = new Model(seed);
        Station station = new Station(1);
        station.setRole(CargoTypes.StationRole.CONSUMER);
        station.setCargoType(CargoTypes.COAL);
        station.setStorage(400);
        model.addStation(station);

        SimulationService service = new SimulationService(model);
        List<Integer> storage = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            service.handleIndustrialActions();
            storage.add(station.getStorage());
        }
        return storage;
    }

    private static List<Integer> draw(Random random, int count) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(random.nextInt());
        }
        return values;
    }
}
