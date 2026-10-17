package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

/**
 * Keyboard track-building state machine ({@link RailTrackMaker}).
 *
 * <p>
 * The regression tests at the bottom pin the delete+rebuild flows of issue #708: a remove must
 * never leave the maker anchored to a piece that can no longer be continued, or every following
 * Shift+Arrow placement is rejected by the 45-degree rule while the cursor stays adjacent — the
 * "stuck cursor" the players hit until they walked away and back.
 */
@DisplayName("RailTrackMaker: build/resume state machine")
public class RailTrackMakerTest {

    @ParameterizedTest(name = "fromInt({0})")
    @CsvSource({"E ", "NE ", "N ", "NW ", "W ", "SW ", "S ", "SE "})
    void testConnectTrack(Dir from) {
        RailTrackMaker maker = new RailTrackMaker(null);
        RailTrack track = new RailTrack();
        ForkRailTrack fork = new ForkRailTrack(1);
        Dir to = from.inverse();
        Dir toTurnedLeft = to.turnLeft();
        track.addRoute(from, to);
        fork.addRoute(from, toTurnedLeft);
        maker.addTrackConnectionsToFork(track, fork);
    }

    // ------------------------------------------------------------------
    // Headless harness: real model on flat GROUND + mocked presenter/view
    // ------------------------------------------------------------------

    private letrain.mvp.impl.Model model;
    private RailTrackMaker maker;

