package letrain.vehicle.rail.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Head locomotive role (headlights only on the head tractor)")
class LocomotiveHeadRoleTest {

    @Test
    @DisplayName("a lone locomotive is its own head")
    void loneLocomotive_isHead() {
        assertTrue(new Locomotive(1, "A").isHeadLocomotive());
    }

    @Test
    @DisplayName("only the train director is the head; the rest of the consist is not")
    void onlyDirectorIsHead() {
        Train train = new Train(1);
        Locomotive head = new Locomotive(1, "A");
        Locomotive second = new Locomotive(2, "B");
        Locomotive third = new Locomotive(3, "C");
        train.pushBack(head);
        train.pushBack(second);
        train.pushBack(third);
        train.setDirectorLinker(head);

        assertTrue(head.isHeadLocomotive());
        assertFalse(second.isHeadLocomotive());
        assertFalse(third.isHeadLocomotive());
    }
}
