package letrain.vehicle.impl.rail;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Transportable {
    private static final int MAX_LOADING_COUNT = 200;
    Logger log = LoggerFactory.getLogger(Train.class);
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected final Deque<Linker> linkersToJoin;
    private int numLinkersToRemove = 0;
    private int numLinkersToJoin = 0;

    public int getNumLinkersToJoin() {
        return numLinkersToJoin;
    }

    public int getNumLinkersToRemove() {
        return numLinkersToRemove;
    }

    protected final Deque<Linker> linkersToRemove;
    int railStationId = 0;
    public boolean isLoading = false;
    int id;

    public int getStationId() {
        return railStationId;
    }

    public void setStationId(int railStationId) {
        this.railStationId = railStationId;
    }
    Itinerary itinerary;

    enum LinkersSense {
        FRONT, BACK
    };

    LinkersSense linkerJoinSense;
    LinkersSense linkerDivisionSense;
    boolean joined = false;
    protected Tractor directorLinker;
    private int loadingCount;

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

    public void addLinkerToJoin() {
        if (numLinkersToJoin < linkersToJoin.size()) {
            numLinkersToJoin++;
            System.out.println("Train.addLinkerToJoin: num=" + numLinkersToJoin); // DEBUG
        }
    }

    public void removeLinkerToJoin() {
        if (numLinkersToJoin > 0) {
            numLinkersToJoin--;
            System.out.println("Train.removeLinkerToJoin: num=" + numLinkersToJoin); // DEBUG
        }
    }

    public List<Linker> getSelectedLinkersToJoin() {
        if (linkersToJoin.isEmpty() || numLinkersToJoin == 0)
            return new ArrayList<>();
        // Convert deque to list to slice it
        List<Linker> all = new ArrayList<>(linkersToJoin);
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return all.subList(0, numLinkersToJoin);
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
        setDirTowedLinkers(normalSense);
        return moveLinkers(normalSense);
    }

    public void refreshLinkersDirection() {
        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }
        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
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
                    if (getDirectorLinker().getSpeed() > 1) {
                        crash(nextTrack.getLinker());
                        return false;
                    }
                    Train crashedTrain = nextTrack.getLinker().getTrain();
                    if (crashedTrain != null && crashedTrain.getDirectorLinker().getSpeed() > 1) {
                        crash(nextTrack.getLinker());
                        return false;
                    }
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
        getLinkers().forEach(Linker::destroy);
        if (linker.getTrain() != null) {
            linker.getTrain().getLinkers().forEach(Linker::destroy);
            ;
        } else {
            linker.destroy();
        }
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
        numLinkersToJoin = linkersToJoin.size();
    }

    public void joinLinkers() {
        if (!joined) {
            int count = 0;
            for (Linker linkerToJoin : linkersToJoin) {
                if (count >= numLinkersToJoin)
                    break;

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
                count++;
            }
            linkersToJoin.clear();
            joined = true;
        }
    }

    public void setFrontDivisionSense() {
        linkerDivisionSense = LinkersSense.FRONT;
        updateLinkersToRemove();
    }

    public void setBackDivisionSense() {
        linkerDivisionSense = LinkersSense.BACK;
        updateLinkersToRemove();
    }

    public void resetUnlinkState() {
        System.out.println("Train.resetUnlinkState: Resetting state"); // DEBUG
        if (!linkers.isEmpty() && linkers.peekLast() == getDirectorLinker()) {
            linkerDivisionSense = LinkersSense.FRONT;
        } else {
            linkerDivisionSense = LinkersSense.BACK;
        }
        numLinkersToRemove = 1;
        updateLinkersToRemove();
    }

    public void resetLinkState() {
        System.out.println("Train.resetLinkState: Resetting state"); // DEBUG
        numLinkersToJoin = 0;
        linkersToJoin.clear();
    }

    public void selectNextDivisionLink() {
        if (numLinkersToRemove < getLinkers().size() - 1) {
            numLinkersToRemove++;
        }
        updateLinkersToRemove();
    }

    public void selectPrevDivisionLink() {
        if (numLinkersToRemove > 0) {
            numLinkersToRemove--;
        }
        updateLinkersToRemove();
    }

    private void updateLinkersToRemove() {
        linkersToRemove.clear();
        Iterator<Linker> linkerIterator = getLinkers().iterator();
        if (linkerDivisionSense == LinkersSense.FRONT) {
            linkerIterator = getLinkers().iterator();
        } else {
            linkerIterator = getLinkers().descendingIterator();
        }
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerIterator.hasNext()) {
                Linker next = linkerIterator.next();
                if (next != getDirectorLinker()) {
                    linkersToRemove.addLast(next);
                } else {
                    // Si nos topamos con el director, no podemos desvincularlo, así que reducimos
                    // la cuenta y paramos.
                    // Esto asume que el director no se puede desvincular de sí mismo si es el
                    // único.
                    // Pero en divideTrain se intenta separar.
                    // La lógica original tenía este check.
                    numLinkersToRemove--;
                    return;
                }
            }
        }
    }

    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {
        System.out.println("Train.divideTrain: numToRemove=" + numLinkersToRemove); // DEBUG
        Linker linkerToRemove = null;
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerDivisionSense == LinkersSense.BACK) {
                linkerToRemove = getLinkers().removeLast();
            } else {
                linkerToRemove = getLinkers().removeFirst();
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
                linkerToRemove = getLinkers().removeLast();
            } else {
                linkerToRemove = getLinkers().removeFirst();
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



    public void startLoadUnloadProcess() {
        if (railStationId != 0 && getDirectorLinker().getSpeed() == 0) {
            setLoadingCount(MAX_LOADING_COUNT);
            setLoading(true);
        }
    }

    public void endLoadUnloadProcess() {
        if (railStationId != 0 && getDirectorLinker().getSpeed() == 0) {
            setLoading(false);
        }
    }

    public void load() {
        if (isLoading()) {
            if (getLoadingCount() > 0) {
                setLoadingCount(getLoadingCount() - 1);
            } else {
                endLoadUnloadProcess();
            }
        }
    }

    public int getLoadingCount() {
        return loadingCount;
    }

    public void setLoadingCount(int loadingCount) {
        this.loadingCount = loadingCount;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean isLoading) {
        this.isLoading = isLoading;
    }

    public int getDistanceTraveled() {
        return getDirectorLinker().getDistanceTraveled();
    }

    public Stop recordStopAtStation() {
        Stop stop = new Stop(railStationId, LocalDateTime.now(), getDistanceTraveled());
        if (this.itinerary == null) {
            this.itinerary = new Itinerary();
        }
        this.itinerary.addStop(stop);
        return stop;
    }

    public Itinerary getItinerary() {
        return this.itinerary;
    }

    public void syncLinkersPosition() {
        if (linkers != null) {
            linkers.forEach(linker -> linker.syncPosition());
        }
    }
}
