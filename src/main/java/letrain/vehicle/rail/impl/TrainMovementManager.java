package letrain.vehicle.rail.impl;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Destructible;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;

/**
 * Extracted from Train.java (~247 lines) to keep the train class focused.
 * Handles the two-pass linker movement logic, collision detection (train-to-train
 * and dead-end), and crash handling.
 */

/**
 * Handles the two-pass linker movement logic, collision detection
 * (train-to-train and dead-end), and crash handling — extracted from
 * {@link Train} to keep that class focused.
 *
 * <p>
 * Movement uses a two-pass approach:
 * <ol>
 * <li><b>Validation</b> — check all linkers can move to their target
 * tracks</li>
 * <li><b>Execution</b> — physically move linkers with rollback on failure</li>
 * </ol>
 * After a successful move, an immediate post-move check detects
 * train-to-train collisions and dead-end impacts.
 */
public class TrainMovementManager implements letrain.vehicle.rail.TrainMovementManager {

    private final Train train;

    public TrainMovementManager(Train train) {
        this.train = train;
    }

    public boolean moveLinkers(boolean isNormalSense) {
        Deque<Linker> linkers = train.getLinkers();
        if (linkers.isEmpty()) {
            return false;
        }

        List<Track> targetTracks = new ArrayList<>();
        Map<Linker, Track> currentTracks = new HashMap<>();
        Map<Linker, Dir> entryDirsMap = new HashMap<>();

        List<Linker> movingOrder = new ArrayList<>();
        if (isNormalSense) {
            movingOrder.addAll(linkers);
        } else {
            Iterator<Linker> it = linkers.descendingIterator();
            while (it.hasNext()) {
                movingOrder.add(it.next());
            }
        }

        Linker firstLinker = movingOrder.get(0);
        Linker lastLinker = movingOrder.get(movingOrder.size() - 1);

        // FASE 1A: Validar solo el primer linker (cabeza)
        // La cabeza es la única que puede chocar con otro tren o encontrar
        // un callejón sin salida. Si la cabeza puede moverse, el resto
        // podrá seguirla.
        {
            Linker head = movingOrder.get(0);
            Track headCurrent = head.getTrack();
            if (headCurrent == null) {
                return false;
            }

            Dir headExitDir = head.getDir();
            Track headNextConnectedTrack = headCurrent.getConnected(headExitDir);
            if (headNextConnectedTrack == null) {
                log.debug("Pass 1: nextTrack is null for head linker {}", head);
                return false;
            }

            Linker headOccupant = headNextConnectedTrack.getLinker();
            if (headOccupant != null) {
                int speed = train.getSpeed();
                if (Math.abs(speed) >= Train.CRASH_SPEED_THRESHOLD) {
                    crashDetected(headOccupant, speed);
                } else {
                    contactDetected(headOccupant, speed);
                }
                return false;
            }

            Dir headEntryDir = headExitDir.inverse();
            currentTracks.put(head, headCurrent);
            entryDirsMap.put(head, headEntryDir);
            headNextConnectedTrack.setReservation(head);
            targetTracks.add(headNextConnectedTrack);
        }

        // FASE 1B: Guardar los datos del resto de linkers
        for (int i = 1; i < movingOrder.size(); i++) {
            Linker linkerToMove = movingOrder.get(i);
            Track currentTrack = linkerToMove.getTrack();
            Dir exitDir = linkerToMove.getDir();
            Track nextTrackOfLinker = currentTrack.getConnected(exitDir);
            Dir entryDirOfLinker = exitDir.inverse();

            currentTracks.put(linkerToMove, currentTrack);
            entryDirsMap.put(linkerToMove, entryDirOfLinker);
            nextTrackOfLinker.setReservation(linkerToMove);
            targetTracks.add(nextTrackOfLinker);
        }

        // ── FASE 2A: Mover cabeza (first linker) ─────────────────────
        {
            Track headCurrentTrack = currentTracks.get(firstLinker);
            Track headNextTrack = targetTracks.get(0);
            Dir headEntryDir = entryDirsMap.get(firstLinker);

            firstLinker.setPreviousTrack(headCurrentTrack);
            firstLinker.setPreviousDir(firstLinker.getDir());
            headCurrentTrack.removeLinker();
            headNextTrack.enterLinkerFromDir(headEntryDir, firstLinker);

            firstLinker.setRailsSinceStop(firstLinker.getRailsSinceStop() + 1);

            if (train.getSafetyManager() != null) {
                train.getSafetyManager().onTrackEntered(headNextTrack);
            }

            Sensor enterSensor = headNextTrack.getSensor();
            if (enterSensor != null) {
                enterSensor.onEnterTrain(train);
            }
            if (headNextTrack.getSemaphore() != null) {
                headNextTrack.getSemaphore().onEnterTrain(train);
            }
            if (headNextTrack instanceof ForkRailTrack) {
                ((ForkRailTrack) headNextTrack).onEnterTrain(train);
            }

            headNextTrack.setReservation(null);
        }

        // ── FASE 2B: Mover centro (middle linkers) ────────────────────
        for (int i = 1; i < movingOrder.size() - 1; i++) {
            Linker linkerToMove = movingOrder.get(i);
            Track currentTrack = currentTracks.get(linkerToMove);
            Track nextTrackOfLinker = targetTracks.get(i);
            Dir entryDirOfLinker = entryDirsMap.get(linkerToMove);

            linkerToMove.setPreviousTrack(currentTrack);
            linkerToMove.setPreviousDir(linkerToMove.getDir());
            currentTrack.removeLinker();
            nextTrackOfLinker.enterLinkerFromDir(entryDirOfLinker, linkerToMove);
            linkerToMove.setRailsSinceStop(linkerToMove.getRailsSinceStop() + 1);
            nextTrackOfLinker.setReservation(null);
        }

        // ── FASE 2C: Mover cola (last linker) ────────────────────────
        if (movingOrder.size() > 1) {
            Track lastLinkerTrack = currentTracks.get(lastLinker);
            Track lastLinkerNextTrack = targetTracks.get(movingOrder.size() - 1);
            Dir lastLinkerDir = entryDirsMap.get(lastLinker);

            if (lastLinkerTrack.getSensor() != null) {
                lastLinkerTrack.getSensor().onExitTrain(train);
            }
            if (lastLinkerTrack.getSemaphore() != null) {
                lastLinkerTrack.getSemaphore().onExitTrain(train);
            }
            if (lastLinkerTrack instanceof ForkRailTrack) {
                ((ForkRailTrack) lastLinkerTrack).onExitTrain(train);
            }

            lastLinker.setPreviousTrack(lastLinkerTrack);
            lastLinker.setPreviousDir(lastLinker.getDir());
            lastLinkerTrack.removeLinker();
            lastLinkerNextTrack.enterLinkerFromDir(lastLinkerDir, lastLinker);
            lastLinker.setRailsSinceStop(lastLinker.getRailsSinceStop() + 1);
            lastLinkerNextTrack.setReservation(null);
        }

        return true;
    }

