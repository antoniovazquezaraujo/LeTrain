package letrain.vehicle.rail.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.RailIterator;
import letrain.vehicle.rail.ScriptTrainEventListener;

public class TrainCouplingManager implements letrain.vehicle.rail.TrainCouplingManager {

    public TrainCouplingManager() {
        // Stateless service constructor
    }

    @Override
    public List<Linker> getSelectedLinkersToJoin(Train train) {
        if (train.getLinkersToJoin().isEmpty() || train.getNumLinkersToJoin() == 0)
            return new ArrayList<Linker>();
        // Convert deque to list to slice it
        List<Linker> all = new ArrayList<Linker>(train.getLinkersToJoin());
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return all.subList(0, train.getNumLinkersToJoin());
    }

    @Override
    public void updateLinkersToJoin(Train train, boolean forwardDirection) {
        train.getLinkersToJoin().clear();
        train.setJoined(false);
        Linker lastLinker = null;
        Dir dir = Dir.E;
        boolean normalSense =
                train.getDirectorLinker() == null || !train.getDirectorLinker().isReversed();

        if (train.getLinkers().size() == 1) {
            lastLinker = (Linker) train.getDirectorLinker();
            Dir entryDir = lastLinker.getEntryDir();
            if (entryDir == null) {
                entryDir = lastLinker.getRealDir().inverse();
            }
            if (forwardDirection) {
                if (normalSense) train.setLinkerJoinSense(Train.LinkersSense.FRONT);
                else train.setLinkerJoinSense(Train.LinkersSense.BACK);
                dir = lastLinker.getDir();
            } else {
                if (normalSense) train.setLinkerJoinSense(Train.LinkersSense.BACK);
                else train.setLinkerJoinSense(Train.LinkersSense.FRONT);
                dir = entryDir;
            }
        } else if (train.getLinkers().size() > 1) {
            if (forwardDirection) {
                if (normalSense) {
                    lastLinker = train.getLinkers().getFirst();
                    train.setLinkerJoinSense(Train.LinkersSense.FRONT);
                } else {
                    lastLinker = train.getLinkers().getLast();
                    train.setLinkerJoinSense(Train.LinkersSense.BACK);
                }

                Dir preferredDir = lastLinker.getDir();
                Track connected = lastLinker.getTrack().getConnected(preferredDir);
                if (connected != null
                        && (connected.getLinker() == null
                                || connected.getLinker().getTrain() != train)) {
                    dir = preferredDir;
                } else {
                    Track lastTrack = lastLinker.getTrack();
                    dir = null;
                    for (Dir conn : lastTrack.getConnections()) {
                        Track t = lastTrack.getConnected(conn);
                        if (t == null) continue;
                        Linker l = t.getLinker();
                        if (l == null || l.getTrain() != train) {
                            dir = conn;
                            break;
                        }
                    }
                }
            } else {
                if (normalSense) {
                    lastLinker = train.getLinkers().getLast();
                    train.setLinkerJoinSense(Train.LinkersSense.BACK);
                } else {
                    lastLinker = train.getLinkers().getFirst();
                    train.setLinkerJoinSense(Train.LinkersSense.FRONT);
                }

                Dir preferredDir = lastLinker.getEntryDir();
                if (preferredDir == null) preferredDir = lastLinker.getRealDir().inverse();
                Track connected = lastLinker.getTrack().getConnected(preferredDir);
                if (connected != null
                        && (connected.getLinker() == null
                                || connected.getLinker().getTrain() != train)) {
                    dir = preferredDir;
                } else {
                    Track lastTrack = lastLinker.getTrack();
                    dir = null;
                    for (Dir conn : lastTrack.getConnections()) {
                        Track t = lastTrack.getConnected(conn);
                        if (t == null) continue;
                        Linker l = t.getLinker();
                        if (l == null || l.getTrain() != train) {
                            dir = conn;
                            break;
                        }
                    }
                }
            }
        }
        if (lastLinker != null && lastLinker.getTrack() != null && dir != null) {
            Track nextTrack = lastLinker.getTrack().getConnected(dir);
            if (nextTrack != null) {
                RailIterator iterator = new RailIterator(nextTrack, dir);
                // Fix initial direction for curves: find actual exit from nextTrack
                // using physical connections, not the router
                Dir entryPort = null;
                for (Dir conn : nextTrack.getConnections()) {
                    if (nextTrack.getConnected(conn) == lastLinker.getTrack()) {
                        entryPort = conn;
                        break;
                    }
                }
                if (entryPort != null) {
                    Dir exitDir = nextTrack.getDir(entryPort);
                    if (exitDir != null) {
                        iterator.setDir(exitDir);
                    }
                }
                Linker nextLinker = iterator.getTrack().getLinker();
                // Skip linkers belonging to our own train
                while (nextLinker != null && nextLinker.getTrain() == train) {
                    if (!iterator.advance()) break;
                    nextLinker = iterator.getTrack().getLinker();
                }
                if (nextLinker != null && train != nextLinker.getTrain()) {
                    while (nextLinker != null) {
                        if (nextLinker.getTrain() != train) {
                            train.getLinkersToJoin().add(nextLinker);
                        }
                        if (!iterator.advance()) {
                            break;
                        }
                        nextLinker = iterator.getTrack().getLinker();
                    }
                }
            }
        }
        train.setNumLinkersToJoin(train.getLinkersToJoin().size());
    }

