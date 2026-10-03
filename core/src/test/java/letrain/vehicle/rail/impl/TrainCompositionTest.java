package letrain.vehicle.rail.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.track.CargoTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Train composition description")
class TrainCompositionTest {

    @Test
    @DisplayName("reports the tractive loco color and wagons per cargo type")
    void describeComposition() {
        Train train = new Train(1);
        train.pushBack(new Locomotive(1, "A", "RED"));
        Wagon coal1 = new Wagon("b");
        coal1.setExclusiveCargoType(CargoTypes.COAL);
        Wagon coal2 = new Wagon("c");
        coal2.setExclusiveCargoType(CargoTypes.COAL);
        Wagon gold = new Wagon("d");
        gold.setExclusiveCargoType(CargoTypes.GOLD);
        train.pushBack(coal1);
        train.pushBack(coal2);
        train.pushBack(gold);

        String text = train.describeComposition();

        assertTrue(text.contains("Loco color: RED"), text);
        assertTrue(text.contains("Wagons (3):"), text);
        assertTrue(text.contains("COAL x2"), text);
        assertTrue(text.contains("GOLD x1"), text);
    }

    @Test
    @DisplayName("a train with no wagons reports none")
    void noWagons() {
        Train train = new Train(2);
        train.pushBack(new Locomotive(2, "B"));

        String text = train.describeComposition();

        assertTrue(text.contains("Wagons (0): none"), text);
    }

    @Test
    @DisplayName("falls back to the loaded cargo type when no exclusive type is set")
    void fallsBackToCargoType() {
        Train train = new Train(3);
        train.pushBack(new Locomotive(3, "C", "BLUE"));
        Wagon wagon = new Wagon("e");
        wagon.setCargoType(CargoTypes.RUBY);
        train.pushBack(wagon);

        String text = train.describeComposition();

        assertTrue(text.contains("RUBY x1"), text);
    }
}
