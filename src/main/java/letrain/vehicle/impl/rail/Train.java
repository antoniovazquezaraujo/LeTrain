package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.render.Renderable;
import letrain.render.Visitor;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Tractor, Transportable {
    private static final float DISTANCE_UNIT = 1;
    private final Deque<Linker> linkers;
    private final List<Tractor> tractors;
    private Linker directorLinker;
    private float acceleration = 0.0F;
    private float distanceTraveled = 0.0F;

    public Train() {
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
    }

    /***********************************************************
     * Trailer implementation
     **********************************************************/

    @Override
    public Deque<Linker> getLinkers() {
        return linkers;
    }

    @Override
    public void pushFront(Linker linker) {
        this.linkers.addFirst(linker);
    }

    @Override
    public Linker popFront() {
        return linkers.removeLast();
    }

    @Override
    public Linker getFront() {
        return linkers.getFirst();
    }

    @Override
    public void pushBack(Linker linker) {
        this.linkers.addLast(linker);
    }

    @Override
    public Linker popBack() {
        return linkers.removeLast();
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


    @Override
    public Trailer divide(Linker p) {
        Trailer<RailTrack> ret = new Train();
        Linker first = getLinkers().getFirst();
        while (first != p) {
            ret.pushFront(getLinkers().removeFirst());
            first = getLinkers().getFirst();
        }
        assignDefaultDirectorLinker();
        return ret;
    }

    public void assignDefaultDirectorLinker() {
        setDirectorLinker(getTractors() != null && !getTractors().isEmpty() ? getLinkers().getFirst() : null);
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
    public void setDirectorLinker(Linker linker) {
        this.directorLinker = linker;
    }

    @Override
    public Linker getDirectorLinker() {
        return directorLinker;
    }

    @Override
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(t -> Tractor.class.isAssignableFrom(t.getClass()))
                .map(t -> (Tractor) t)
                .collect(Collectors.toList());
    }

    public float getTractorsForce() {
        return (float) getTractors()
                .stream()
                .mapToDouble(Tractor::getForce)
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
    public void applyForce() {
        float f = getForce();
        if (f <= 0) return;
        float m = getMass();
        this.acceleration = f / m;
        this.distanceTraveled = this.distanceTraveled + this.acceleration;
        if (this.distanceTraveled >= DISTANCE_UNIT) {
            advance();
            this.distanceTraveled = 0.0F;
        }
    }

    @Override
    public boolean advance() {
        Linker tractor = (Linker) getDirectorLinker();
        Dir tractionDir = tractor.getDir();
        Iterator<Linker> pushIterator;
        Iterator<Linker> pullIterator;

        // Si el tren no está invertido, pushIterator empieza desde el final y se queda al pasar el tractor master
        // y pullIterator empieza al principio para quedarse detras de el.
        // Ambos listos para empujar a los de delante y tirar de los de atrás
        if (!isReversed()) {
            pushIterator = getLinkers().descendingIterator();
            pullIterator = getLinkers().iterator();
        } else {
            pushIterator = getLinkers().iterator();
            pullIterator = getLinkers().descendingIterator();
        }

        //Avanzamos hasta llegar al tractor desde el principio
        while (pushIterator.hasNext()) {
            Linker next = pushIterator.next();
            if (next == tractor) {
                break;
            }
        }
        //Avanzamos hasta llegar al tractor desde el final
        while (pullIterator.hasNext()) {
            Linker next = pullIterator.next();
            if (next == tractor) {
                break;
            }
        }

        //Se empuja a los vehículos que haya delante:
        Dir pushDir = tractionDir;
        while (pushIterator.hasNext()) {
            Linker next = pushIterator.next();
            Track nextTrack = next.getTrack();
            //usamos la dirección inversa, estamos empujando
            next.setDir(nextTrack.getDir(tractionDir.inverse()));
            tractionDir = next.getDir();
        }

        Dir pullDir = tractionDir;
        Track oldTrack = tractor.getTrack();
        while (pullIterator.hasNext()) {
            Linker next = pullIterator.next();
            next.setDir(oldTrack.getDir(tractionDir).inverse());
            tractionDir = next.getDir();
            oldTrack = next.getTrack();
        }

        Iterator<Linker> moveIterator = getLinkers().iterator();
        if (isReversed()) {
            moveIterator = getLinkers().descendingIterator();
        }
        while (moveIterator.hasNext()) {
            Linker next = moveIterator.next();
            Track track = next.getTrack();
            Track nextTrack = track.getConnected(next.getDir());
            if (nextTrack != null) {
                if (nextTrack.getLinker() == null) {
                    next.getTrack().removeLinker();
                    nextTrack.enterLinkerFromDir(next.getDir().inverse(), next);
                } else {
                    System.out.println("CRASHHH!!!");
                    return false;
                }
            }
        }
        return true;

    }

    @Override
    public float getAcceleration() {
        return this.acceleration;
    }

    @Override
    public float getSpeed() {
        return getAcceleration();
    }

    @Override
    public float getDistanceTraveled() {
        return distanceTraveled;
    }

    @Override
    public void resetDistanceTraveled() {
        this.distanceTraveled = 0.0F;
    }


    @Override
    public boolean reverse() {
        getTractors()
                .stream()
                .forEach(t -> t.reverse());
        return true; // TODO: ELIMINAR ESTE RETORNO
    }

    @Override
    public boolean isReversed() {
        if (this.directorLinker != null) {
            return (this.directorLinker).isReversed();
        }
        return false;
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTrain(this);
    }

    @Override
    public float getForce() {
        return getTractorsForce();//- getFrictionForce();
    }

    @Override
    public void setForce(float force) {
        getTractors()
                .forEach(t -> t.setForce(force));
    }

    @Override
    public void incForce(float force) {
        getTractors()
                .forEach(t -> t.incForce(force));
    }

    @Override
    public void decForce(float force) {
        getTractors()
                .forEach(t -> t.decForce(force));
    }

    @Override
    public float getMass() {
        return (float) getLinkers()
                .stream()
                .mapToDouble(t -> {
                    return t.getMass();
                })
                .sum();
    }

    @Override
    public float getFrictionCoefficient() {
        return 0;
    }
}
