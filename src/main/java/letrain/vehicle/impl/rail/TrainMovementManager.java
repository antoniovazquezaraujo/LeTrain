package letrain.vehicle.impl.rail;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.Destructible;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

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
 * <p>Movement uses a two-pass approach:
 * <ol>
 *   <li><b>Validation</b> — check all linkers can move to their target tracks</li>
 *   <li><b>Execution</b> — physically move linkers with rollback on failure</li>
 * </ol>
 * After a successful move, an immediate post-move check detects
 * train-to-train collisions and dead-end impacts.
 */
public class TrainMovementManager {
    private static final Logger log = LoggerFactory.getLogger(TrainMovementManager.class);

    private final Train train;

    public TrainMovementManager(Train train) {
        this.train = train;
    }

    // ── moved from Train.moveLinkers() ──────────────────────────────────

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
                // Auto-correct: the linker's direction doesn't match any connection
                correctDirection(linkerToMove);
                exitDir = linkerToMove.getDir();
                nextTrackOfLinker = currentTrack.getConnected(exitDir);
                if (nextTrackOfLinker == null) {
                    log.debug("Pass 1: nextTrack is null for {}", linkerToMove);
                    clearReservations(targetTracks);
                    return false;
                }
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

            nextTrackOfLinker.setReservation(null);

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
                // In shunting mode, never crash — always treat as contact.
                // Two trains sharing a segment should not destroy each other.
                if (Math.abs(speed) >= Train.CRASH_SPEED_THRESHOLD && !train.isShuntingMode()) {
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
                            ((Locomotive) l).setAcousticSpeedSignal(-1);
                            ((Locomotive) l).setEngineTransitioning(false);
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
                        ((Locomotive) t).setAcousticSpeedSignal(-1);
                        ((Locomotive) t).setEngineTransitioning(false);
                        ((Locomotive) t).setForceIdleSound(true);
                    }
                });
            }
        }

        return true;
    }

    private void correctDirection(Linker linker) {
        if (linker == null) return;
        Track t = linker.getTrack();
        Dir d = linker.getDir();
        if (t == null || t.getConnected(d) != null) return;
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

    // ── moved from Train.clearReservations() ────────────────────────────

    private void clearReservations(List<Track> reservedTracks) {
        for (Track t : reservedTracks) {
            t.setReservation(null);
        }
    }

    // ── moved from Train.crash() ────────────────────────────────────────

    private void crash(Linker linker, int speed) {
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
            }
        } else {
            log.info("crash: Destroying loose linker {} at crash position {}", linker, crashPos);
            linker.destroy();
        }
    }
}
