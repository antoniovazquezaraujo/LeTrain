package letrain.vehicle.impl.rail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;

@DisplayName("Train.moveLinkers() — NPE fix regression tests")
class TrainMoveLinkersTest {

    private Train train;

    // Linkers
    private Linker firstLinker;
    private Linker secondLinker;

    // Tracks
    private Track trackA; // firstLinker's current track
    private Track trackB; // firstLinker's target track / secondLinker's current track
    private Track trackC; // secondLinker's target track

    // Track linker state holders (to simulate real setLinker/getLinker/removeLinker)
    private AtomicReference<Linker> linkerOnTrackA;
    private AtomicReference<Linker> linkerOnTrackB;
    private AtomicReference<Linker> linkerOnTrackC;

    // Linker track state holders (to correlate setTrack/getTrack on mocks)
    private AtomicReference<Track> firstLinkerTrack;
    private AtomicReference<Track> secondLinkerTrack;

    // ---------- Test 1: Null-check defensivo ----------

    /**
     * Configures track mocks so that Pass 1 succeeds, Pass 2 "succeeds"
     * (enterLinkerFromDir returns true) but —by not executing the real
     * TrackDirector logic— the linker's track remains null after removeLinker().
     * The defensive null-check at the post-move check must detect this and
     * return false instead of throwing a NullPointerException.
     */
    @Test
    @DisplayName("should return false when first linker's track is null after move (defensive null-check)")
    void shouldReturnFalse_When_FirstLinkerTrackIsNull_AfterMove() {
        // --- Arrange ---
        setupTwoLinkerScenario();
        setupPass1Success();

        // Pass 2: enterLinkerFromDir returns true but does NOT call
        // vehicle.setTrack() (simulates residual null-track scenario).
        when(trackB.enterLinkerFromDir(any(Dir.class), any(Linker.class))).thenReturn(true);
        when(trackC.enterLinkerFromDir(any(Dir.class), any(Linker.class))).thenReturn(true);

        // removeLinker sets the linker's track to null (real side-effect)
        setupRemoveLinkerSetsTrackToNull(trackA, linkerOnTrackA, firstLinkerTrack);
        setupRemoveLinkerSetsTrackToNull(trackB, linkerOnTrackB, secondLinkerTrack);

        // --- Act ---
        boolean result = train.moveLinkers(true);

        // --- Assert ---
        assertFalse(result,
                "Should return false: null-track detected in post-move check");
        // Verify the null-check path was hit: firstLinker.getTrack() returned null
        // and clearReservations was called on the target tracks.
        // (setReservation(null) is called twice: once at line 738 after "successful" move,
        //  and again at line 791 by clearReservations when the null-track is detected.)
        verify(trackB, atLeastOnce()).setReservation(null);
        verify(trackC, atLeastOnce()).setReservation(null);
    }

    // ---------- Test 2: Rollback cuando enterLinkerFromDir falla ----------

    /**
     * Simulates a scenario where linker1 tries to enter trackB during Pass 2,
     * but trackB is still occupied by linker2 (same train, so Pass 1 allowed
     * it). enterLinkerFromDir returns false → rollback must restore linker1
     * to trackA and the method must return false.
     */
    @Test
    @DisplayName("should rollback and return false when enterLinkerFromDir fails during Pass 2")
    void shouldReturnFalse_When_EnterLinkerFromDirFails_DuringPass2() {
        // --- Arrange ---
        setupTwoLinkerScenario();

        // Pass 1: trackB is occupied by linker2 (same train), so canEnter is skipped
        // (condition at line 688: occupyingL != null && same train → skip canEnter)
        linkerOnTrackB.set(secondLinker);
        when(trackB.getLinker()).thenAnswer(inv -> linkerOnTrackB.get());

        // trackC is empty → canEnter is called
        when(trackC.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);
        when(trackC.getLinker()).thenAnswer(inv -> linkerOnTrackC.get());

        // Pass 2 stubs
        // removeLinker on trackA → sets firstLinker's track to null
        setupRemoveLinkerSetsTrackToNull(trackA, linkerOnTrackA, firstLinkerTrack);

        // removeLinker on trackB → sets secondLinker's track to null
        setupRemoveLinkerSetsTrackToNull(trackB, linkerOnTrackB, secondLinkerTrack);

        // trackB.enterLinkerFromDir returns FALSE (cannot enter because still occupied by linker2)
        when(trackB.enterLinkerFromDir(any(Dir.class), any(Linker.class))).thenReturn(false);

        // trackC.enterLinkerFromDir won't be reached (because we return false before)
        // but stub it anyway for safety
        when(trackC.enterLinkerFromDir(any(Dir.class), any(Linker.class))).thenReturn(true);

        // --- Act ---
        boolean result = train.moveLinkers(true);

        // --- Assert ---
        assertFalse(result,
                "Should return false: Pass 2 enterLinkerFromDir failure triggers rollback");

        // Rollback verifications:
        // 1. First linker's track should have been restored to trackA
        verify(firstLinker).setTrack(trackA);
        // 2. trackA.setLinker should have been called to restore occupancy
        verify(trackA).setLinker(firstLinker);
        // 3. Reservations should be cleared
        verify(trackB).setReservation(null);
        verify(trackC).setReservation(null);
        // 4. Second linker should NOT have been moved (method returned early)
        verify(trackB, never()).removeLinker();
        verify(trackC, never()).enterLinkerFromDir(any(Dir.class), any(Linker.class));
    }

