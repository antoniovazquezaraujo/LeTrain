package letrain.vehicle.impl.rail;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import letrain.map.Dir;
import letrain.track.CargoTypes;
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
    private static final int MAX_LOADING_COUNT = 80; // 4.0 seconds at 20fps per wagon
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
    private List<TrainEventListener> trainListeners = new CopyOnWriteArrayList<>();

    public void addTrainEventListener(TrainEventListener listener) {
        trainListeners.add(listener);
    }

    public void removeTrainEventListener(TrainEventListener listener) {
        trainListeners.remove(listener);
    }

    public void notifySpeedChanged(int speed) {
        for (TrainEventListener l : trainListeners) {
            l.onSpeedChanged(speed);
        }
    }

    public void notifySenseChanged(boolean forward) {
        for (TrainEventListener l : trainListeners) {
            l.onSenseChanged(forward);
        }
    }

    public void notifyLink() {
        for (TrainEventListener l : trainListeners) {
            l.onLink();
        }
    }

    public void notifyContact() {
        for (TrainEventListener l : trainListeners) {
            l.onContact();
        }
    }

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
        // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
        if (isLoading) {
            return false;
        }
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
                    notifyContact();
                    if (crashedTrain != null) {
                        crashedTrain.notifyContact();
                    }
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
        for (TrainEventListener l : trainListeners) {
            l.onCrash();
        }
        getLinkers().forEach(Linker::destroy);
        if (linker.getTrain() != null) {
            for (TrainEventListener l : linker.getTrain().trainListeners) {
                l.onCrash();
            }
            linker.getTrain().getLinkers().forEach(Linker::destroy);
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
            notifyLink();
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
        return null;
    }

    public letrain.track.Station getStationAtTrain() {
        for (letrain.vehicle.impl.Linker linker : getLinkers()) {
            letrain.track.Track track = linker.getTrack();
            if (track != null && track.getSensor() instanceof letrain.track.Station) {
                return (letrain.track.Station) track.getSensor();
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

    private boolean isUnloadingDirection = false; // True = Unload (Down), False = Load (Up)

    // Punto 6: El tiempo de carga depende de la cantidad de vagones cargados.
    public void startLoadProcess(letrain.track.Station station) {
        setLoading(true);
        this.isUnloadingDirection = false;
        int wagonsToLoad = 0;
        CargoTypes trainCurrentCargoType = getTrainCargoType();

        for (letrain.vehicle.impl.Linker linker : getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                // Punto 10: Si un tren lleva carga no se puede volver a cargar hasta que se
                // descargue.
                // Punto 11: No se permiten trenes que carguen distintos tipos de mercancía
                // simultáneamente.
                if (wagon.getCargoAmount() == 0 &&
                        (trainCurrentCargoType == CargoTypes.NONE
                                || trainCurrentCargoType == station.getCargoType())) {
                    wagonsToLoad++;
                }
            }
        }
        log.info("Train {} starting LOAD at Station {}. Wagons to load: {}. Station cargo: {}. Station role: {}",
                getId(), station.getId(), wagonsToLoad, station.getCargoType(), station.getRole());
        setLoadingCount(MAX_LOADING_COUNT * wagonsToLoad); // SEQUENTIAL: Total time is sum of all wagons
        if (loadingCount == 0) { // Si no hay vagones que puedan cargar, finaliza el proceso inmediatamente
            log.info("LOAD aborted: 0 functional wagons for this cargo type/station.");
            setLoading(false);
        }
    }

    // Punto 9: El tiempo de descarga depende de la cantidad de vagones descargados.
    public void startUnloadProcess(letrain.track.Station station) {
        setLoading(true);
        this.isUnloadingDirection = true;
        int wagonsToUnload = 0;
        for (letrain.vehicle.impl.Linker linker : getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (wagon.getCargoAmount() > 0 && wagon.getCargoType() == station.getCargoType()) {
                    wagonsToUnload++;
                }
            }
        }
        log.info("Train {} starting UNLOAD at Station {}. Wagons to unload: {}. Station cargo: {}. Station role: {}",
                getId(), station.getId(), wagonsToUnload, station.getCargoType(), station.getRole());
        setLoadingCount(MAX_LOADING_COUNT * wagonsToUnload); // SEQUENTIAL: Total time is sum of all wagons
        if (loadingCount == 0) { // Si no hay vagones que puedan descargar, finaliza el proceso inmediatamente
            log.info("UNLOAD aborted: 0 functional wagons for this cargo type/station.");
            setLoading(false);
        }
    }

    public void endLoadUnloadProcess() {
        setLoading(false);
        setLoadingCount(0);
    }

    public boolean performIndustrialAction(letrain.track.Station station) {
        if (getDirectorLinker().getSpeed() != 0)
            return false;

        // Punto 10: Si un tren lleva carga no se puede volver a cargar hasta que se
        // descargue.
        // Punto 11: No se permiten trenes que carguen distintos tipos de mercancía
        // simultáneamente.
        CargoTypes trainCurrentCargoType = getTrainCargoType();

        boolean anyActionTaken = false;
        double totalDistance = 0;
        int deliveryCount = 0;

        if (getLinkers().isEmpty())
            return false;
        List<letrain.vehicle.impl.Linker> wagons = getLinkers().stream()
                .filter(l -> l instanceof Wagon)
                .collect(java.util.stream.Collectors.toList());

        if (wagons.isEmpty())
            return false;

        // CALCULATE CURRENT WAGON INDEX
        // loadingCount goes from (MAX_LOADING_COUNT * numWagons) down to 1.
        // Index 0 is the first wagon in the list, Index (N-1) is the last.
        // We process them in order: first wagon, then second, etc.
        // For numWagons = 2:
        // ticks 160...81 -> wagonIndex 0
        // ticks 80...1 -> wagonIndex 1
        int numWagons = wagons.size();
        int totalTicks = MAX_LOADING_COUNT * numWagons;
        int currentTickInTotal = totalTicks - (loadingCount - 1); // 1 to totalTicks
        int wagonIndex = (currentTickInTotal - 1) / MAX_LOADING_COUNT;

        if (wagonIndex >= numWagons)
            return false;

        Wagon wagon = (Wagon) wagons.get(wagonIndex);
        int wagonTick = (currentTickInTotal - 1) % MAX_LOADING_COUNT; // 0 to 79

        if (station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER) {
            // LOADING
            if (trainCurrentCargoType != CargoTypes.NONE && trainCurrentCargoType != station.getCargoType()) {
                return false;
            }

            // PRECISION SYNC: Reach exactly 50 at tick 80
            int targetCargo = ((wagonTick + 1) * 50) / MAX_LOADING_COUNT;

            if (wagon.getCargoAmount() < targetCargo && !wagon.isFull()) {
                int toLoad = targetCargo - wagon.getCargoAmount();
                int taken = station.takeExportCargo(toLoad);
                if (taken > 0) {
                    wagon.load(taken);
                    wagon.setCargoType(station.getCargoType());
                    wagon.setLoadingPoint(station.getTrack().getPosition());
                    anyActionTaken = true;
                }
            }
        } else if (station.getRole() == letrain.track.CargoTypes.StationRole.CONSUMER) {
            // UNLOADING: Reach exactly 0 at tick 80
            int targetRemaining = 50 - ((wagonTick + 1) * 50) / MAX_LOADING_COUNT;

            if (wagon.getCargoAmount() > targetRemaining && wagon.getCargoType() == station.getCargoType()) {
                int toUnload = wagon.getCargoAmount() - targetRemaining;
                wagon.unload(toUnload);
                station.receiveImportCargo(toUnload);

                if (wagon.getLoadingPoint() != null) {
                    totalDistance += letrain.map.Point.distance(wagon.getLoadingPoint(),
                            station.getTrack().getPosition());
                    deliveryCount++;
                }
                if (wagon.getCargoAmount() == 0) {
                    wagon.setCargoType(letrain.track.CargoTypes.NONE);
                    wagon.setLoadingPoint(null);
                }
                anyActionTaken = true;
            }
        }

        if (anyActionTaken && deliveryCount > 0) {
            // Pay reward (placeholder for EconomyManager update)
            // economyManager.onCargoDelivered(totalAmount, totalDistance)
        }

        return anyActionTaken;
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

    public int getRailStationId() {
        return railStationId;
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

    // Método auxiliar para determinar el tipo de carga general del tren
    public CargoTypes getTrainCargoType() {
        CargoTypes firstCargoType = CargoTypes.NONE;
        for (Linker linker : linkers) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (wagon.getCargoAmount() > 0) {
                    if (firstCargoType == CargoTypes.NONE) {
                        firstCargoType = wagon.getCargoType();
                    } else if (firstCargoType != wagon.getCargoType()) {
                        return null; // Indica carga mixta
                    }
                }
            }
        }
        return firstCargoType;
    }
}
