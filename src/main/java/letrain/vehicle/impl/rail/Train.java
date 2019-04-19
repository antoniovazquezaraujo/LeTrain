package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.map.Reversible;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Vehicle;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.render.Renderable;
import letrain.render.Renderer;

import java.util.*;
import java.util.stream.Collectors;

public class Train implements Trailer<RailTrack>, Reversible, Renderable {
    private static final float DISTANCE_UNIT = 1;
    private final Deque<Linker> linkers;
    private final List<Tractor> tractors;
    private Tractor mainTractor;
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
        assignDefaultMainTractor();
        ret.setMainTractor(ret.getTractors() != null && !ret.getTractors().isEmpty() ? ret.getTractors().get(0) : null);
        return ret;
    }

    public void assignDefaultMainTractor() {
        setMainTractor(getTractors() != null && !getTractors().isEmpty() ? getTractors().get(0) : null);
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
    public void setMainTractor(Tractor tractor) {
        this.mainTractor = tractor;
    }

    @Override
    public Tractor getMainTractor() {
        return mainTractor;
    }

    @Override
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(t -> Tractor.class.isAssignableFrom(t.getClass()))
                .map(t -> (Tractor) t)
                .collect(Collectors.toList());
    }

    @Override
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
    public float getTotalForce() {
        return getTractorsForce() - getFrictionForce();
    }

    @Override
    public float getTotalMass() {
        return (float) getLinkers()
                .stream()
                .mapToDouble(Vehicle::getMass)
                .sum();
    }

    @Override
    public void applyForces() {
        this.acceleration = getTotalForce() / getTotalMass();
        float speed = acceleration ;
        this.distanceTraveled += speed;
        if (this.distanceTraveled >= DISTANCE_UNIT) {
            move();
            this.distanceTraveled=0;
        }
    }

    @Override
    public void move() {
        Linker tractor = (Linker) getMainTractor();
        Dir tractionDir = tractor.getDir();
        Iterator<Linker> pushIterator;
        Iterator<Linker> pullIterator;

        if(isReversed()){
            pushIterator= getLinkers().descendingIterator();
            pullIterator= getLinkers().iterator();
        }else{
            pushIterator= getLinkers().iterator();
            pullIterator= getLinkers().descendingIterator();
        }

        //Avanzamos hasta llegar al tractor desde el principio
        while(pushIterator.hasNext()){
            Linker next = pushIterator.next();
            if(next == tractor){
                break;
            }
        }
        //Avanzamos hasta llegar al tractor desde el final
        while(pullIterator.hasNext()){
            Linker next = pullIterator.next();
            if(next == tractor){
                break;
            }
        }

        Dir pushDir = tractionDir;
        while(pushIterator.hasNext()){
            Linker next = pushIterator.next();
            Track nextTrack = next.getTrack();
            //usamos la dirección inversa, estamos empujando
            next.setDir(nextTrack.getDir(tractionDir).inverse());
            tractionDir = next.getDir();
        }

        Dir pullDir = tractionDir;
        while(pullIterator.hasNext()){
            Linker next = pullIterator.next();
            Track nextTrack = next.getTrack();
            next.setDir(next.getTrack().getDir(tractionDir).inverse());
            tractionDir = next.getDir();
        }

        Iterator<Linker> moveIterator = getLinkers().iterator();
        if(isReversed()){
            moveIterator = getLinkers().descendingIterator();
        }
        while(moveIterator.hasNext()){
            Linker next = moveIterator.next();
            Track track = next.getTrack();
            Track nextTrack = track.getConnected(next.getDir());
            if(nextTrack != null){
                if(nextTrack.getLinker() == null) {
                    next.getTrack().removeLinker();
                    nextTrack.enterLinkerFromDir(next.getDir().inverse(), next);
                }else{
                    System.out.println("CRASHHH!!!");
                }
            }
        }

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
        this.distanceTraveled = 0;
    }


    @Override
    public boolean reverse() {
        if(this.mainTractor!=null) {
            ((Locomotive) this.mainTractor).reverse();
            return ((Locomotive) this.mainTractor).isReversed();
        }else{
            return false;
        }
    }

    @Override
    public boolean isReversed() {
        if(this.mainTractor!=null) {
            return ((Locomotive) this.mainTractor).isReversed();
        }
        return false;
    }
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderTrain(this);
    }
}
