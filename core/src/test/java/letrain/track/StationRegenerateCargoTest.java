package letrain.track;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #811: {@link Station#regenerateCargo(Random)} uses the RNG supplied by the world instead of
 * {@code Math.random()}, so the caller can make cargo evolution reproducible (same seed ⇒ same
 * stream).
 */
@DisplayName("Station.regenerateCargo honours the supplied Random (#811)")
class StationRegenerateCargoTest {

    @Test
    @DisplayName("a consumer consumes when the supplied roll is below the consumption chance")
    void should_ConsumeOnALowRoll() {
        Station station = consumer(5);

        station.regenerateCargo(constant(0.0));

        assertEquals(4, station.getStorage());
    }

    @Test
    @DisplayName("a consumer keeps its stock when the supplied roll is above the chance")
    void should_NotConsumeOnAHighRoll() {
        Station station = consumer(5);

        station.regenerateCargo(constant(0.5));

        assertEquals(5, station.getStorage(), "no Math.random may leak into the decision");
    }

    @Test
    @DisplayName("a producer ignores the RNG and regenerates its stock")
    void should_RegenerateAProducerWithoutRandomness() {
        Station station = new Station(1);
        station.setRole(CargoTypes.StationRole.PRODUCER);
        station.setCargoType(CargoTypes.COAL);
        station.setStorage(10);

        station.regenerateCargo(null);

        assertEquals(11, station.getStorage(), "producer regeneration is deterministic");
    }

    @Test
    @DisplayName("the same seeded RNG drives the same consumer sequence")
    void should_RepeatTheSameSequenceForTheSameSeed() {
        Station first = consumer(500);
        Station second = consumer(500);
        Random firstRandom = new Random(77);
        Random secondRandom = new Random(77);

        for (int tick = 0; tick < 500; tick++) {
            first.regenerateCargo(firstRandom);
            second.regenerateCargo(secondRandom);
        }

        assertEquals(first.getStorage(), second.getStorage(),
                "same seed ⇒ same consumption sequence");
    }

    private static Station consumer(int storage) {
        Station station = new Station(1);
        station.setRole(CargoTypes.StationRole.CONSUMER);
        station.setCargoType(CargoTypes.COAL);
        station.setStorage(storage);
        return station;
    }

    /** A Random whose {@code nextDouble()} is fixed, so the branch is fully deterministic. */
    private static Random constant(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