    private void contactDetected(Linker headOccupant, int speed) {
        Point collisionPos = headOccupant.getPosition();
        train.notifyContact(collisionPos, speed);
        Train otherTrain = headOccupant.getTrain();
        if (otherTrain != null) {
            otherTrain.emergencyStop();
        }
    }


    @Override
    public void crashDetected(Linker linker, int speed) {
        Point crashPos = linker.getPosition();

        if (!isAlreadyDestroying(train)) {
            train.notifyCrash(crashPos, speed);
            train.crashDestroy();
        }

        Train otherTrain = linker.getTrain();
        if (otherTrain != null) {
            if (!isAlreadyDestroying(otherTrain)) {
                otherTrain.notifyCrash(crashPos, speed);
                otherTrain.crashDestroy();
            }
        } else {
            log.info("crash: Destroying loose linker {} at crash position {}", linker, crashPos);
            linker.destroy();
        }
    }

    private boolean isAlreadyDestroying(Train t) {
        for (Linker l : t.getLinkers()) {
            if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void correctDirection(Linker linker) {
        if (linker == null)
            return;
        Track t = linker.getTrack();
        Dir d = linker.getDir();
        if (t == null || t.getConnected(d) != null)
            return;
        // Skip the entry direction (where we came from) — pick the exit
        Dir entryDir = linker.getEntryDir();
        for (Dir conn : t.getConnections()) {
            if (conn != entryDir && t.getConnected(conn) != null) {
                linker.setDir(conn);
                return;
            }
        }
        // Fallback: just pick any connected direction
        for (Dir conn : t.getConnections()) {
            if (t.getConnected(conn) != null) {
                linker.setDir(conn);
                return;
            }
        }
    }

    @Override
    public void clearReservations(List<Track> reservedTracks) {
        for (Track t : reservedTracks) {
            t.setReservation(null);
        }
    }

    @Override
    public void forceEmergencyStop() {
        if (train.isAutoMode()) {
            train.setAutoMode(false);
            if (train.getDirectorLinker() != null) {
                train.getDirectorLinker().setTargetSpeed(0);
            }
            if (train.getSafetyManager() != null) {
                train.getSafetyManager().onEmergencyStop();
            }
            Train.log.warn("Train {} deactivated autopilot and stopped due to segment conflict.", train.getId());
        }
    }

    @Override
    public boolean advance() {
        if (train.getLogisticsManager().isLoading()) {
            Train.log.info("Train {} advance: cannot move because train is loading", train.getId());
            return false;
        }

        if (train.isStalled()) {
            Train.log.info("Train {} advance: cannot move because train is stalled", train.getId());
            if (train.getDirectorLinker() != null) {
                train.getDirectorLinker().setTargetSpeed(0);
            }
            return false;
        }

        if (train.getModel() != null) {
            if (!train.getSafetyManager().hasPermissionToMove()) {
                Train.log.info("Train {} advance: cannot move because hasPermissionToMove is false. Forcing setTargetSpeed(0)", train.getId());
                if (train.getDirectorLinker() != null) {
                    train.getDirectorLinker().setTargetSpeed(0);
                }
                return false;
            }
        }

        Train.log.info("Train {} advance: proceeding to moveLinkers", train.getId());

        boolean normalSense = true;
        if (train.getDirectorLinker() != null && train.getDirectorLinker().isReversed()) {
            normalSense = false;
        }

        // Save linker directions before attempting to move.
        // If moveLinkers fails (collision, blocked), we must restore them
        // so the renderer draws wagons at their correct positions.
        Map<Linker, Dir> savedDirs = new HashMap<>();
        Map<Linker, Dir> savedEntryDirs = new HashMap<>();
        for (Linker l : train.getLinkers()) {
            savedDirs.put(l, l.getDir());
            savedEntryDirs.put(l, l.getEntryDir());
        }

        refreshLinkersDirection(normalSense);
        boolean moved = moveLinkers(normalSense);

        if (!moved || train.isStalled()) {
            Linker first = train.getLinkers().isEmpty() ? null : train.getLinkers().getFirst();
            for (Linker l : train.getLinkers()) {
                if (train.isStalled() && l == first)
                    continue; // skip first linker on crash
                Dir savedDir = savedDirs.get(l);
                Dir savedEntry = savedEntryDirs.get(l);
                if (savedDir != null) {
                    l.setDir(savedDir);
                }
                if (savedEntry != null) {
                    l.setEntryDir(savedEntry);
                }
            }
        }

        return moved;
    }

    @Override
    public void refreshLinkersDirection() {
        if (train.getDirectorLinker() == null || ((Locomotive) train.getDirectorLinker()).getTrack() == null) {
            return;
        }
        boolean normalSense = true;
        if (train.getDirectorLinker().isReversed()) {
            normalSense = false;
        }
        refreshLinkersDirection(normalSense);
    }

    private void refreshLinkersDirection(boolean isNormalSense) {
        setDirPushedLinkers(isNormalSense);
        setDirTowedLinkers(isNormalSense);
    }

    private void setDirPushedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (!isNormalSense) {
            iterator = train.getLinkers().iterator();
        } else {
            iterator = train.getLinkers().descendingIterator();
        }

        Tractor tractor = train.getDirectorLinker();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            if (next == tractor) {
                break;
            }
        }
        Dir pushDir = ((Locomotive) tractor).getDir();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track nextTrack = next.getTrack();
            if (pushDir == null) {
                break;
            }
            Dir inverseEntry = pushDir.inverse();
            next.setEntryDir(inverseEntry);
            Dir nextDir = nextTrack.getDir(inverseEntry);
            if (nextDir == null) {
                break;
            }
            next.setDir(nextDir);
            pushDir = next.getDir();
        }
    }

    private void setDirTowedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (isNormalSense) {
            iterator = train.getLinkers().iterator();
        } else {
            iterator = train.getLinkers().descendingIterator();
        }
        Tractor tractor = train.getDirectorLinker();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            if (next == tractor) {
                break;
            }
        }
        Track oldTrack = ((Locomotive) tractor).getTrack();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Dir nextDir = null;
            Track wagonTrack = next.getTrack();
            for (Dir conn : oldTrack.getConnections()) {
                if (oldTrack.getConnected(conn) == wagonTrack) {
                    nextDir = conn.inverse();
                    break;
                }
            }
            if (nextDir == null) {
                break;
            }
            next.setDir(nextDir);
            Dir nextEntry = next.getTrack().getDir(next.getDir());
            if (nextEntry == null) {
                break;
            }
            next.setEntryDir(nextEntry);
            oldTrack = next.getTrack();
        }
    }

    @Override
    public void initiateBraking() {
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            int currentTargetSpeed = head.getTargetSpeed();
            Train.log.info("Train {} initiateBraking: target speed was {}, setting to 0", train.getId(), currentTargetSpeed);
            train.getSafetyManager().onBrakingInitiated(currentTargetSpeed);
            head.setTargetSpeed(0);
        }
    }

    @Override
    public void restoreSpeed(int speed) {
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            Train.log.info("Train {} restoreSpeed: restoring target speed to {}", train.getId(), speed);
            head.setTargetSpeed(speed);
        }
    }
}
