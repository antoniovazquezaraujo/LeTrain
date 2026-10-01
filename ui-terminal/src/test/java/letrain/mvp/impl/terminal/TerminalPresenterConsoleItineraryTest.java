package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import letrain.audio.AudioController;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #632, end to end through the real console funnel: an itinerary created in one typed line
 * can be assigned in the next one (or in the same line) because every statement of a session shares
 * the {@code CommandManager} bound to the live world.
 */
@DisplayName("2D console: itineraries survive across typed statements (#632)")
class TerminalPresenterConsoleItineraryTest {

    private static final String CREATE = "create itinerary \"c\" { add station 1; add station 2; }";
    private static final String ASSIGN = "assign itinerary \"c\" to train 1;";

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    /** Types one whole console line, exactly as the player would. */
    private static void console(TerminalPresenter presenter, String cmd) {
        presenter.onChar(charKey(':'));
        for (int i = 0; i < cmd.length(); i++) {
            presenter.onChar(charKey(cmd.charAt(i)));
        }
        presenter.onChar(new InputEvent(KeyType.Enter));
    }

    /**
     * A presenter over the mocked view, with no real audio line (same harness as the other tests).
     */
    private static TerminalPresenter presenter(Model model) {
        TerminalPresenter presenter = new TerminalPresenter(model, mock(TerminalView.class));
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    private static Model worldWithTrain() {
        Model model = new Model(1);
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
        return model;
    }

    private static void assertAssigned(Model model) {
        Train train = model.getTrainFromLocomotiveId(1);
        assertTrue(train.getAutopilot().itinerary().isPresent(),
                "the assign must find the itinerary created in another typed statement");
        assertEquals(2, train.getAutopilot().itinerary().orElseThrow().waypoints().size());
    }

    @Test
    @DisplayName("create in one typed line, assign in the next one")
    void createThenAssign_inTwoConsoleLines() {
        Model model = worldWithTrain();
        TerminalPresenter presenter = presenter(model);

        console(presenter, CREATE);
        console(presenter, ASSIGN);

        assertAssigned(model);
    }

    @Test
    @DisplayName("create and assign in a single typed line")
    void createAndAssign_inOneConsoleLine() {
        Model model = worldWithTrain();
        TerminalPresenter presenter = presenter(model);

        console(presenter, CREATE + " " + ASSIGN);

        assertAssigned(model);
    }
}
