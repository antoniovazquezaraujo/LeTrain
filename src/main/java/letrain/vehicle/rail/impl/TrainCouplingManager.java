package letrain.vehicle.rail.impl;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.RailIterator;
import letrain.vehicle.rail.TrainEventListener;

public class TrainCouplingManager implements letrain.vehicle.rail.TrainCouplingManager {
    private final Train train;
    @JsonProperty("linkersToJoin")
    @JsonDeserialize(as = LinkedList.class)
    private Deque<Linker> linkersToJoin;
    private int numLinkersToRemove = 0;
    private int numLinkersToJoin = 0;
    @JsonProperty("linkersToRemove")
    @JsonDeserialize(as = LinkedList.class)
    private Deque<Linker> linkersToRemove;
    Train.LinkersSense linkerJoinSense;
    Train.LinkersSense linkerDivisionSense;
    boolean joined = false;

    public TrainCouplingManager(Train train) {
        this.train = train;
        this.setLinkersToJoin(new LinkedList<>());
        this.setLinkersToRemove(new LinkedList<>());

    }

    @Override
    public int getNumLinkersToJoin() {
        return numLinkersToJoin;
    }

    @Override
    public int getNumLinkersToRemove() {
        return numLinkersToRemove;
    }

    @JsonIgnore
    @Override
    public List<Linker> getSelectedLinkersToJoin() {
        if (getLinkersToJoin().isEmpty() || getNumLinkersToJoin() == 0)
            return new ArrayList<Linker>();
        // Convert deque to list to slice it
        List<Linker> all = new ArrayList<Linker>(getLinkersToJoin());
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return all.subList(0, getNumLinkersToJoin());
    }/*
     * - Vaciamos los linkersToJoin
     * - Si solicitan forwardDirection, lastLinker es getFirst(), si no es
     * getLast(), es decir, que vamos agregar linkers en ese sentido seleccionado.
     * - En dir ponemos la dirección de "salida" del tren, es decir, la que apuntará
     * a despegarse del tren. Pero ahí necesitamos saber si el tren está invertido o
     * no.
     * - Si el tren no está invertido, la dirección de salida del primer linker es
     * la correcta, pero la del último será la inversa de su track.
     * - Si el tren está invertido es lo contrario, la que hay que invertir es la
     * primera.
     */