    // ---------- Test 3: Happy path ----------

    /**
     * All linkers move successfully: Pass 1 preconditions are met, all
     * enterLinkerFromDir calls return true and properly set the linkers on
     * their new tracks. The method returns true.
     */
    @Test
    @DisplayName("should move all linkers and return true when all targets are empty")
    void shouldMoveAllLinkers_When_AllTargetsAreEmpty() {
        // --- Arrange ---
        setupTwoLinkerScenario();
        setupPass1Success();

        // Pass 2: set up real-like side effects for removeLinker and enterLinkerFromDir
        setupRealisticRemoveLinker(trackA, linkerOnTrackA, firstLinkerTrack, firstLinker);
        setupRealisticRemoveLinker(trackB, linkerOnTrackB, secondLinkerTrack, secondLinker);
        setupRealisticEnterLinker(trackB, linkerOnTrackB, firstLinkerTrack, firstLinker);
        setupRealisticEnterLinker(trackC, linkerOnTrackC, secondLinkerTrack, secondLinker);

        // --- Act ---
        boolean result = train.moveLinkers(true);

        // --- Assert ---
        assertTrue(result,
                "Should return true: all linkers moved successfully");

        // Verify first linker ended up on trackB
        assertNotNull(firstLinkerTrack.get(),
                "First linker should have a track after move");
        assertTrue(firstLinkerTrack.get() == trackB,
                "First linker should be on trackB");

        // Verify second linker ended up on trackC
        assertNotNull(secondLinkerTrack.get(),
                "Second linker should have a track after move");
        assertTrue(secondLinkerTrack.get() == trackC,
                "Second linker should be on trackC");

        // Verify reservations were cleared after successful move
        verify(trackB).setReservation(null);
        verify(trackC).setReservation(null);
    }

    // ================================================================
    //  Helper methods
    // ================================================================

