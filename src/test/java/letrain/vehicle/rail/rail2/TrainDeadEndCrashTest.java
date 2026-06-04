package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.Track;
import letrain.vehicle.rail.Linker;

@DisplayName("Train.moveLinkers() — Dead-end crash/contact deferred to next tick")
class TrainDeadEndCrashTest {

    // ================================================================
    //  Test 1: High-speed crash into dead-end
    // ================================================================

    @Test
    @DisplayName("should move to last track and defer crash detection to next tick")
    void shouldCrash_When_DeadEndAtHighSpeed() {
        Train train = new Train(1);
        Locomotive loco = mock(Locomotive.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);

        AtomicReference<Linker> linkerOnA = new AtomicReference<>(loco);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(null);
        AtomicReference<Track> locoTrack = new AtomicReference<>(trackA);

        when(loco.getDir()).thenReturn(Dir.E);
        when(loco.getTrain()).thenReturn(train);
        when(loco.getPosition()).thenReturn(new Point(1, 0));
        when(loco.getRailsSinceStop()).thenReturn(0);
        when(loco.getSpeed()).thenReturn(8);
        doNothing().when(loco).destroy();

        when(loco.getTrack()).thenAnswer(inv -> locoTrack.get());
        doAnswer(inv -> { locoTrack.set(inv.getArgument(0)); return null; })
                .when(loco).setTrack(any(Track.class));

        doNothing().when(loco).setPreviousTrack(any(Track.class));
        doNothing().when(loco).setPreviousDir(any(Dir.class));
        doNothing().when(loco).setEntryDir(any(Dir.class));
        doNothing().when(loco).setDir(any(Dir.class));
        doNothing().when(loco).setPosition(any(Point.class));
        doNothing().when(loco).setRailsSinceStop(anyInt());
        doNothing().when(loco).setCurrentSpeed(anyInt());
        doNothing().when(loco).setTargetSpeed(anyInt());

        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));

        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(null);

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
            Linker removed = linkerOnA.get();
            linkerOnA.set(null);
            return removed;
        }).when(trackA).removeLinker();

        doAnswer(inv -> {
            Linker v = inv.getArgument(1);
            locoTrack.set(trackB);
            linkerOnB.set(v);
            return true;
        }).when(trackB).enterLinkerFromDir(any(Dir.class), any(Linker.class));

        train.getLinkers().add(loco);
        train.setDirectorLinker(loco);

        ScriptTrainEventListener listener = mock(ScriptTrainEventListener.class);
        train.addScriptTrainEventListener(listener);

        boolean moved = train.movementManager.moveLinkers(true);

        assertTrue(moved, "moveLinkers should succeed — train moves to last track");
        assertSame(trackB, loco.getTrack(), "Locomotive should have moved to trackB");
        assertFalse(train.isStalled(),
                "Train should not be stalled yet — dead-end detected next tick");
        verify(listener, never()).onCrash(any(Train.class), any(Point.class), anyInt());
        verify(listener, never()).onContact(any(Train.class), any(Point.class), anyInt());
        verify(loco, never()).destroy();
        verify(loco, never()).setForceIdleSound(true);
    }

    // ================================================================
    //  Test 2: Low-speed contact with dead-end
    // ================================================================

    @Test
    @DisplayName("should move to last track and defer contact detection to next tick")
    void shouldContact_When_DeadEndAtLowSpeed() {
        Train train = new Train(1);
        Locomotive loco = mock(Locomotive.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);

        AtomicReference<Linker> linkerOnA = new AtomicReference<>(loco);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(null);
        AtomicReference<Track> locoTrack = new AtomicReference<>(trackA);

        when(loco.getDir()).thenReturn(Dir.E);
        when(loco.getTrain()).thenReturn(train);
        when(loco.getPosition()).thenReturn(new Point(1, 0));
        when(loco.getRailsSinceStop()).thenReturn(0);
        when(loco.getSpeed()).thenReturn(3);

        when(loco.getTrack()).thenAnswer(inv -> locoTrack.get());
        doAnswer(inv -> { locoTrack.set(inv.getArgument(0)); return null; })
                .when(loco).setTrack(any(Track.class));

        doNothing().when(loco).setPreviousTrack(any(Track.class));
        doNothing().when(loco).setPreviousDir(any(Dir.class));
        doNothing().when(loco).setEntryDir(any(Dir.class));
        doNothing().when(loco).setDir(any(Dir.class));
        doNothing().when(loco).setPosition(any(Point.class));
        doNothing().when(loco).setRailsSinceStop(anyInt());
        doNothing().when(loco).setCurrentSpeed(anyInt());
        doNothing().when(loco).setTargetSpeed(anyInt());

        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getPosition()).thenReturn(new Point(1, 0));

        when(trackA.getConnected(Dir.E)).thenReturn(trackB);
        when(trackB.getConnected(Dir.E)).thenReturn(null);

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

        ScriptTrainEventListener listener = mock(ScriptTrainEventListener.class);
        train.addScriptTrainEventListener(listener);

        boolean moved = train.movementManager.moveLinkers(true);

        assertTrue(moved, "moveLinkers should succeed — train moves to last track");
        assertSame(trackB, loco.getTrack(), "Locomotive should have moved to trackB");
        assertFalse(train.isStalled(),
                "Train should not be stalled — contact detected next tick");
        verify(listener, never()).onContact(any(Train.class), any(Point.class), anyInt());
        verify(listener, never()).onCrash(any(Train.class), any(Point.class), anyInt());
        verify(loco, never()).destroy();
        verify(loco, never()).setForceIdleSound(true);
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
        ScriptTrainEventListener listener = mock(ScriptTrainEventListener.class);
        train.addScriptTrainEventListener(listener);

        // --- Act ---
        boolean moved = train.movementManager.moveLinkers(true);

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

    // ================================================================
    //  Test 4: Reverse (marcha atrás) into dead-end → engine goes idle
    // ================================================================

    @Test
    @DisplayName("should move to last track when reversing into a dead-end")
    void shouldContact_When_DeadEndInReverse() {
        Train train = new Train(1);
        Locomotive loco = mock(Locomotive.class);
        Track trackA = mock(Track.class);
        Track trackB = mock(Track.class);

        AtomicReference<Linker> linkerOnA = new AtomicReference<>(null);
        AtomicReference<Linker> linkerOnB = new AtomicReference<>(loco);
        AtomicReference<Track> locoTrack = new AtomicReference<>(trackB);

        when(loco.getDir()).thenReturn(Dir.W);
        when(loco.getTrain()).thenReturn(train);
        when(loco.getPosition()).thenReturn(new Point(0, 0));
        when(loco.getRailsSinceStop()).thenReturn(0);
        when(loco.getSpeed()).thenReturn(3);

        when(loco.getTrack()).thenAnswer(inv -> locoTrack.get());
        doAnswer(inv -> { locoTrack.set(inv.getArgument(0)); return null; })
                .when(loco).setTrack(any(Track.class));

        doNothing().when(loco).setPreviousTrack(any(Track.class));
        doNothing().when(loco).setPreviousDir(any(Dir.class));
        doNothing().when(loco).setEntryDir(any(Dir.class));
        doNothing().when(loco).setDir(any(Dir.class));
        doNothing().when(loco).setPosition(any(Point.class));
        doNothing().when(loco).setRailsSinceStop(anyInt());
        doNothing().when(loco).setCurrentSpeed(anyInt());
        doNothing().when(loco).setTargetSpeed(anyInt());

        when(trackB.getPosition()).thenReturn(new Point(1, 0));
        when(trackA.getPosition()).thenReturn(new Point(0, 0));
        when(trackB.getConnected(Dir.W)).thenReturn(trackA);
        when(trackA.getConnected(Dir.W)).thenReturn(null);

        when(trackB.getSensor()).thenReturn(null);
        when(trackB.getSemaphore()).thenReturn(null);
        when(trackA.getSensor()).thenReturn(null);
        when(trackA.getSemaphore()).thenReturn(null);

        doNothing().when(trackB).setReservation(any(Linker.class));
        doNothing().when(trackA).setReservation(any(Linker.class));
        when(trackB.getReservation()).thenReturn(null);
        when(trackA.getReservation()).thenReturn(null);

        when(trackB.getLinker()).thenAnswer(inv -> linkerOnB.get());
        when(trackA.getLinker()).thenAnswer(inv -> linkerOnA.get());
        when(trackA.canEnter(any(Dir.class), any(Linker.class))).thenReturn(true);

        doAnswer(inv -> {
            linkerOnB.set(null);
            return loco;
        }).when(trackB).removeLinker();

        doAnswer(inv -> {
            Linker v = inv.getArgument(1);
            locoTrack.set(trackA);
            linkerOnA.set(v);
            return true;
        }).when(trackA).enterLinkerFromDir(any(Dir.class), any(Linker.class));

        train.getLinkers().add(loco);
        train.setDirectorLinker(loco);

        ScriptTrainEventListener listener = mock(ScriptTrainEventListener.class);
        train.addScriptTrainEventListener(listener);

        boolean moved = train.movementManager.moveLinkers(false);

        assertTrue(moved, "moveLinkers should succeed — train moves to last track");
        assertSame(trackA, loco.getTrack(), "Locomotive should have moved to trackA");
        assertFalse(train.isStalled(),
                "Train should not be stalled — contact detected next tick");
        verify(listener, never()).onContact(any(Train.class), any(Point.class), anyInt());
        verify(listener, never()).onCrash(any(Train.class), any(Point.class), anyInt());
        verify(loco, never()).destroy();
        verify(loco, never()).setForceIdleSound(true);
    }
}
