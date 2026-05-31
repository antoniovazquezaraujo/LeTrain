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

    @Override
    public boolean moveLinkers(boolean isNormalSense) {
        Deque<Linker> linkers = train.getLinkers();
        if (linkers.isEmpty()) {
            return false;
        }

        // Pass 1: Verify all linkers can move to their next tracks
        List<Track> targetTracks = new ArrayList<>();
        Map<Linker, Track> currentTracks = new HashMap<>();
        Map<Linker, Dir> entryDirsMap = new HashMap<>();
        Map<Linker, Linker> occupyingLinkerMap = new HashMap<>();

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

        for (Linker linkerToMove : movingOrder) {
            Track currentTrack = linkerToMove.getTrack();
            if (currentTrack == null) {
                return false;
            }

            Dir exitDir = linkerToMove.getDir();
            Track nextTrackOfLinker = currentTrack.getConnected(exitDir);

            if (nextTrackOfLinker == null) {
                log.debug("Pass 1: nextTrack is null for {}", linkerToMove);
                clearReservations(targetTracks);
                return false;
            }

            Linker occupyingL = nextTrackOfLinker.getLinker();
            if (occupyingL != null) {

                if (occupyingL.getTrain() != train) {
                    int speed = train.getSpeed();

                    if (Math.abs(speed) >= Train.CRASH_SPEED_THRESHOLD) {
                        crash(occupyingL, speed);
                    } else {
                        Point collisionPos = occupyingL.getPosition();
                        train.notifyContact(collisionPos, speed);
                        train.getTractors().forEach(t -> {
                            t.setCurrentSpeed(0);
                            t.setTargetSpeed(0);
                        });
                        Train otherTrain = occupyingL.getTrain();
                        if (otherTrain != null) {
                            otherTrain.getTractors().forEach(t -> {
                                t.setCurrentSpeed(0);
                                t.setTargetSpeed(0);
                            });
                        }
                    }
                    clearReservations(targetTracks);
                    return false;
                }
            }

            Dir entryDirOfLinker = linkerToMove.getDir().inverse();
            if (occupyingL == null || occupyingL.getTrain() != train) {
                if (!nextTrackOfLinker.canEnter(entryDirOfLinker, linkerToMove)) {
                    clearReservations(targetTracks);
                    return false;
                }
            }

            currentTracks.put(linkerToMove, currentTrack);
            entryDirsMap.put(linkerToMove, entryDirOfLinker);
            occupyingLinkerMap.put(linkerToMove, occupyingL);

            nextTrackOfLinker.setReservation(linkerToMove);
            targetTracks.add(nextTrackOfLinker);
        }

        // Pass 2: Actually move the linkers
        for (int i = 0; i < movingOrder.size(); i++) {
            Linker linkerToMove = movingOrder.get(i);
            Track currentTrack = currentTracks.get(linkerToMove);
            Track nextTrackOfLinker = targetTracks.get(i);
            Dir entryDirOfLinker = entryDirsMap.get(linkerToMove);

            // Si el linker que sale de la celda es el último del tren disparamos evento
            // onExitTrain
            // a sensores, semáforos y forks
            Sensor sensorExit = currentTrack.getSensor();
            if (sensorExit != null && linkerToMove == lastLinker) {
                sensorExit.onExitTrain(train);
            }
            if (currentTrack.getSemaphore() != null && linkerToMove == lastLinker) {
                currentTrack.getSemaphore().onExitTrain(train);
            }
            if (currentTrack instanceof ForkRailTrack && linkerToMove == lastLinker) {
                ((ForkRailTrack) currentTrack).onExitTrain(train);
            }

            linkerToMove.setPreviousTrack(currentTrack);
            linkerToMove.setPreviousDir(linkerToMove.getDir());
            currentTrack.removeLinker();
            if (nextTrackOfLinker instanceof ForkRailTrack && linkerToMove == firstLinker) {
                train.notifyForkEntry((ForkRailTrack) nextTrackOfLinker);
            }
            if (!nextTrackOfLinker.enterLinkerFromDir(entryDirOfLinker, linkerToMove)) {
                // Rollback: restore linker to its previous track
                linkerToMove.setTrack(currentTrack);
                currentTrack.setLinker(linkerToMove);
                linkerToMove.setPreviousTrack(null);
                linkerToMove.setPreviousDir(null);
                clearReservations(targetTracks);
                return false;
            }
            linkerToMove.setRailsSinceStop(linkerToMove.getRailsSinceStop() + 1);


            // Reactivo: Si la locomotora (firstLinker) cambia de vía, notificamos al gestor
            // de seguridad si entra en un nuevo cantón
            if (linkerToMove == firstLinker && train.getModel() != null) {
                letrain.mvp.Model model = train.getModel();
                letrain.segments.RailwayGraph graph = model.getRailwayGraph();
                if (graph != null && nextTrackOfLinker instanceof RailTrack) {
                    letrain.vehicle.rail.TrainSafetyManager safety = train.getSafetyManager();
                    letrain.segments.Segment newSegment = null;
                    if (nextTrackOfLinker instanceof letrain.track.rail.ForkRailTrack && safety != null && safety.getNextSegment() != null) {
                        newSegment = safety.getNextSegment();
                    } else {
                        newSegment = graph.getSegment((RailTrack) nextTrackOfLinker);
                    }
                    if (newSegment != null && safety != null && !newSegment.equals(safety.getCurrentSegment())) {
                        train.notifySegmentEntered(newSegment);
                    }
                }
            }
            nextTrackOfLinker.setReservation(null);

            // Si el linker que sale de la celda es el primero del tren disparamos evento
            // onEnterTrain
            // a sensores, semáforos y forks

            Sensor sensorEnter = nextTrackOfLinker.getSensor();
            if (sensorEnter != null && linkerToMove == firstLinker) {
                sensorEnter.onEnterTrain(train);
            }
            if (nextTrackOfLinker.getSemaphore() != null && linkerToMove == firstLinker) {
                nextTrackOfLinker.getSemaphore().onEnterTrain(train);
            }
            if (nextTrackOfLinker instanceof ForkRailTrack && linkerToMove == firstLinker) {
                ((ForkRailTrack) nextTrackOfLinker).onEnterTrain(train);
            }
        }

        // Post-move collision / dead-end check
        Track currentFirstTrack = firstLinker.getTrack();
        if (currentFirstTrack == null) {
            log.warn("First linker has no track after move sequence — cannot check next cell for collisions");
            clearReservations(targetTracks);
            return false;
        }
        Track nextAfterMove = currentFirstTrack.getConnected(firstLinker.getDir());
        if (nextAfterMove != null) {
            Linker blockingLinker = nextAfterMove.getLinker();
            if (blockingLinker != null && blockingLinker.getTrain() != train) {
                int speed = train.getSpeed();
                if (Math.abs(speed) >= Train.CRASH_SPEED_THRESHOLD) {
                    crash(blockingLinker, speed);
                    train.setStalled(true);
                } else {
                    Point collisionPos = blockingLinker.getPosition();
                    train.notifyContact(collisionPos, speed);
                    train.getTractors().forEach(t -> {
                        t.setCurrentSpeed(0);
                        t.setTargetSpeed(0);
                        if (t instanceof Locomotive) {
                            ((Locomotive) t).setForceIdleSound(true);
                        }
                    });
                    Train otherTrain = blockingLinker.getTrain();
                    if (otherTrain != null) {
                        otherTrain.getTractors().forEach(t -> {
                            t.setCurrentSpeed(0);
                            t.setTargetSpeed(0);
                        });
                    }
                }
                // After collision, correct direction to match physical track connections
                correctDirection(firstLinker);
            }
        } else {
            int speed = train.getSpeed();
            Point impactPos = firstLinker.getPosition();
            if (Math.abs(speed) >= Train.CRASH_SPEED_THRESHOLD) {
                boolean alreadyDestroying = false;
                for (Linker l : train.getLinkers()) {
                    if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                        alreadyDestroying = true;
                        break;
                    }
                }
                if (!alreadyDestroying) {
                    train.notifyCrash(impactPos, speed);
                    train.getLinkers().forEach(l -> {
                        if (l instanceof Locomotive) {
                            ((Locomotive) l).setCurrentSpeed(0);
                            ((Locomotive) l).setTargetSpeed(0);
                            ((Locomotive) l).setForceIdleSound(true);
                        }
                        l.destroy();
                    });
                    train.setStalled(true);
                }
            } else {
                train.notifyContact(impactPos, speed);
                train.getTractors().forEach(t -> {
                    t.setCurrentSpeed(0);
                    t.setTargetSpeed(0);
                    if (t instanceof Locomotive) {
                        ((Locomotive) t).setForceIdleSound(true);
                    }
                });
            }
        }

        return true;
    }

    @Override
    public void crash(Linker linker, int speed) {
        Point crashPos = linker.getPosition();
        boolean alreadyDestroying = false;
        for (Linker l : train.getLinkers()) {
            if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                alreadyDestroying = true;
                break;
            }
        }

        if (!alreadyDestroying) {
            train.notifyCrash(crashPos, speed);
            train.getLinkers().forEach(l -> {
                if (l instanceof Locomotive) {
                    ((Locomotive) l).setCurrentSpeed(0);
                    ((Locomotive) l).setTargetSpeed(0);
                    ((Locomotive) l).setForceIdleSound(true);
                }
                l.destroy();
            });
            train.setStalled(true);
            // Release segments so other trains can use them
            if (train.getModel() != null) {
                train.getModel().getBlockManager().releaseAll(train);
            }
        }

        // Also handle the other linker/train
        if (linker.getTrain() != null) {
            boolean otherAlreadyDestroying = false;
            for (Linker l : linker.getTrain().getLinkers()) {
                if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                    otherAlreadyDestroying = true;
                    break;
                }
            }
            if (!otherAlreadyDestroying) {
                linker.getTrain().notifyCrash(crashPos, speed);
                linker.getTrain().getLinkers().forEach(l -> {
                    if (l instanceof Locomotive) {
                        ((Locomotive) l).setCurrentSpeed(0);
                        ((Locomotive) l).setTargetSpeed(0);
                        ((Locomotive) l).setForceIdleSound(true);
                    }
                    l.destroy();
                });
                linker.getTrain().setStalled(true);
                if (linker.getTrain().getModel() != null) {
                    linker.getTrain().getModel().getBlockManager().releaseAll(linker.getTrain());
                }
            }
        } else {
            log.info("crash: Destroying loose linker {} at crash position {}", linker, crashPos);
            linker.destroy();
        }
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
        if (train.isLoading()) {
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
            if (!train.hasPermissionToMove()) {
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
            train.onBrakingInitiated(currentTargetSpeed);
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
