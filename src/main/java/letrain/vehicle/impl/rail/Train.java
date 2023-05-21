package letrain.vehicle.impl.rail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Transportable {
    Logger log = LoggerFactory.getLogger(Train.class);
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected final Deque<Linker> linkersToJoin;
    int numLinkersToRemove = 0;
    protected final Deque<Linker> linkersToRemove;
    int railPlatformId = 0;
    public boolean isLoading = false;
    int id;

    enum LinkersSense {
        FRONT, BACK
    };

    LinkersSense linkerJoinSense;
    LinkersSense linkerDivisionSense;
    boolean joined = false;
    protected Tractor directorLinker;

    public Train(int id) {
        setId(id);
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
        this.linkersToJoin = new LinkedList<>();
        this.linkersToRemove = new LinkedList<>();
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /***********************************************************
     * Trailer implementation
     **********************************************************/

    @Override
    public Deque<Linker> getLinkers() {
        return linkers;
    }

    @Override
    public Deque<Linker> getLinkersToJoin() {
        return this.linkersToJoin;
    }

    @Override
    public void pushFront(Linker linker) {
        this.linkers.addFirst(linker);
        assignDefaultDirectorLinker();
        linker.setTrain(this);
    }

    @Override
    public Linker popFront() {
        Linker linker = linkers.removeLast();
        assignDefaultDirectorLinker();
        return linker;
    }

    @Override
    public Linker getFront() {
        return linkers.getFirst();
    }

    @Override
    public void pushBack(Linker linker) {
        this.linkers.addLast(linker);
        linker.setTrain(this);
        // assignDefaultDirectorLinker();
    }

    @Override
    public Linker popBack() {
        Linker linker = linkers.removeLast();
        // assignDefaultDirectorLinker();
        linker.setTrain(null);
        return linker;
    }

    @Override
    public Linker getBack() {
        return linkers.getLast();
    }

    @Override
    public boolean isEmpty() {
        return linkers.isEmpty();
    }

    @Override
    public int size() {
        return linkers.size();
    }

    public void assignDefaultDirectorLinker() {
        setDirectorLinker(getTractors() != null
                &&
                !getTractors().isEmpty()
                        ? (Tractor) getTractors().get(0)
                        : null);
    }

    @Override
    public void joinTrailerBack(Trailer t) {
        while (!t.isEmpty()) {
            pushBack(t.popFront());
        }
    }

    @Override
    public void joinTrailerFront(Trailer t) {
        while (!t.isEmpty()) {
            pushFront(t.popBack());
        }
    }

    @Override
    public void setDirectorLinker(Tractor linker) {
        this.directorLinker = linker;
    }

    @Override
    public Tractor getDirectorLinker() {
        return directorLinker;
    }

    @Override
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(t -> Tractor.class.isAssignableFrom(t.getClass()))
                .map(t -> (Tractor) t)
                .collect(Collectors.toList());
    }

    @Override
    public boolean advance() {
        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }

        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
        return moveLinkers(normalSense);
    }

    private void setDirPushedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (!isNormalSense) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }

        Tractor tractor = getDirectorLinker();
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
            next.setDir(nextTrack.getDir(pushDir.inverse()));
            pushDir = next.getDir();
        }
    }

    private void setDirTowedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (isNormalSense) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }
        Tractor tractor = getDirectorLinker();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            if (next == tractor) {
                break;
            }
        }
        Track oldTrack = ((Locomotive) tractor).getTrack();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            next.setDir(next.getPosition().locate(oldTrack.getPosition()));
            oldTrack = next.getTrack();
        }
    }

    private boolean moveLinkers(boolean isNormalSense) {
        Linker firstLinker = getLinkers().getFirst();
        Linker lastLinker = getLinkers().getLast();
        Iterator<Linker> iterator;
        if (isNormalSense && !getDirectorLinker().isReversed()) {
            iterator = getLinkers().iterator();
            firstLinker = getLinkers().getFirst();
            lastLinker = getLinkers().getLast();
        } else {
            iterator = getLinkers().descendingIterator();
            firstLinker = getLinkers().getLast();
            lastLinker = getLinkers().getFirst();
        }
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track track = next.getTrack();
            Dir nextDir = next.getDir();
            Track nextTrack = track.getConnected(nextDir);
            if (nextTrack != null) {
                if (nextTrack.getLinker() == null) {
                    Sensor sensor = nextTrack.getSensor();
                    if (sensor != null && next == lastLinker) {
                        sensor.onExitTrain(next.getTrain());
                    }
                    next.getTrack().removeLinker();
                    if (nextTrack.canEnter(next.getDir().inverse(), next)) {
                        nextTrack.enterLinkerFromDir(next.getDir().inverse(), next);
                        sensor = nextTrack.getSensor();
                        if (sensor != null && next == firstLinker) {
                            sensor.onEnterTrain(next.getTrain());
                        }
                    } else {
                        // System.out.println("NO PUEDO ENTRAR AQUÍ !!!");
                        return false;
                    }
                } else {
                    crash(nextTrack.getLinker());
                    return false;
                }
            } else {
                // System.out.println("Ojo, no hay track en " + track.getPosition() + " -> " +
                // next.getDir());
                return false;
            }
        }
        return true;
    }

    private void crash(Linker linker) {
        // System.out.println("Choque de :" + linker);
    }

    public Linker getFirstLinker() {
        return linkers.getFirst();
    }

    public Linker getLastLinker() {
        return linkers.getLast();
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitLocomotive((Locomotive) this.getDirectorLinker());
    }

    /*
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
    public void setLinkersToJoin(boolean forwardDirection) {
        linkersToJoin.clear();
        joined = false;
        Linker lastLinker = null;
        Dir dir = Dir.E;

        if (getLinkers().size() == 1) {
            lastLinker = (Linker) getDirectorLinker();
            if (forwardDirection) {
                linkerJoinSense = LinkersSense.FRONT;
                dir = lastLinker.getRealDir();
            } else {
                linkerJoinSense = LinkersSense.BACK;
                dir = lastLinker.getTrack().getDir(lastLinker.getRealDir());
            }
        } else if (getLinkers().size() > 1) {
            if (forwardDirection) {
                lastLinker = getLinkers().getFirst();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = LinkersSense.FRONT;
                }
            } else {
                lastLinker = getLinkers().getLast();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = LinkersSense.BACK;
                }
            }
        }
        if (lastLinker != null && dir != null) {
            Track track = lastLinker.getTrack();
            Track nextTrack = track.getConnected(dir);
            RailIterator iterator = new RailIterator(nextTrack, dir);
            Linker nextLinker = iterator.getTrack().getLinker();
            if (nextLinker != null && this != nextLinker.getTrain()) {
                while (nextLinker != null) {
                    if (nextLinker.getTrain() != this) {
                        linkersToJoin.add(nextLinker);
                    }
                    iterator.advance();
                    nextLinker = iterator.getTrack().getLinker();
                }
            }
        }
    }

    public void joinLinkers() {
        if (!joined) {

            for (Linker linkerToJoin : linkersToJoin) {
                if (linkerJoinSense == LinkersSense.FRONT) {
                    this.linkers.addFirst(linkerToJoin);
                } else {
                    this.linkers.addLast(linkerToJoin);
                }

                Train train = linkerToJoin.getTrain();
                linkerToJoin.setTrain(this);
                if (train != null && linkerToJoin == train.getDirectorLinker()) {
                    train.assignDefaultDirectorLinker();
                    if (train.getDirectorLinker() == null) {
                        train.getLinkers().stream().forEach(linker -> linker.setTrain(null));
                    }
                }
            }
            linkersToJoin.clear();
            joined = true;
        }
    }

    public void setFrontDivisionSense() {
        linkerDivisionSense = LinkersSense.FRONT;
    }

    public void setBackDivisionSense() {
        linkerDivisionSense = LinkersSense.BACK;
    }

    public void selectNextDivisionLink() {
        if (numLinkersToRemove < getLinkers().size() - 1) {
            numLinkersToRemove++;
        }
        linkersToRemove.clear();
        Iterator<Linker> linkerIterator = getLinkers().iterator();
        if (linkerDivisionSense == LinkersSense.FRONT) {
            linkerIterator = getLinkers().descendingIterator();
        } else {
            linkerIterator = getLinkers().iterator();
        }
        for (int n = 0; n < numLinkersToRemove; n++) {
            Linker next = linkerIterator.next();
            if (next != getDirectorLinker()) {
                linkersToRemove.addLast(next);
            } else {
                numLinkersToRemove--;
                return;
            }
        }
    }

    public void selectPrevDivisionLink() {
        if (numLinkersToRemove > 0) {
            numLinkersToRemove--;
        }
        linkersToRemove.clear();
        Iterator<Linker> linkerIterator = getLinkers().iterator();
        if (linkerDivisionSense == LinkersSense.FRONT) {
            linkerIterator = getLinkers().descendingIterator();
        } else {
            linkerIterator = getLinkers().iterator();
        }
        for (int n = 0; n < numLinkersToRemove; n++) {
            Linker next = linkerIterator.next();
            if (next != getDirectorLinker()) {
                linkersToRemove.addLast(next);
            } else {
                numLinkersToRemove--;
                return;
            }
        }
    }

    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {
        Linker linkerToRemove = null;
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerDivisionSense == LinkersSense.BACK) {
                linkerToRemove = getLinkers().removeFirst();
            } else {
                linkerToRemove = getLinkers().removeLast();
            }
            linkerToRemove.setTrain(null);
            if (linkerToRemove instanceof Locomotive) {
                Train train = new Train(nextTrainIdSupplier.get());
                linkerToRemove.setTrain(train);
                train.getLinkers().add(linkerToRemove);
                train.assignDefaultDirectorLinker();
            }
        }
        linkersToRemove.clear();
        numLinkersToRemove = 0;
    }

    public List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier) {
        List<Linker> linkersToDestroy = new ArrayList<>();
        Linker linkerToRemove = null;
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerDivisionSense == LinkersSense.BACK) {
                linkerToRemove = getLinkers().removeFirst();
            } else {
                linkerToRemove = getLinkers().removeLast();
            }
            linkerToRemove.setTrain(null);
            if (linkerToRemove instanceof Locomotive) {
                Train train = new Train(nextTrainIdSupplier.get());
                linkerToRemove.setTrain(train);
                train.getLinkers().add(linkerToRemove);
                train.assignDefaultDirectorLinker();
            }
            linkersToDestroy.add(linkerToRemove);
        }
        linkersToRemove.clear();
        numLinkersToRemove = 0;
        return linkersToDestroy;
    }

    public Deque<Linker> getLinkersToRemove() {
        return this.linkersToRemove;
    }

    // Devuelve la dirección en la que hay un linker que no pertenece al tren
    Dir getLinkDir2(Linker linker) {
        Dir linkerDir = linker.getDir();
        Dir resultDir = linkerDir;
        Train train = getAdjacentTrain(linker, linkerDir);
        try {
            if (train != this) {
                return resultDir;
            }
            resultDir = linker.getTrack().getDir(resultDir);
            train = getAdjacentTrain(linker, resultDir);
            if (train != this) {
                return resultDir;
            }
            log.error("Error getting link dir:" + resultDir + " train:" + train);
            return null;
        } catch (Exception e) {
            log.error("Error getting link dir", e);
            return null;
        }
    }

    Dir getLinkDir(Linker linker) {
        Dir linkerDir = linker.getDir();
        Linker adjacentLinker = getAdjacentLinker(linker, linkerDir);
        if (adjacentLinker != null && adjacentLinker.getTrain() != this) {
            return linkerDir;
        }
        linkerDir = linker.getTrack().getDir(linkerDir);
        adjacentLinker = getAdjacentLinker(linker, linkerDir);
        if (adjacentLinker != null && adjacentLinker.getTrain() != this) {
            return linkerDir;
        }
        return null;
    }

    Linker getAdjacentLinker(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            return linker.getTrack().getConnected(dir).getLinker();
        }
        return null;
    }

    Train getAdjacentTrain(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            Linker connectedLinker = linker.getTrack().getConnected(dir).getLinker();
            if (connectedLinker != null) {
                return connectedLinker.getTrain();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Train " + getId();
    }

    public void setPlatformId(int railPlatformId) {
        this.railPlatformId = railPlatformId;
    }

    public void startLoadUnloadProcess() {
        if (railPlatformId != 0 && getDirectorLinker().getSpeed() == 0) {
            isLoading = true;
        }
    }

    public void endLoadUnloadProcess() {
        if (railPlatformId != 0 && getDirectorLinker().getSpeed() == 0) {
            isLoading = false;
        }

    }
}