    @BeforeEach
    void setUp() {
        model = new letrain.mvp.impl.Model();
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                model.getGroundMap().setValueAt(new Point(x, y), letrain.ground.GroundMap.GROUND);
            }
        }
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(GameMode.RAILS);
        Presenter presenter = Mockito.mock(Presenter.class);
        Mockito.when(presenter.getModel()).thenReturn(model);
        Mockito.when(presenter.getView()).thenReturn(Mockito.mock(View.class));
        Mockito.when(presenter.getAudioController()).thenReturn(null);
        maker = new RailTrackMaker(presenter);
    }

    /** One full Shift+ArrowUp gesture: press (reset), one build tick, release. */
    private void buildForward() {
        maker.onChar(new InputEvent(KeyType.ArrowUp, null, false, false, true));
        maker.makeTracks();
        maker.onKeyUp(new InputEvent(KeyType.ArrowUp));
    }

    private void moveBackward() {
        maker.onChar(new InputEvent(KeyType.ArrowDown));
        maker.onKeyUp(new InputEvent(KeyType.ArrowDown));
    }

    private void eraseForward() {
        maker.onChar(new InputEvent(KeyType.ArrowUp, null, true, false, false));
        maker.onKeyUp(new InputEvent(KeyType.ArrowUp));
    }

    private void eraseBackward() {
        maker.onChar(new InputEvent(KeyType.ArrowDown, null, true, false, false));
        maker.onKeyUp(new InputEvent(KeyType.ArrowDown));
    }

    private void turnLeft() {
        maker.onChar(new InputEvent(KeyType.ArrowLeft));
    }

    private RailTrack trackAt(int x, int y) {
        return model.getRailMap().getTrackAt(x, y);
    }

    private Point cursor() {
        return model.getCursor().getPosition();
    }

    /** The anchor is either absent or still the piece placed at its position in the map. */
    private boolean anchorIsPlaced() {
        return maker.oldTrack == null || maker.oldTrack.getPosition() == null
                || model.getRailMap().getTrackAt(maker.oldTrack.getPosition()) == maker.oldTrack;
    }

    // ------------------------------------------------------------------
    // Normal chaining must keep working (no over-clearing)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consecutive keyboard builds stay connected")
    void chainedBuild_shouldConnectConsecutivePieces() {
        buildForward();
        buildForward();
        buildForward();

        RailTrack first = trackAt(0, 0);
        RailTrack second = trackAt(1, 0);
        RailTrack third = trackAt(2, 0);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertSame(second, first.getConnected(Dir.E));
        assertSame(first, second.getConnected(Dir.W));
        assertSame(third, second.getConnected(Dir.E));
        assertEquals(new Point(3, 0), cursor());
    }

    @Test
    @DisplayName("an adjacent, legal anchor is kept by reset")
    void reset_shouldKeepAnchor_whenAdjacentAndLegal() {
        buildForward(); // (0,0), cursor (1,0), anchor (0,0) right behind

        maker.reset();

        assertSame(trackAt(0, 0), maker.oldTrack,
                "an adjacent placed piece must still anchor the next build");

        buildForward(); // continues from the kept anchor
        assertSame(trackAt(0, 0), trackAt(1, 0).getConnected(Dir.W));
    }

    // ------------------------------------------------------------------
    // Resume invariants: never anchor on a piece that cannot chain
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reset drops an anchor whose piece was removed externally")
    void reset_shouldDropAnchor_whenPieceWasRemoved() {
        buildForward();
        buildForward(); // (0,0),(1,0), cursor (2,0), anchor (1,0)
        model.removeTrack(new Point(1, 0));

        maker.reset();

        assertNull(maker.oldTrack, "a removed piece must not anchor the next build (#708)");
    }

    @Test
    @DisplayName("reset drops an anchor sitting ahead of the cursor (walking back along the rail)")
    void reset_shouldDropAnchor_whenAnchorIsAhead() {
        buildForward();
        buildForward();
        buildForward(); // (0..2,0), cursor (3,0), anchor (2,0)
        moveBackward();
        moveBackward(); // cursor (1,0), anchor (2,0) ahead

        maker.reset();

        assertNull(maker.oldTrack,
                "a 180-degree resume would fail the 45-degree rule forever (#708)");
    }

    @Test
    @DisplayName("deleting the chaining anchor clears the resume state immediately")
    void removeTrack_shouldClearAnchor_whenAnchorDeleted() {
        buildForward();
        buildForward(); // (0,0),(1,0), cursor (2,0), anchor (1,0)
        moveBackward(); // cursor (1,0)
        eraseForward(); // removes the anchor (1,0), cursor (2,0)

        assertNull(trackAt(1, 0), "the erased piece is gone");
        assertNull(maker.oldTrack, "the removed piece must not remain the resume anchor (#708)");
    }

    @Test
    @DisplayName("deleting a piece does not clear an unrelated, still-placed anchor")
    void removeTrack_shouldKeepAnchor_whenAnotherPieceDeleted() {
        buildForward();
        buildForward();
        buildForward();
        buildForward(); // (0..3,0), cursor (4,0), anchor (3,0)
        moveBackward();
        moveBackward(); // cursor (2,0), anchor (3,0)
        eraseForward(); // removes (2,0), unrelated to the anchor

        assertNull(trackAt(2, 0));
        assertSame(trackAt(3, 0), maker.oldTrack,
                "chaining from a still-placed piece must survive an unrelated delete");
    }

    // ------------------------------------------------------------------
    // Issue #708 regression: delete + rebuild must never lock the cursor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delete a middle piece and rebuild forward: cursor advances again (#708)")
    void deleteMiddleThenRebuildForward_shouldNotBlockCursor() {
        for (int i = 0; i < 4; i++) {
            buildForward(); // (0..3,0), cursor (4,0)
        }
        moveBackward();
        moveBackward();
        moveBackward(); // cursor (1,0)
        eraseForward(); // removes (1,0), cursor follows the rail to (2,0)

        buildForward();
        buildForward();
        buildForward();

        assertEquals(new Point(5, 0), cursor(),
                "the cursor must keep advancing after the delete+rebuild cycle");
        assertNotNull(trackAt(4, 0), "the build must continue the line");
        assertTrue(anchorIsPlaced());
    }

    @Test
    @DisplayName("delete a piece, turn 90 degrees and rebuild: cursor advances (#708)")
    void deleteThenTurnAndRebuild_shouldNotBlockCursor() {
        for (int i = 0; i < 3; i++) {
            buildForward(); // (0..2,0), cursor (3,0)
        }
        moveBackward(); // cursor (2,0)
        eraseForward(); // removes (2,0), cursor (3,0)
        turnLeft();
        turnLeft(); // face N

        buildForward();
        buildForward();

        assertEquals(new Point(3, -2), cursor(),
                "the cursor must be able to rebuild in a fresh direction after a delete");
        assertNotNull(trackAt(3, -1), "a northbound piece must have been laid");
        assertTrue(anchorIsPlaced());
    }

    @Test
    @DisplayName("walking back along the rail and building resumes at the rail end (#708)")
    void walkBackAlongRailThenBuild_shouldNotBlockCursor() {
        for (int i = 0; i < 4; i++) {
            buildForward(); // (0..3,0), cursor (4,0)
        }
        moveBackward();
        moveBackward(); // cursor (2,0), anchor ahead (3,0)

        buildForward(); // follows the existing rail
        buildForward(); // follows the existing rail
        buildForward(); // lays a new piece and advances

        assertEquals(new Point(5, 0), cursor());
        assertNotNull(trackAt(4, 0));
    }

    @Test
    @DisplayName("erase backward then rebuild does not lock the cursor (#708)")
    void eraseBackwardThenRebuild_shouldNotBlockCursor() {
        for (int i = 0; i < 3; i++) {
            buildForward(); // (0..2,0), cursor (3,0)
        }
        eraseBackward(); // removes (2,0), cursor (2,0)

        buildForward();
        buildForward();

        assertNotNull(trackAt(2, 0), "the backward-erased piece can be rebuilt");
        assertEquals(new Point(3, 0), cursor());
        assertTrue(anchorIsPlaced());
    }

    @Test
    @DisplayName("repeated delete/rebuild cycles never leave a dangling anchor (#708)")
    void deleteRebuildCycles_shouldNeverLeaveStaleAnchor() {
        for (int i = 0; i < 4; i++) {
            buildForward(); // (0..3,0), cursor (4,0)
        }
        for (int cycle = 0; cycle < 3; cycle++) {
            moveBackward(); // cursor (3,0)
            eraseForward(); // removes (3,0), cursor (4,0)
            moveBackward(); // cursor (3,0), on the hole
            buildForward(); // rebuilds (3,0), cursor (4,0)

            assertNotNull(trackAt(3, 0), "cycle " + cycle + " must rebuild the piece");
            assertEquals(new Point(4, 0), cursor(), "cycle " + cycle + " cursor position");
            assertTrue(anchorIsPlaced(), "cycle " + cycle + " must not leave a dangling anchor");
        }
    }
}
