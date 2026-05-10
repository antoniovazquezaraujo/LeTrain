package letrain.vehicle.impl.rail;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;

import java.util.*;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainCouplingManager {
    private static final Logger log = LoggerFactory.getLogger(TrainCouplingManager.class);
    private final Train train;
    @JsonProperty("linkersToJoin")
    @JsonDeserialize(as = LinkedList.class)
    protected Deque<Linker> linkersToJoin;
    int numLinkersToRemove = 0;
    int numLinkersToJoin = 0;
    @JsonProperty("linkersToRemove")
    @JsonDeserialize(as = LinkedList.class)
    protected Deque<Linker> linkersToRemove;
    Train.LinkersSense linkerJoinSense;
    Train.LinkersSense linkerDivisionSense;
    boolean joined = false;

    public TrainCouplingManager(Train train) {
        this.train = train;
    }

    public int getNumLinkersToJoin() {
        return numLinkersToJoin;
    }

    public int getNumLinkersToRemove() {
        return numLinkersToRemove;
    }

    @JsonIgnore
    public List<Linker> getSelectedLinkersToJoin() {
        if (linkersToJoin.isEmpty() || numLinkersToJoin == 0)
            return new ArrayList<Linker>();
        // Convert deque to list to slice it
        List<Linker> all = new ArrayList<Linker>(linkersToJoin);
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return all.subList(0, numLinkersToJoin);
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
    public void updateLinkersToJoin(boolean forwardDirection) {
        linkersToJoin.clear();
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
            log.info("[LINK] single-linker forward={} pos={} entryDir={} realDir={} usedEntry={} linkDir={}",
                    forwardDirection, lastLinker.getPosition(), lastLinker.getEntryDir(),
                    lastLinker.getRealDir(), entryDir, dir);
        } else if (train.getLinkers().size() > 1) {
            if (forwardDirection) {
                lastLinker = train.getLinkers().getFirst();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = Train.LinkersSense.FRONT;
                }
            } else {
                lastLinker = train.getLinkers().getLast();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = Train.LinkersSense.BACK;
                }
            }
            log.info("[LINK] multi-linker forward={} lastLinker={} linkDir={}", forwardDirection, lastLinker, dir);
        }
        if (lastLinker != null && lastLinker.getTrack() != null && dir != null) {
            Track nextTrack = lastLinker.getTrack().getConnected(dir);
            log.info("[LINK] nextTrack from dir={} is {}", dir, nextTrack);
            if (nextTrack != null) {
                // We enter nextTrack from direction 'dir'
                // RailIterator expects: current track and entry direction
                RailIterator iterator = new RailIterator(nextTrack, dir);
                Linker nextLinker = iterator.getTrack().getLinker();
                log.info("[LINK] firstLinker={} train={}",
                        nextLinker, nextLinker != null ? nextLinker.getTrain() : null);
                if (nextLinker != null && train != nextLinker.getTrain()) {
                    while (nextLinker != null) {
                        if (nextLinker.getTrain() != train) {
                            linkersToJoin.add(nextLinker);
                            log.info("[LINK]   added {} at {}", nextLinker, nextLinker.getPosition());
                        }
                        boolean advanced = iterator.advance();
                        log.info("[LINK]   advance={} track={}", advanced, advanced ? iterator.getTrack() : null);
                        if (!advanced) {
                            break;
                        }
                        nextLinker = iterator.getTrack().getLinker();
                    }
                }
            }
        }
        numLinkersToJoin = linkersToJoin.size();
    }

    public void joinLinkers() {
        if (!joined) {
            int count = 0;
            boolean linkersActuallyAdded = false;
            Set<Train> affectedOldTrains = new HashSet<Train>();
            for (Linker linkerToJoin : linkersToJoin) {
                if (count >= numLinkersToJoin)
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

            linkersToJoin.clear();
            joined = true;
            if (linkersActuallyAdded) {
                train.refreshLinkersDirection();
                train.setStalled(false);
                train.notifyLink();
            }
        }
    }

    public void prepareLink(boolean forward, int count) {
        updateLinkersToJoin(forward);
        if (count > 0 && count < linkersToJoin.size()) {
            numLinkersToJoin = count;
        } else {
            numLinkersToJoin = linkersToJoin.size();
        }
    }

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
            numLinkersToRemove = 0;
        } else if (count <= 0) {
            numLinkersToRemove = 1;
        } else {
            numLinkersToRemove = Math.min(Math.max(1, count), maxRemovable);
        }

        updateLinkersToRemove();
    }

    public void setFrontDivisionSense() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = Train.LinkersSense.FRONT;
        numLinkersToRemove = train.calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void setBackDivisionSense() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = Train.LinkersSense.BACK;
        numLinkersToRemove = train.calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void resetUnlinkState() {
        if (!train.getLinkers().isEmpty() && train.getLinkers().peekLast() == train.getDirectorLinker()) {
            linkerDivisionSense = Train.LinkersSense.FRONT;
        } else {
            linkerDivisionSense = Train.LinkersSense.BACK;
        }
        numLinkersToRemove = train.calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void resetLinkState() {
        numLinkersToJoin = 0;
        linkersToJoin.clear();
    }

    public void selectNextDivisionLink() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (numLinkersToRemove < train.getLinkers().size() - 1) {
            numLinkersToRemove++;
        }
        updateLinkersToRemove();
    }

    public void selectPrevDivisionLink() {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (numLinkersToRemove > 0) {
            numLinkersToRemove--;
        }
        updateLinkersToRemove();
    }

    void updateLinkersToRemove() {
        linkersToRemove.clear();
        Iterator<Linker> linkerIterator = train.getLinkers().iterator();
        if (linkerDivisionSense == Train.LinkersSense.FRONT) {
            linkerIterator = train.getLinkers().iterator();
        } else {
            linkerIterator = train.getLinkers().descendingIterator();
        }
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerIterator.hasNext()) {
                Linker next = linkerIterator.next();
                if (next != train.getDirectorLinker()) {
                    linkersToRemove.addLast(next);
                } else {
                    // Si nos topamos con el director, no podemos desvincularlo, así que ajustamos
                    // la cuenta al número actual de elementos válidos y paramos.
                    numLinkersToRemove = n;
                    return;
                }
            } else {
                // Si no hay más elementos en el iterador, ajustamos la cuenta al número real
                // encontrado.
                numLinkersToRemove = n;
                break;
            }
        }
    }

    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return;
        }

        int toRemove = linkersToRemove.size();
        if (toRemove > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (TrainEventListener listener : train.getTrainListeners()) {
                newTrain.addTrainEventListener(listener);
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
        linkersToRemove.clear();
        numLinkersToRemove = 0;
        train.refreshLinkersDirection();
        train.setStalled(false);
        train.notifyUnlink();
    }

    public List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            return new ArrayList<Linker>();
        }

        List<Linker> linkersToDestroy = new ArrayList<Linker>();
        if (numLinkersToRemove > 0) {
            Train newTrain = new Train(nextTrainIdSupplier.get());
            newTrain.setModel(train.getModel());
            for (TrainEventListener listener : train.getTrainListeners()) {
                newTrain.addTrainEventListener(listener);
            }

            for (int n = 0; n < numLinkersToRemove; n++) {
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
        linkersToRemove.clear();
        numLinkersToRemove = 0;
        return linkersToDestroy;
    }

    Dir getLinkDir(Linker linker) {
        Dir linkerDir = linker.getDir();
        Linker adjacentLinker = getAdjacentLinker(linker, linkerDir);
        log.info("[GETLINKDIR] linker={} pos={} dir={} adjacent={} adjTrain={}",
                linker, linker.getPosition(), linkerDir, adjacentLinker,
                adjacentLinker != null ? adjacentLinker.getTrain() : null);
        if (adjacentLinker != null && adjacentLinker.getTrain() != train) {
            return linkerDir;
        }
        // If no adjacent linker or it's from our own train, try the opposite
        // direction via the track router. This handles the case where the loco
        // faces backward after reversing, but the wagons are forward.
        if (adjacentLinker == null && linker.getEntryDir() != null) {
            Dir oppositeDir = linker.getTrack().getDir(linker.getEntryDir());
            log.info("[GETLINKDIR] dead-end at {}, trying opposite: entryDir={} oppositeDir={}",
                    linkerDir, linker.getEntryDir(), oppositeDir);
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
            Track currentTrack = linker.getTrack().getConnected(linkerDir);
            Linker nextLinker = adjacentLinker;
            while (currentTrack != null && nextLinker != null && nextLinker.getTrain() == train) {
                // Find the physical connection from currentTrack to nextLinker's track
                Track nextLinkerTrack = nextLinker.getTrack();
                Track nextTrack = null;
                for (Dir conn : currentTrack.getConnections()) {
                    if (currentTrack.getConnected(conn) == nextLinkerTrack) {
                        nextTrack = nextLinkerTrack;
                        break;
                    }
                }
                if (nextTrack == null) break;
                currentTrack = nextTrack;
                nextLinker = currentTrack.getLinker();
            }
            if (nextLinker != null && nextLinker.getTrain() != train) {
                return linkerDir;
            }
        }
        return null;
    }

    Linker getAdjacentLinker(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            return linker.getTrack().getConnected(dir).getLinker();
        }
        return null;
    }
}