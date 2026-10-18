package letrain.vehicle.rail.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * White headlights follow the <em>physical</em> front (#618). These tests pin the rule on
 * {@link Train#getHeadlightLocomotive()} without any renderer: the physical front is derived from
 * the director linker's reversed sense, so a consist can be "reversed" by flipping it directly.
 */
@DisplayName("Headlights follow the physical front (#618)")
class HeadlightLocomotiveTest {

    @Test
    @DisplayName("a lone locomotive is the headlight and stays so while pushing")
    void givenSingleLocomotivePushing_whenQueried_thenItIsTheHeadlight() {
        // Arrange: the loco is at the back, the wagon at the physical front.
        Train train = new Train(1);
        Locomotive loco = new Locomotive(1, "A");
        train.pushBack(new Wagon("w"));
        train.pushBack(loco);
        train.setDirectorLinker(loco);

        // Act
        Locomotive headlight = train.getHeadlightLocomotive();

        // Assert: a single loco keeps its lamps on, even pointing towards the wagons.
        assertSame(loco, headlight);
    }

    @Test
    @DisplayName("a single locomotive keeps the headlights when the train is reversed")
    void givenSingleLocomotive_whenReversed_thenStillTheHeadlight() {
        Train train = new Train(2);
        Locomotive loco = new Locomotive(2, "A");
        train.pushBack(loco);
        train.pushBack(new Wagon("w"));
        train.setDirectorLinker(loco);

        loco.setReversed(true);

        assertSame(loco, train.getHeadlightLocomotive());
    }

    @Test
    @DisplayName("push-pull: the headlight follows the physical front and swaps on reversal")
    void givenPushPull_whenReversed_thenHeadlightSwapsEnds() {
        // Arrange: a loco at each end, director is L1 (front) in normal sense.
        Train train = new Train(3);
        Locomotive frontLoco = new Locomotive(3, "A");
        Locomotive rearLoco = new Locomotive(4, "B");
        train.pushBack(frontLoco);
        train.pushBack(new Wagon("w"));
        train.pushBack(rearLoco);
        train.setDirectorLinker(frontLoco);

        // Normal sense: physical front is the front loco.
        assertSame(frontLoco, train.getHeadlightLocomotive());

        // Act: reverse the director sense (the physical front becomes the back end).
        frontLoco.setReversed(true);

        // Assert: the new head turns on, the old one turns off.
        assertSame(rearLoco, train.getHeadlightLocomotive());
    }

    @Test
    @DisplayName("several locomotives with a wagon at the physical front: no headlight")
    void givenSeveralLocomotivesAndWagonFront_whenQueried_thenNoHeadlight() {
        Train train = new Train(4);
        Wagon leadingWagon = new Wagon("w");
        Locomotive first = new Locomotive(5, "A");
        Locomotive second = new Locomotive(6, "B");
        train.pushBack(leadingWagon);
        train.pushBack(first);
        train.pushBack(second);
        train.setDirectorLinker(first);

        // Normal sense: the front really is the wagon, so the front stays dark.
        assertNull(train.getHeadlightLocomotive());

        // When reversed, the back loco now leads and must show the headlights.
        first.setReversed(true);
        assertSame(second, train.getHeadlightLocomotive());
    }

    @Test
    @DisplayName("an empty train has no headlight")
    void givenEmptyTrain_whenQueried_thenNoHeadlight() {
        assertNull(new Train(5).getHeadlightLocomotive());
    }
}
