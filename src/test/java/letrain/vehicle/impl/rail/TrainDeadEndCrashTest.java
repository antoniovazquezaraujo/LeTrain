package letrain.vehicle.impl.rail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import letrain.vehicle.Destructible;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

@DisplayName("Train.moveLinkers() — Dead-end crash/contact tests")
class TrainDeadEndCrashTest {

    // ================================================================
    //  Test 1: High-speed crash into dead-end
    // ================================================================

    @Test
    @DisplayName("should crash when reaching a dead-end at high speed (|speed| >= 5)")
    void shouldCrash_When_DeadEndAtHighSpeed() {
        // --- Arrange ---
        Train train = new Train(1);
        Locomotive loco = mock(Locomotive.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);

        // Track references to simulate real getLinker/setLinker side effects
        AtomicReference<Linker> linkerOnA = new AtomicReference<>(loco);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(null);
        AtomicReference<Track> locoTrack = new AtomicReference<>(trackA);

        // --- Linker (Locomotive) stubs ---
        when(loco.getDir()).thenReturn(Dir.E);
        when(loco.getTrain()).thenReturn(train);
        when(loco.getPosition()).thenReturn(new Point(1, 0));
        when(loco.getRailsSinceStop()).thenReturn(0);
        when(loco.getSpeed()).thenReturn(8); // high speed ≥ 5
        // isDestroying defaults to false (Mockito default for boolean)
        doNothing().when(loco).destroy(); // no-op, but verifiable

        // getTrack / setTrack correlation
        when(loco.getTrack()).thenAnswer(inv -> locoTrack.get());
        doAnswer(inv -> { locoTrack.set(inv.getArgument(0)); return null; })
                .when(loco).setTrack(any(Track.class));

        // Other linker setters
        doNothing().when(loco).setPreviousTrack(any(Track.class));
        doNothing().when(loco).setPreviousDir(any(Dir.class));
        doNothing().when(loco).setEntryDir(any(Dir.class));
        doNothing().when(loco).setDir(any(Dir.class));
        doNothing().when(loco).setPosition(any(Point.class));
        doNothing().when(loco).setRailsSinceStop(anyInt());
        doNothing().when(loco).setCurrentSpeed(anyInt());
        doNothing().when(loco).setTargetSpeed(anyInt());

        // --- Track stubs ---
        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));

        // Connection: trackA → trackB, but trackB is dead-end
        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(null);

        // Sensors/semaphores: none
        when(trackA.getSensor()).thenReturn(null);
        when(trackA.getSemaphore()).thenReturn(null);
        when(trackB.getSensor()).thenReturn(null);
        when(trackB.getSemaphore()).thenReturn(null);

        // Reservations
        doNothing().when(trackA).setReservation(any(Linker.class));
        doNothing().when(trackB).setReservation(any(Linker.class));
        when(trackA.getReservation()).thenReturn(null);
        when(trackB.getReservation()).thenReturn(null);

        // Pass 1: trackB is empty, canEnter returns true
        when(trackA.getLinker()).thenAnswer(inv -> linkerOnA.get());
        when(trackB.getLinker()).thenAnswer(inv -> linkerOnB.get());
        when(trackB.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);

        // Pass 2: removeLinker from trackA
        doAnswer(inv -> {
            Linker removed = linkerOnA.get();
            linkerOnA.set(null);
            return removed;
        }).when(trackA).removeLinker();

        // Pass 2: enterLinkerFromDir on trackB (simulates real behavior)
        doAnswer(inv -> {
            Linker v = inv.getArgument(1);
            locoTrack.set(trackB);
            linkerOnB.set(v);
            return true;
        }).when(trackB).enterLinkerFromDir(any(Dir.class), any(Linker.class));

        // Add loco to train and set as director
        train.getLinkers().add(loco);
        train.setDirectorLinker(loco);

        // Add mock listener to verify notifyCrash
        TrainEventListener listener = mock(TrainEventListener.class);
        train.addTrainEventListener(listener);

        // --- Act ---
        train.moveLinkers(true);

        // --- Assert ---
        // 1. notifyCrash was invoked (via listener.onCrash)
        verify(listener).onCrash(eq(train), any(Point.class), eq(8));
        // 2. loco.destroy() was called
        verify(loco).destroy();
        // 3. Train is stalled
        assertTrue(train.isStalled(),
                "Train should be stalled after high-speed dead-end crash");
        // 4. notifyContact should NOT be called
        verify(listener, never()).onContact(any(Train.class), any(Point.class), anyInt());
    }

    // ================================================================
    //  Test 2: Low-speed contact with dead-end
    // ================================================================

    @Test
    @DisplayName("should contact when reaching a dead-end at low speed (|speed| < 5)")
    void shouldContact_When_DeadEndAtLowSpeed() {
        // --- Arrange ---
        Train train = new Train(1);
        Locomotive loco = mock(Locomotive.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);

        AtomicReference<Linker> linkerOnA = new AtomicReference<>(loco);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(null);
        AtomicReference<Track> locoTrack = new AtomicReference<>(trackA);

        // --- Linker (Locomotive) stubs ---
        when(loco.getDir()).thenReturn(Dir.E);
        when(loco.getTrain()).thenReturn(train);
        when(loco.getPosition()).thenReturn(new Point(1, 0));
        when(loco.getRailsSinceStop()).thenReturn(0);
        when(loco.getSpeed()).thenReturn(3); // low speed < 5

        // getTrack / setTrack
        when(loco.getTrack()).thenAnswer(inv -> locoTrack.get());
        doAnswer(inv -> { locoTrack.set(inv.getArgument(0)); return null; })
                .when(loco).setTrack(any(Track.class));

        // Other setters
        doNothing().when(loco).setPreviousTrack(any(Track.class));
        doNothing().when(loco).setPreviousDir(any(Dir.class));
        doNothing().when(loco).setEntryDir(any(Dir.class));
        doNothing().when(loco).setDir(any(Dir.class));
        doNothing().when(loco).setPosition(any(Point.class));
        doNothing().when(loco).setRailsSinceStop(anyInt());
        doNothing().when(loco).setCurrentSpeed(anyInt());
        doNothing().when(loco).setTargetSpeed(anyInt());

        // --- Track stubs ---
        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));

        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(null); // dead-end

        when(trackA.getSensor()).thenReturn(null);
        when(trackA.getSemaphore()).thenReturn(null);
        when(trackB.getSensor()).thenReturn(null);
        when(trackB.getSemaphore()).thenReturn(null);

        doNothing().when(trackA).setReservation(any(Linker.class));
        doNothing().when(trackB).setReservation(any(Linker.class));
        when(trackA.getReservation()).thenReturn(null);
        when(trackB.getReservation()).thenReturn(null);

        when(trackA.getLinker()).thenAnswer(inv -> linkerOnA.get());
        when(trackB.getLinker()).thenAnswer(inv -> linkerOnB.get());
        when(trackB.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);

        doAnswer(inv -> {
            linkerOnA.set(null);
            return loco;
        }).when(trackA).removeLinker();

        doAnswer(inv -> {
            Linker v = inv.getArgument(1);
            locoTrack.set(trackB);
            linkerOnB.set(v);
            return true;
        }).when(trackB).enterLinkerFromDir(any(Dir.class), any(Linker.class));

        train.getLinkers().add(loco);
        train.setDirectorLinker(loco);

        // Listener to verify notifyContact (and absence of notifyCrash)
        TrainEventListener listener = mock(TrainEventListener.class);
        train.addTrainEventListener(listener);

        // --- Act ---
        train.moveLinkers(true);

        // --- Assert ---
        // 1. notifyContact was invoked (via listener.onContact)
        verify(listener).onContact(eq(train), any(Point.class), eq(3));
        // 2. Speed was set to 0 on the tractors (called by both notifyContact and the
        //    dead-end handler, consistent with existing train-to-train contact logic)
        verify(loco, org.mockito.Mockito.atLeast(1)).setCurrentSpeed(0);
        verify(loco, org.mockito.Mockito.atLeast(1)).setTargetSpeed(0);
        // 3. Train is stalled
        assertTrue(train.isStalled(),
                "Train should be stalled after low-speed dead-end contact");
        // 4. notifyCrash should NOT be called
        verify(listener, never()).onCrash(any(Train.class), any(Point.class), anyInt());
        // 5. destroy() should NOT be called on linkers
        verify(loco, never()).destroy();
    }

    // ================================================================
    //  Test 3: Normal movement — next track exists, no dead-end
    // ================================================================

    @Test
    @DisplayName("should not trigger crash/contact when next track exists (normal case)")
    void shouldNotTrigger_When_NextTrackExists() {
        // --- Arrange ---
        Train train = new Train(1);
        Linker linker = mock(Linker.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);
        Track trackC = mock(Track.class);

        AtomicReference<Linker> linkerOnA = new AtomicReference<>(linker);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(null);
        AtomicReference<Linker> linkerOnC = new AtomicReference<>(null);
        AtomicReference<Track> linkerTrack = new AtomicReference<>(trackA);

        // --- Linker stubs ---
        when(linker.getDir()).thenReturn(Dir.E);
        when(linker.getTrain()).thenReturn(train);
        when(linker.getPosition()).thenReturn(new Point(1, 0));
        when(linker.getRailsSinceStop()).thenReturn(0);

        when(linker.getTrack()).thenAnswer(inv -> linkerTrack.get());
        doAnswer(inv -> { linkerTrack.set(inv.getArgument(0)); return null; })
                .when(linker).setTrack(any(Track.class));

        doNothing().when(linker).setPreviousTrack(any(Track.class));
        doNothing().when(linker).setPreviousDir(any(Dir.class));
        doNothing().when(linker).setEntryDir(any(Dir.class));
        doNothing().when(linker).setDir(any(Dir.class));
        doNothing().when(linker).setPosition(any(Point.class));
        doNothing().when(linker).setRailsSinceStop(anyInt());

        // --- Track stubs ---
        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));
        when(trackC.getPosition()).thenReturn(new Point(2, 0));

        // Connections: trackA → trackB → trackC (NOT a dead-end)
        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(trackC);

        when(trackA.getSensor()).thenReturn(null);
        when(trackA.getSemaphore()).thenReturn(null);
        when(trackB.getSensor()).thenReturn(null);
        when(trackB.getSemaphore()).thenReturn(null);

        doNothing().when(trackA).setReservation(any(Linker.class));
        doNothing().when(trackB).setReservation(any(Linker.class));
        when(trackA.getReservation()).thenReturn(null);
        when(trackB.getReservation()).thenReturn(null);

        when(trackA.getLinker()).thenAnswer(inv -> linkerOnA.get());
        when(trackB.getLinker()).thenAnswer(inv -> linkerOnB.get());
        when(trackB.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);

        doAnswer(inv -> {
            linkerOnA.set(null);
            return linker;
        }).when(trackA).removeLinker();

        doAnswer(inv -> {
            Linker v = inv.getArgument(1);
            linkerTrack.set(trackB);
            linkerOnB.set(v);
            return true;
        }).when(trackB).enterLinkerFromDir(any(Dir.class), any(Linker.class));

        // trackC has no foreign linker — just return null (same train or empty)
        when(trackC.getLinker()).thenAnswer(inv -> linkerOnC.get());

        train.getLinkers().add(linker);

        // Listener to verify no crash/contact is triggered
        TrainEventListener listener = mock(TrainEventListener.class);
        train.addTrainEventListener(listener);

        // --- Act ---
        boolean moved = train.moveLinkers(true);

        // --- Assert ---
        // 1. moveLinkers returns true (movement succeeded)
        assertTrue(moved, "moveLinkers should return true when next track exists");
        // 2. notifyCrash was NOT invoked by the dead-end code
        verify(listener, never()).onCrash(any(Train.class), any(Point.class), anyInt());
        // 3. notifyContact was NOT invoked by the dead-end code
        verify(listener, never()).onContact(any(Train.class), any(Point.class), anyInt());
        // 4. Train should NOT be stalled (normal movement)
        assertFalse(train.isStalled(),
                "Train should not be stalled after normal movement");
    }
}