    @Override
    public boolean hasLinkableVehicles(Train train) {
        return hasLinkable(train, true) || hasLinkable(train, false);
    }

    private boolean hasLinkable(Train train, boolean forwardDirection) {
        Linker lastLinker = null;
        Dir dir = Dir.E;
        boolean normalSense =
                train.getDirectorLinker() == null || !train.getDirectorLinker().isReversed();

        if (train.getLinkers().size() == 1) {
            lastLinker = (Linker) train.getDirectorLinker();
            Dir entryDir = lastLinker.getEntryDir();
            if (entryDir == null) {
                entryDir = lastLinker.getRealDir().inverse();
            }
            if (forwardDirection) {
                dir = lastLinker.getDir();
            } else {
                dir = entryDir;
            }
        } else if (train.getLinkers().size() > 1) {
            if (forwardDirection) {
                if (normalSense) {
                    lastLinker = train.getLinkers().getFirst();
                } else {
                    lastLinker = train.getLinkers().getLast();
                }

                Dir preferredDir = lastLinker.getDir();
                Track connected = lastLinker.getTrack().getConnected(preferredDir);
                if (connected != null
                        && (connected.getLinker() == null
                                || connected.getLinker().getTrain() != train)) {
                    dir = preferredDir;
                } else {
                    Track lastTrack = lastLinker.getTrack();
                    dir = null;
                    for (Dir conn : lastTrack.getConnections()) {
                        Track t = lastTrack.getConnected(conn);
                        if (t == null) continue;
                        Linker l = t.getLinker();
                        if (l == null || l.getTrain() != train) {
                            dir = conn;
                            break;
                        }
                    }
                }
            } else {
                if (normalSense) {
                    lastLinker = train.getLinkers().getLast();
                } else {
                    lastLinker = train.getLinkers().getFirst();
                }

                Dir preferredDir = lastLinker.getEntryDir();
                if (preferredDir == null) preferredDir = lastLinker.getRealDir().inverse();
                Track connected = lastLinker.getTrack().getConnected(preferredDir);
                if (connected != null
                        && (connected.getLinker() == null
                                || connected.getLinker().getTrain() != train)) {
                    dir = preferredDir;
                } else {
                    Track lastTrack = lastLinker.getTrack();
                    dir = null;
                    for (Dir conn : lastTrack.getConnections()) {
                        Track t = lastTrack.getConnected(conn);
                        if (t == null) continue;
                        Linker l = t.getLinker();
                        if (l == null || l.getTrain() != train) {
                            dir = conn;
                            break;
                        }
                    }
                }
            }
        }
        if (lastLinker != null && lastLinker.getTrack() != null && dir != null) {
            Track nextTrack = lastLinker.getTrack().getConnected(dir);
            if (nextTrack != null) {
                RailIterator iterator = new RailIterator(nextTrack, dir);
                Dir entryPort = null;
                for (Dir conn : nextTrack.getConnections()) {
                    if (nextTrack.getConnected(conn) == lastLinker.getTrack()) {
                        entryPort = conn;
                        break;
                    }
                }
                if (entryPort != null) {
                    Dir exitDir = nextTrack.getDir(entryPort);
                    if (exitDir != null) {
                        iterator.setDir(exitDir);
                    }
                }
                Linker nextLinker = iterator.getTrack().getLinker();
                while (nextLinker != null && nextLinker.getTrain() == train) {
                    if (!iterator.advance()) break;
                    nextLinker = iterator.getTrack().getLinker();
                }
                if (nextLinker != null && train != nextLinker.getTrain()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void joinLinkers(Train train) {
        if (!train.isJoined()) {
            int count = 0;
            boolean linkersActuallyAdded = false;
            Set<Train> affectedOldTrains = new HashSet<Train>();

            for (Linker linkerToJoin : train.getLinkersToJoin()) {
                if (count >= train.getNumLinkersToJoin()) break;

                if (train.getLinkerJoinSense() == Train.LinkersSense.FRONT) {
                    train.getLinkers().addFirst(linkerToJoin);
                } else {
                    train.getLinkers().addLast(linkerToJoin);
                }

                Train oldTrain = linkerToJoin.getTrain();
                if (oldTrain != null && oldTrain != train) {
                    oldTrain.getLinkers().remove(linkerToJoin);
                    affectedOldTrains.add(oldTrain);
                }

                linkerToJoin.setTrain(train);
                count++;
                linkersActuallyAdded = true;
            }

            // Cleanup affected old trains
            for (Train oldTrain : affectedOldTrains) {
                oldTrain.assignDefaultDirectorLinker();
                if (oldTrain.getDirectorLinker() == null || oldTrain.getLinkers().isEmpty()) {
                    Train.log.info("Cleaning up dead train {}", oldTrain.getId());
                    // If no locomotives left or no linkers at all, the train is dead
                    oldTrain.getLinkers().forEach(linker -> linker.setTrain(null));
                    oldTrain.getLinkers().clear();
                    if (train.getModel() != null) {
                        train.getModel().getBlockManager().releaseAll(oldTrain);
                    }
                }
            }

            train.getLinkersToJoin().clear();
            train.setJoined(true);
            if (linkersActuallyAdded) {
                train.getMovementManager().refreshLinkersDirection();
                train.setStalled(false);
                train.notifyLink();
            }
        }
    }

    @Override
    public void prepareLink(Train train, boolean forward, int count) {
        updateLinkersToJoin(train, forward);
        if (count > 0 && count < train.getLinkersToJoin().size()) {
            train.setNumLinkersToJoin(count);
        } else {
            train.setNumLinkersToJoin(train.getLinkersToJoin().size());
        }
    }

    @Override
    public void prepareUnlink(Train train, boolean forward, int count) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }

        boolean normalSense =
                train.getDirectorLinker() == null || !train.getDirectorLinker().isReversed();

        if (forward) {
            if (normalSense) train.setLinkerDivisionSense(Train.LinkersSense.FRONT);
            else train.setLinkerDivisionSense(Train.LinkersSense.BACK);
        } else {
            if (normalSense) train.setLinkerDivisionSense(Train.LinkersSense.BACK);
            else train.setLinkerDivisionSense(Train.LinkersSense.FRONT);
        }

        int maxRemovable = Math.max(0, train.getLinkers().size() - 1);
        if (maxRemovable == 0) {
            train.setNumLinkersToRemove(0);
        } else if (count <= 0) {
            train.setNumLinkersToRemove(1);
        } else {
            train.setNumLinkersToRemove(Math.min(Math.max(1, count), maxRemovable));
        }

        updateLinkersToRemove(train);
    }

    @Override
    public void setFrontDivisionSense(Train train) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        train.setLinkerDivisionSense(Train.LinkersSense.FRONT);
        train.setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove(train);
    }

    @Override
    public void setBackDivisionSense(Train train) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        train.setLinkerDivisionSense(Train.LinkersSense.BACK);
        train.setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove(train);
    }

