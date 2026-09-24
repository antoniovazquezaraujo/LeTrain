package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.itinerary.Itinerary;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression: {@link PlayerCommandExecutor#execute(String, Model)} appends a {@code ;} when the
 * typed text does not end with one, so block-terminated statements ({@code create itinerary},
 * {@code trigger commandBlock}) must accept an <b>optional</b> trailing semicolon or the console
 * can never create an itinerary (bug: "Syntax Error at ...: extraneous input ';'").
 */
@DisplayName("Block-terminated statements accept an optional trailing ';'")
class BlockTrailingSemicolonTest {

    private static final String ITINERARY_BLOCK = """
            create itinerary "x" {
                add station 1 arrival 08:00, departure 08:05
                add station 2
            }""";

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station one = new Station(1);
        one.setName("A");
        model.addStation(one);
        Station two = new Station(2);
        two.setName("B");
        model.addStation(two);
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

    /** Runs console text exactly as the console funnels it (the executor may append ';'). */
    private String run(String text) {
        return PlayerCommandExecutor.execute(text, model);
    }

    private Itinerary assignedItinerary() {
        Train train = model.getTrainFromLocomotiveId(1);
        assertNotNull(train, "train 1 must exist");
        return train.getAutopilot().itinerary().orElseThrow();
    }

    @Test
    @DisplayName("create itinerary as the last console statement (no ';' typed) executes")
    void createItinerary_lastStatementWithoutSemicolon_executes() {
        String error = run(ITINERARY_BLOCK);

        assertNull(error, error);
    }

    @Test
    @DisplayName("create itinerary with an explicit ';' after the closing '}' executes")
    void createItinerary_explicitSemicolonAfterBlock_executes() {
        String error = run(ITINERARY_BLOCK + ";");

        assertNull(error, error);
    }

    @Test
    @DisplayName("a ';' after the block does not break the following console statement")
    void createItinerary_semicolonThenAnotherStatement_executesBoth() {
        String[] timeReport = {null};
        String error = PlayerCommandExecutor.execute(ITINERARY_BLOCK + "; time;", model, null, null,
                null, (title, text) -> timeReport[0] = text, null);

        assertNull(error, error);
        assertNotNull(timeReport[0], "the statement after the block must still execute");
        assertTrue(timeReport[0].contains("08:00"), timeReport[0]);
    }

    @Test
    @DisplayName("a program with ';' after the block is accepted (scenarios/letrain-check)")
    void program_trailingSemicolonAfterBlock_isAccepted() {
        List<String> errors =
                model.setProgram(ITINERARY_BLOCK + ";\nassign itinerary \"x\" to train 1;");

        assertTrue(errors.isEmpty(), "unexpected parser errors: " + errors);
        assertEquals(2, assignedItinerary().waypoints().size());
    }

    @Test
    @DisplayName("a program without ';' after the block keeps working")
    void program_withoutTrailingSemicolon_isAccepted() {
        List<String> errors =
                model.setProgram(ITINERARY_BLOCK + "\nassign itinerary \"x\" to train 1;");

        assertTrue(errors.isEmpty(), "unexpected parser errors: " + errors);
        assertEquals(2, assignedItinerary().waypoints().size());
    }

    @Test
    @DisplayName("a stray ';' is still rejected")
    void straySemicolon_isStillRejected() {
        assertNotNull(run(";"), "a lone ';' must still be a syntax error");
        assertNotNull(run("time;;"), "a doubled ';' must still be a syntax error");
    }

    @Test
    @DisplayName("a trigger block as the last console statement (no ';' typed) registers and fires")
    void triggerBlock_lastStatementWithoutSemicolon_registersAndFires() {
        String error = run("sensor 1 on train enter { train set speed 5; }");

        assertNull(error, error);
        Train train = model.getTrainFromLocomotiveId(1);
        model.getSensor(1).onEnterTrain(train);
        assertEquals(5, train.getDirectorLinker().getTargetSpeed(),
                "the trigger block must have been registered and executed");
    }

    @Test
    @DisplayName("a trigger block with an explicit ';' after the closing '}' registers and fires")
    void triggerBlock_explicitSemicolonAfterBlock_registersAndFires() {
        String error = run("sensor 1 on train enter { train set speed 5; };");

        assertNull(error, error);
        Train train = model.getTrainFromLocomotiveId(1);
        model.getSensor(1).onEnterTrain(train);
        assertEquals(5, train.getDirectorLinker().getTargetSpeed(),
                "the trigger block must have been registered and executed");
    }
}