    /**
     * Creates a Train with 2 linkers on 3 tracks:
     *   linker1 on trackA → (target) trackB
     *   linker2 on trackB → (target) trackC
     *
     * linkers are added in order: firstLinker, secondLinker.
     */
    private void setupTwoLinkerScenario() {
        train = new Train(1);

        // -- Create tracker states --
        firstLinkerTrack = new AtomicReference<>();
        secondLinkerTrack = new AtomicReference<>();

        // -- Create linkers (mocks) --
        firstLinker = mock(Linker.class);
        secondLinker = mock(Linker.class);

        // Correlate setTrack / getTrack for firstLinker
        doAnswer(inv -> {
            firstLinkerTrack.set(inv.getArgument(0));
            return null;
        }).when(firstLinker).setTrack(any(Track.class));
        when(firstLinker.getTrack()).thenAnswer(inv -> firstLinkerTrack.get());

        // Correlate setTrack / getTrack for secondLinker
        doAnswer(inv -> {
            secondLinkerTrack.set(inv.getArgument(0));
            return null;
        }).when(secondLinker).setTrack(any(Track.class));
        when(secondLinker.getTrack()).thenAnswer(inv -> secondLinkerTrack.get());

        // Common linker stubs
        when(firstLinker.getDir()).thenReturn(Dir.E);
        when(firstLinker.getTrain()).thenReturn(train);
        doNothing().when(firstLinker).setPosition(any(Point.class));
        doNothing().when(firstLinker).setEntryDir(any(Dir.class));
        doNothing().when(firstLinker).setDir(any(Dir.class));
        doNothing().when(firstLinker).setPreviousTrack(any(Track.class));
        doNothing().when(firstLinker).setPreviousDir(any(Dir.class));
        doNothing().when(firstLinker).setRailsSinceStop(anyInt());
        when(firstLinker.getRailsSinceStop()).thenReturn(0);

        when(secondLinker.getDir()).thenReturn(Dir.E);
        when(secondLinker.getTrain()).thenReturn(train);
        doNothing().when(secondLinker).setPosition(any(Point.class));
        doNothing().when(secondLinker).setEntryDir(any(Dir.class));
        doNothing().when(secondLinker).setDir(any(Dir.class));
        doNothing().when(secondLinker).setPreviousTrack(any(Track.class));
        doNothing().when(secondLinker).setPreviousDir(any(Dir.class));
        doNothing().when(secondLinker).setRailsSinceStop(anyInt());
        when(secondLinker.getRailsSinceStop()).thenReturn(0);

        // -- Create tracks (mocks) --
        trackA = mock(Track.class);
        trackB = mock(Track.class);
        trackC = mock(Track.class);

        // Track positions (for logging / TrackDirector)
        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));
        when(trackC.getPosition()).thenReturn(new Point(2, 0));

        // Sensors & semaphores: null (no triggers)
        when(trackA.getSensor()).thenReturn(null);
        when(trackA.getSemaphore()).thenReturn(null);
        when(trackB.getSensor()).thenReturn(null);
        when(trackB.getSemaphore()).thenReturn(null);
        when(trackC.getSensor()).thenReturn(null);
        when(trackC.getSemaphore()).thenReturn(null);

        // Track connections: trackA → trackB, trackB → trackC
        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(trackC);

        // Reservation stubs (do nothing by default)
        doNothing().when(trackA).setReservation(any(Linker.class));
        doNothing().when(trackB).setReservation(any(Linker.class));
        doNothing().when(trackC).setReservation(any(Linker.class));
        when(trackA.getReservation()).thenReturn(null);
        when(trackB.getReservation()).thenReturn(null);
        when(trackC.getReservation()).thenReturn(null);

        // Track linker state holders (what getLinker/setLinker use)
        linkerOnTrackA = new AtomicReference<>(firstLinker);
        linkerOnTrackB = new AtomicReference<>(secondLinker);
        linkerOnTrackC = new AtomicReference<>(null);

        when(trackA.getLinker()).thenAnswer(inv -> linkerOnTrackA.get());
        when(trackB.getLinker()).thenAnswer(inv -> linkerOnTrackB.get());
        when(trackC.getLinker()).thenAnswer(inv -> linkerOnTrackC.get());

        doAnswer(inv -> {
            linkerOnTrackA.set(inv.getArgument(0));
            return null;
        }).when(trackA).setLinker(any(Linker.class));
        doAnswer(inv -> {
            linkerOnTrackB.set(inv.getArgument(0));
            return null;
        }).when(trackB).setLinker(any(Linker.class));
        doAnswer(inv -> {
            linkerOnTrackC.set(inv.getArgument(0));
            return null;
        }).when(trackC).setLinker(any(Linker.class));

        // Initial track assignments
        firstLinkerTrack.set(trackA);
        secondLinkerTrack.set(trackB);

        // Add linkers to train (order matters: firstLinker first)
        train.getLinkers().add(firstLinker);
        train.getLinkers().add(secondLinker);
    }

    /**
     * Sets up Pass 1 (preflight) to succeed: canEnter returns true for both
     * target tracks, and target tracks have no foreign occupying linkers.
     */
    private void setupPass1Success() {
        // Both target tracks return no occupying linker (checked at line 662)
        linkerOnTrackB.set(null);
        linkerOnTrackC.set(null);

        // canEnter returns true for both
        when(trackB.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);
        when(trackC.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);
    }

    /**
     * Stubs removeLinker() so that it sets the tracked linker's track to null
     * (simulating the real TrackDirector.removeLinker side-effect) and clears
     * the track's recorded linker.
     */
    private void setupRemoveLinkerSetsTrackToNull(
            Track track,
            AtomicReference<Linker> trackLinkerRef,
            AtomicReference<Track> linkerTrackRef) {

        doAnswer(inv -> {
            Linker removed = trackLinkerRef.get();
            if (removed != null) {
                linkerTrackRef.set(null);
            }
            trackLinkerRef.set(null);
            return removed;
        }).when(track).removeLinker();
    }

    /**
     * Stubs removeLinker() with realistic behavior: sets the specific linker's
     * track to null and clears the track's recorded linker.
     */
    private void setupRealisticRemoveLinker(
            Track track,
            AtomicReference<Linker> trackLinkerRef,
            AtomicReference<Track> linkerTrackRef,
            Linker expectedLinker) {

        doAnswer(inv -> {
            Linker removed = trackLinkerRef.get();
            if (removed != null) {
                linkerTrackRef.set(null);
            }
            trackLinkerRef.set(null);
            return removed;
        }).when(track).removeLinker();
    }

    /**
     * Stubs enterLinkerFromDir() with realistic behavior: sets the linker's
     * track and position, records the linker on the target track.
     */
    private void setupRealisticEnterLinker(
            Track targetTrack,
            AtomicReference<Linker> targetTrackLinkerRef,
            AtomicReference<Track> linkerTrackRef,
            Linker linker) {

        doAnswer(inv -> {
            Dir d = inv.getArgument(0);
            Linker v = inv.getArgument(1);
            linkerTrackRef.set(targetTrack);
            targetTrackLinkerRef.set(v);
            return true;
        }).when(targetTrack).enterLinkerFromDir(any(Dir.class), any(Linker.class));
    }
}