    @Override
    public void resetUnlinkState(Train train) {
        if (!train.getLinkers().isEmpty()
                && train.getLinkers().peekLast() == train.getDirectorLinker()) {
            train.setLinkerDivisionSense(Train.LinkersSense.FRONT);
        } else {
            train.setLinkerDivisionSense(Train.LinkersSense.BACK);
        }
        train.setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove(train);
    }

    @Override
    public void resetLinkState(Train train) {
        train.setNumLinkersToJoin(0);
        train.getLinkersToJoin().clear();
    }

    @Override
    public void selectNextDivisionLink(Train train) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (train.getNumLinkersToRemove() < train.getLinkers().size() - 1) {
            train.setNumLinkersToRemove(train.getNumLinkersToRemove() + 1);
        }
        updateLinkersToRemove(train);
    }

    @Override
    public void selectPrevDivisionLink(Train train) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (train.getNumLinkersToRemove() > 0) {
            train.setNumLinkersToRemove(train.getNumLinkersToRemove() - 1);
        }
        updateLinkersToRemove(train);
    }

    @Override
    public void updateLinkersToRemove(Train train) {
        train.getLinkersToRemove().clear();
        Iterator<Linker> linkerIterator = train.getLinkers().iterator();
        if (train.getLinkerDivisionSense() == Train.LinkersSense.FRONT) {
            linkerIterator = train.getLinkers().iterator();
        } else {
            linkerIterator = train.getLinkers().descendingIterator();
        }
        for (int n = 0; n < train.getNumLinkersToRemove(); n++) {
            if (linkerIterator.hasNext()) {
                Linker next = linkerIterator.next();
                if (next != train.getDirectorLinker()) {
                    train.getLinkersToRemove().addLast(next);
                } else {
                    // Si nos topamos con el director, no podemos desvincularlo, así que ajustamos
                    // la cuenta al número actual de elementos válidos y paramos.
                    train.setNumLinkersToRemove(n);
                    return;
                }
            } else {
                // Si no hay más elementos en el iterador, ajustamos la cuenta al número real
                // encontrado.
                train.setNumLinkersToRemove(n);
                break;
            }
        }
    }

    @Override
    public void divideTrain(Train train, Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }

        int toRemove = train.getLinkersToRemove().size();
        if (toRemove > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (ScriptTrainEventListener listener : train.getScriptTrainListeners()) {
                newTrain.addScriptTrainEventListener(listener);
            }
            for (CoreTrainEventListener listener : train.getCoreTrainListeners()) {
                newTrain.addCoreTrainEventListener(listener);
            }

            for (int n = 0; n < toRemove; n++) {
                Linker linkerToRemove;
                if (train.getLinkerDivisionSense() == Train.LinkersSense.BACK) {
                    linkerToRemove = train.getLinkers().removeLast();
                    newTrain.getLinkers().addFirst(linkerToRemove);
                } else {
                    linkerToRemove = train.getLinkers().removeFirst();
                    newTrain.getLinkers().addLast(linkerToRemove);
                }
                linkerToRemove.setTrain(newTrain);
                Train.log.info(
                        "Train {}: unlinked {} to new Train {}",
                        train.getId(),
                        linkerToRemove,
                        newTrain.getId());
            }
            newTrain.assignDefaultDirectorLinker();
            newTrain.rebind();
            newTrain.getMovementManager().refreshLinkersDirection();
        }

        train.assignDefaultDirectorLinker();
        train.rebind();
        train.getLinkersToRemove().clear();
        train.setNumLinkersToRemove(0);
        train.getMovementManager().refreshLinkersDirection();
        train.setStalled(false);
        train.notifyUnlink();
    }

    @Override
    public List<Linker> destroyLinkers(Train train, Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return new ArrayList<Linker>();
        }

        List<Linker> linkersToDestroy = new ArrayList<Linker>();
        if (train.getNumLinkersToRemove() > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (ScriptTrainEventListener listener : train.getScriptTrainListeners()) {
                newTrain.addScriptTrainEventListener(listener);
            }
            for (CoreTrainEventListener listener : train.getCoreTrainListeners()) {
                newTrain.addCoreTrainEventListener(listener);
            }

            for (int n = 0; n < train.getNumLinkersToRemove(); n++) {
                Linker linkerToRemove;
                if (train.getLinkerDivisionSense() == Train.LinkersSense.BACK) {
                    linkerToRemove = train.getLinkers().removeLast();
                    newTrain.getLinkers().addFirst(linkerToRemove);
                } else {
                    linkerToRemove = train.getLinkers().removeFirst();
                    newTrain.getLinkers().addLast(linkerToRemove);
                }
                linkerToRemove.setTrain(newTrain);
                linkersToDestroy.add(linkerToRemove);
            }
            newTrain.assignDefaultDirectorLinker();
            newTrain.rebind();
            newTrain.getMovementManager().refreshLinkersDirection();
        }

        train.assignDefaultDirectorLinker();
        train.rebind();
        train.getMovementManager().refreshLinkersDirection();
        train.getLinkersToRemove().clear();
        train.setNumLinkersToRemove(0);
        return linkersToDestroy;
    }

    @Override
    public Linker getAdjacentLinker(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            return linker.getTrack().getConnected(dir).getLinker();
        }
        return null;
    }
}