    @JsonIgnore
    @Override
    public void updateLinkersToJoin(boolean forwardDirection) {
        getLinkersToJoin().clear();
        joined = false;
        Linker lastLinker = null;
        Dir dir = Dir.E;

        if (train.getLinkers().size() == 1) {
            lastLinker = (Linker) train.getDirectorLinker();
            Dir entryDir = lastLinker.getEntryDir();
            if (entryDir == null) {
                entryDir = lastLinker.getRealDir().inverse();
            }
            if (forwardDirection) {
                linkerJoinSense = Train.LinkersSense.FRONT;
                dir = lastLinker.getTrack().getDir(entryDir);
            } else {
                linkerJoinSense = Train.LinkersSense.BACK;
                dir = entryDir;
            }
        } else if (train.getLinkers().size() > 1) {
            if (forwardDirection) {
                lastLinker = train.getLinkers().getFirst();
                linkerJoinSense = Train.LinkersSense.FRONT;
                dir = getLinkDir(lastLinker);
            } else {
                lastLinker = train.getLinkers().getLast();
                linkerJoinSense = Train.LinkersSense.BACK;
                // Find the connection from the last linker that leads away
                // from our own train (empty track or different train).
                Track lastTrack = lastLinker.getTrack();
                dir = null;
                for (Dir conn : lastTrack.getConnections()) {
                    Track connected = lastTrack.getConnected(conn);
                    if (connected == null) continue;
                    Linker l = connected.getLinker();
                    if (l == null || l.getTrain() != train) {
                        dir = conn;
                        break;
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
                            getLinkersToJoin().add(nextLinker);
                        }
                        if (!iterator.advance()) {
                            break;
                        }
                        nextLinker = iterator.getTrack().getLinker();
                    }
                }
            }
        }
        setNumLinkersToJoin(getLinkersToJoin().size());
    }

    @Override
    public void joinLinkers() {
        if (!joined) {
            int count = 0;
            boolean linkersActuallyAdded = false;
            Set<Train> affectedOldTrains = new HashSet<Train>();
            for (Linker linkerToJoin : getLinkersToJoin()) {
                if (count >= getNumLinkersToJoin())
                    break;

                if (linkerJoinSense == Train.LinkersSense.FRONT) {
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

            getLinkersToJoin().clear();
            joined = true;
            if (linkersActuallyAdded) {
                train.movementManager.refreshLinkersDirection();
                train.setStalled(false);
                train.notifyLink();
            }
        }
    }

    @Override
    public void prepareLink(boolean forward, int count) {
        updateLinkersToJoin(forward);
        if (count > 0 && count < getLinkersToJoin().size()) {
            setNumLinkersToJoin(count);
        } else {
            setNumLinkersToJoin(getLinkersToJoin().size());
        }
    }

    @Override
    public void prepareUnlink(boolean forward, int count) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }

        if (forward) {
            linkerDivisionSense = Train.LinkersSense.FRONT;
        } else {
            linkerDivisionSense = Train.LinkersSense.BACK;
        }

        int maxRemovable = Math.max(0, train.getLinkers().size() - 1);
        if (maxRemovable == 0) {
            setNumLinkersToRemove(0);
        } else if (count <= 0) {
            setNumLinkersToRemove(1);
        } else {
            setNumLinkersToRemove(Math.min(Math.max(1, count), maxRemovable));
        }

        updateLinkersToRemove();
    }

    @Override
    public void setFrontDivisionSense() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = Train.LinkersSense.FRONT;
        setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove();
    }

    @Override
    public void setBackDivisionSense() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = Train.LinkersSense.BACK;
        setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove();
    }

    @Override
    public void resetUnlinkState() {
        if (!train.getLinkers().isEmpty() && train.getLinkers().peekLast() == train.getDirectorLinker()) {
            linkerDivisionSense = Train.LinkersSense.FRONT;
        } else {
            linkerDivisionSense = Train.LinkersSense.BACK;
        }
        setNumLinkersToRemove(train.calcInitialUnlinkCount());
        updateLinkersToRemove();
    }

    @Override
    public void resetLinkState() {
        setNumLinkersToJoin(0);
        getLinkersToJoin().clear();
    }

    @Override
    public void selectNextDivisionLink() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (getNumLinkersToRemove() < train.getLinkers().size() - 1) {
            setNumLinkersToRemove(getNumLinkersToRemove() + 1);
        }
        updateLinkersToRemove();
    }

    @Override
    public void selectPrevDivisionLink() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (getNumLinkersToRemove() > 0) {
            setNumLinkersToRemove(getNumLinkersToRemove() - 1);
        }
        updateLinkersToRemove();
    }

    @Override
    public void updateLinkersToRemove() {
        getLinkersToRemove().clear();
        Iterator<Linker> linkerIterator = train.getLinkers().iterator();
        if (linkerDivisionSense == Train.LinkersSense.FRONT) {
            linkerIterator = train.getLinkers().iterator();
        } else {
            linkerIterator = train.getLinkers().descendingIterator();
        }
        for (int n = 0; n < getNumLinkersToRemove(); n++) {
            if (linkerIterator.hasNext()) {
                Linker next = linkerIterator.next();
                if (next != train.getDirectorLinker()) {
                    getLinkersToRemove().addLast(next);
                } else {
                    // Si nos topamos con el director, no podemos desvincularlo, así que ajustamos
                    // la cuenta al número actual de elementos válidos y paramos.
                    setNumLinkersToRemove(n);
                    return;
                }
            } else {
                // Si no hay más elementos en el iterador, ajustamos la cuenta al número real
                // encontrado.
                setNumLinkersToRemove(n);
                break;
            }
        }
    }

    @Override
    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }

        int toRemove = getLinkersToRemove().size();
        if (toRemove > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (TrainEventListener listener : train.getScriptTrainListeners()) {
                newTrain.addScriptTrainEventListener(listener);
            }
            for (TrainEventListener listener : train.getCoreTrainListeners()) {
                newTrain.addCoreTrainEventListener(listener);
            }

            for (int n = 0; n < toRemove; n++) {
                Linker linkerToRemove;
                if (linkerDivisionSense == Train.LinkersSense.BACK) {
                    linkerToRemove = train.getLinkers().removeLast();
                    newTrain.getLinkers().addFirst(linkerToRemove);
                } else {
                    linkerToRemove = train.getLinkers().removeFirst();
                    newTrain.getLinkers().addLast(linkerToRemove);
                }
                linkerToRemove.setTrain(newTrain);
                Train.log.info("Train {}: unlinked {} to new Train {}", train.getId(), linkerToRemove, newTrain.getId());
            }
            newTrain.assignDefaultDirectorLinker();
            newTrain.rebind();
        }

        train.assignDefaultDirectorLinker();
        train.rebind();
        getLinkersToRemove().clear();
        setNumLinkersToRemove(0);
        train.movementManager.refreshLinkersDirection();
        train.setStalled(false);
        train.notifyUnlink();
    }

    @Override
    public List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return new ArrayList<Linker>();
        }

        List<Linker> linkersToDestroy = new ArrayList<Linker>();
        if (getNumLinkersToRemove() > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (TrainEventListener listener : train.getScriptTrainListeners()) {
                newTrain.addScriptTrainEventListener(listener);
            }
            for (TrainEventListener listener : train.getCoreTrainListeners()) {
                newTrain.addCoreTrainEventListener(listener);
            }

            for (int n = 0; n < getNumLinkersToRemove(); n++) {
                Linker linkerToRemove;
                if (linkerDivisionSense == Train.LinkersSense.BACK) {
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
        }

        train.assignDefaultDirectorLinker();
        train.rebind();
        getLinkersToRemove().clear();
        setNumLinkersToRemove(0);
        return linkersToDestroy;
    }

    @Override
    public Dir getLinkDir(Linker linker) {
        Dir linkerDir = linker.getDir();
        Linker adjacentLinker = getAdjacentLinker(linker, linkerDir);
        if (adjacentLinker != null && adjacentLinker.getTrain() != train) {
            return linkerDir;
        }
        // If no adjacent linker or it's from our own train, try the opposite
        // direction via the track router. This handles the case where the loco
        // faces backward after reversing, but the wagons are forward.
        if (adjacentLinker == null && linker.getEntryDir() != null) {
            Dir oppositeDir = linker.getTrack().getDir(linker.getEntryDir());
            if (oppositeDir != null) {
                Linker oppositeLinker = getAdjacentLinker(linker, oppositeDir);
                if (oppositeLinker != null && oppositeLinker.getTrain() != train) {
                    return oppositeDir;
                }
            }
        }
        // Walk through same-train linkers
        // Walk through same-train linkers using track router to find
        // the exit where a different train might be.
        if (adjacentLinker != null && adjacentLinker.getTrain() == train) {
            // Walk through same-train linkers following track connections outward.
            Track currentTrack = linker.getTrack().getConnected(linkerDir);
            Track prevTrack = linker.getTrack(); // skip the track we came from
            Linker nextLinker = adjacentLinker;
            while (currentTrack != null && nextLinker != null && nextLinker.getTrain() == train) {
                // Walk one hop: find a connection that doesn't go back
                Track nextTrack = null;
                for (Dir conn : currentTrack.getConnections()) {
                    Track t = currentTrack.getConnected(conn);
                    if (t != null && t != prevTrack) {
                        nextTrack = t;
                        break;
                    }
                }
                if (nextTrack == null) break;
                prevTrack = currentTrack;
                currentTrack = nextTrack;
                nextLinker = currentTrack.getLinker();
            }
            if (nextLinker != null && nextLinker.getTrain() != train) {
                return linkerDir;
            }
        }
        return null;
    }

    @Override
    public Linker getAdjacentLinker(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            return linker.getTrack().getConnected(dir).getLinker();
        }
        return null;
    }

    @Override
    public void setNumLinkersToRemove(int numLinkersToRemove) {
        this.numLinkersToRemove = numLinkersToRemove;
    }

    @Override
    public void setNumLinkersToJoin(int numLinkersToJoin) {
        this.numLinkersToJoin = numLinkersToJoin;
    }

    @Override
    public Deque<Linker> getLinkersToJoin() {
        return linkersToJoin;
    }

    @Override
    public void setLinkersToJoin(Deque<Linker> linkersToJoin) {
        this.linkersToJoin = linkersToJoin;
    }

    @Override
    public Deque<Linker> getLinkersToRemove() {
        return linkersToRemove;
    }

    @Override
    public void setLinkersToRemove(Deque<Linker> linkersToRemove) {
        this.linkersToRemove = linkersToRemove;
    }
}