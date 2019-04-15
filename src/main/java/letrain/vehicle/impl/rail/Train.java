package letrain.vehicle.impl.rail;

import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class Train implements Trailer<RailTrack> {
    private static final float DISTANCE_UNIT = 20;
    Deque<Linker<RailTrack>> linkers;
    List<Tractor> tractors;
    Tractor mainTractor;
    private float acceleration;
    private float distanceTraveled;

    public Train() {
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
    }

    /***********************************************************
     * Trailer implementation
     **********************************************************/

    @Override
    public Deque<Linker<RailTrack>> getLinkers() {
        return linkers;
    }

    @Override
    public void pushFront(Linker<RailTrack> linker) {
        this.linkers.addFirst(linker);
    }

    @Override
    public Linker<RailTrack> popFront() {
        return linkers.removeLast();
    }

    @Override
    public Linker<RailTrack> getFront() {
        return linkers.getFirst();
    }

    @Override
    public void pushBack(Linker<RailTrack> linker) {
        this.linkers.addLast(linker);
    }

    @Override
    public Linker<RailTrack> popBack() {
        return linkers.removeLast();
    }

    @Override
    public Linker<RailTrack> getBack() {
        return linkers.getLast();
    }

    @Override
    public void setMainTractor(Tractor tractor) {
        this.mainTractor = tractor;
    }

    @Override
    public Tractor getMainTractor() {
        return mainTractor;
    }

    @Override
    public List<Tractor> getTractors() {
        return tractors;
    }

    @Override
    public void addTractor(Tractor tractor) {
        tractors.add(tractor);
    }

    @Override
    public void removeTractor(Tractor tractor) {
        tractors.remove(tractor);
    }

    @Override
    public float getTractorsForce() {
        return (float) getTractors()
                .stream()
                .mapToDouble(t -> t.getForce())
                .sum();
    }

    /**
     * La fuerza de fricción de un tren es la suma de las fuerzas de fricción de todos los linkers.
     * Cada uno tiene su masa y su coeficiente de fricción. Al multiplicarlos por la aceleración del tren obtenemos
     * la fuerza de fricción total, que se restará a la fuerza de tracción total
     * En los linkers estan incluidas las locomotoras también, que, en este caso, son un vagón más que arrastrar
     *
     * @return
     */
    @Override
    public float getFrictionForce() {
        return (float) getLinkers()
                .stream()
                .mapToDouble(l -> l.getFrictionCoefficient() * l.getMass() * getAcceleration())
                .sum();
    }

    @Override
    public float getTotalForce() {
        return getTractorsForce() - getFrictionForce();
    }

    @Override
    public float getTotalMass() {
        return (float) getLinkers()
                .stream()
                .mapToDouble(l -> l.getMass())
                .sum();
    }

    @Override
    public void applyForces() {
        float lastAcceleration = getAcceleration();
        this.acceleration = getTotalForce() / getTotalMass();
        float speed = acceleration - lastAcceleration;
        this.distanceTraveled += speed;
        if (this.distanceTraveled >= DISTANCE_UNIT) {
            move();
        }
    }

    @Override
    public void move() {
        System.out.println("Moving train!");
    }

    @Override
    public float getAcceleration() {
        return this.acceleration;
    }

    @Override
    public float getSpeed() {
        return getTotalForce() / getTotalMass();
    }

    @Override
    public float getDistanceTraveled() {
        return distanceTraveled;
    }

    @Override
    public void resetDistanceTraveled() {
        this.distanceTraveled = 0;
    }

}
