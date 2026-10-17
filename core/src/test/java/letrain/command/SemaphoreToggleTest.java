package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@code semaphore N toggle;} DSL command (issue #736): it flips the light
 * state (open <-> closed), the same action the UI performs with the 'm' key in SEMAPHORES mode, and
 * it never touches the direction (that is what {@code invert} does). The action is exercised from
 * the console and inside a trigger block, with the same warning as the other actions for an unknown
 * id.
 */
@DisplayName("CLI semaphore toggle command")
class SemaphoreToggleTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        model.addSemaphore(new RailSemaphore(1));
    }

    /** Runs console text exactly as the console funnels it (the executor may append ';'). */
    private String run(String text) {
        return PlayerCommandExecutor.execute(text, model);
    }

    private void addTrainAndSensor() {
        Locomotive loco = new Locomotive(1, "A", "RED");
        Train train = new Train(1);
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        Sensor sensor = new Sensor(1);
        sensor.setName("S");
        model.addSensor(sensor);
    }

    @Test
    @DisplayName("semaphore 1 toggle flips the light state on and off")
    void toggle_flipsLightState() {
        // Arrange
        RailSemaphore semaphore = model.getSemaphore(1);
        assertFalse(semaphore.isOpen(), "semaphores start closed");

        // Act
        String error = run("semaphore 1 toggle;");

        // Assert
        assertNull(error, error);
        assertTrue(semaphore.isOpen(), "first toggle opens the light");

        // Act again
        error = run("semaphore 1 toggle;");

        // Assert
        assertNull(error, error);
        assertFalse(semaphore.isOpen(), "second toggle closes it again");
    }

    @Test
    @DisplayName("toggle starts from the current state (open -> closed, closed -> open)")
    void toggle_fromExplicitState() {
        // Arrange: force the light open with the existing command
        run("semaphore 1 set open;");
        assertTrue(model.getSemaphore(1).isOpen());

        // Act
        run("semaphore 1 toggle;");

        // Assert
        assertFalse(model.getSemaphore(1).isOpen(), "an open light must close");
    }

    @Test
    @DisplayName("toggle keeps the semaphore identity and direction (invert flips the direction)")
    void toggle_keepsDirection() {
        // Arrange
        RailSemaphore semaphore = model.getSemaphore(1);
        semaphore.setCreationDir(Dir.E);

        // Act
        run("semaphore 1 toggle;");

        // Assert
        assertSame(semaphore, model.getSemaphore(1), "toggle must keep the same semaphore");
        assertEquals(Dir.E, semaphore.getCreationDir(), "toggle must not touch the direction");
        assertTrue(semaphore.isOpen(), "the light state is the only thing that changed");
    }

    @Test
    @DisplayName("unknown id keeps the current warning behaviour (order ignored)")
    void toggle_unknownId_warns() {
        // Act
        String error = run("semaphore 99 toggle;");

        // Assert: a runtime warning, not a syntax error
        assertNull(error, error);
        assertTrue(model.getCommandNotice().contains("Semaphore 99 not found"),
                "expected the not-found notice, got: " + model.getCommandNotice());
        assertFalse(model.getSemaphore(1).isOpen(), "the existing semaphore is untouched");
    }

    @Test
    @DisplayName("semaphore toggle as a trigger-block action fires with the event")
    void triggerBlockToggle_firesOnSensorEvent() {
        // Arrange
        addTrainAndSensor();
        String error = run("sensor 1 on train enter { semaphore 1 toggle; }");
        assertNull(error, error);
        assertFalse(model.getSemaphore(1).isOpen(), "still closed before the event");
        Train train = model.getTrainFromLocomotiveId(1);

        // Act: the train steps on the sensor
        model.getSensor(1).onEnterTrain(train);

        // Assert
        assertTrue(model.getSemaphore(1).isOpen(), "the trigger must have toggled the light");
    }

    @Test
    @DisplayName("a trigger-block toggle never changes the direction either")
    void triggerBlockToggle_keepsDirection() {
        // Arrange
        addTrainAndSensor();
        RailSemaphore semaphore = model.getSemaphore(1);
        semaphore.setCreationDir(Dir.W);
        run("sensor 1 on train enter { semaphore 1 toggle; }");

        // Act
        model.getSensor(1).onEnterTrain(model.getTrainFromLocomotiveId(1));

        // Assert
        assertTrue(semaphore.isOpen(), "the trigger toggled the light");
        assertEquals(Dir.W, semaphore.getCreationDir(), "the direction must stay untouched");
    }
}
