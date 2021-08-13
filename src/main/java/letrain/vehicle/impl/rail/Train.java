package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.map.UVector;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Tractor, Transportable {
    protected static final float DISTANCE_UNIT = 50;
    private static final float MAX_VELOCITY = 8f;
    public static final float BRAKES_COEFICIENT = 0.5f;
    public static final float FRICTION_COEFICIENT = 0.01f;
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected Tractor directorLinker;
    private static final Logger log = LoggerFactory.getLogger(Train.class);
    UVector location;

    UVector velocity;
    UVector acceleration;
    UVector motorForce;
    UVector externalForce;
    boolean reversed = false;

    protected boolean brakesActivated = false;
    private Linker externalPushedLinker;

    public Train() {
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
        location = new UVector();
        velocity = new UVector();
        acceleration = new UVector();
        motorForce = new UVector();
        externalForce = new UVector();
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
        assignDefaultDirectorLinker();
        linker.setTrain(this);
    }

    @Override
    public Linker popFront() {
        Linker linker = linkers.removeFirst();
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
        assignDefaultDirectorLinker();
    }

    @Override
    public Linker popBack() {
        Linker linker = linkers.removeLast();
        assignDefaultDirectorLinker();
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
        setDirectorLinker(getTractors() != null && !getTractors().isEmpty() ? (Tractor) getTractors().get(0) : null);
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

    /**
     * La fuerza de fricción de un trailer es la suma de las fuerzas de fricción de todos los linkers.
     * Cada uno tiene su masa y su coeficiente de fricción. Al multiplicarlos por la aceleración del trailer obtenemos
     * la fuerza de fricción total, que se restará a la fuerza de tracción total
     * En los linkers estan incluidas los tractors también, que, en este caso, son un linker más que arrastrar
     *
     * @return
     */
    @Override
    public float getFrictionForce() {
        return (float) getLinkers()
                .stream()
                .mapToDouble(linker -> linker.getFrictionCoefficient() * linker.getMass())
                .sum();
    }

    /***********************************************************
     * Transportable implementation
     **********************************************************/

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }

    @Override
    public float getFrictionCoefficient() {
        return 0;
    }

    @Override
    public float getAcceleration() {
        return this.acceleration.x;
    }

    @Override
    public void setAcceleration(float speed) {
        this.acceleration.x = speed;
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
    public void reverseMotor(boolean reversed) {
        getDirectorLinker().reverseMotor(reversed);
    }

    @Override
    public boolean isMotorReversed() {
        return getDirectorLinker().isMotorReversed();
    }


    @Override
    public void incBrakes(int i) {
        getLinkers().forEach(t -> t.incBrakes(i));
    }

    @Override
    public void decBrakes(int i) {
        getLinkers().forEach(t -> t.decBrakes(i));
    }

    @Override
    public float getBrakes() {
        return getLinkers().stream().map(t -> t.getBrakes()).reduce((a, b) -> a + b).get();
    }

    @Override
    public void setBrakes(float i) {
        getLinkers().forEach(t -> t.setBrakes(i));
    }

    @Override
    public Track move() {
        if (applyForces()) {
            Track t = moveLinkers();
            if(t==null) {
                location.set(0);
            }else{
                velocity.set(0);
            }
            return t;
        }
        acceleration.set(0);
        return null;
    }

    public boolean applyForces() {
        float prevVelocitySign = Math.signum(velocity.x);
        addForce(externalForce);
        motorForce.set(getTractorsForce());
        addForce(motorForce);
        applyFriction();
        applyBrakes();
        velocity.add(acceleration);
        if(limitVelocity()){
            location.add(velocity);
        }
        return Math.abs(location.x) > 10.0;
    }

    private boolean limitVelocity() {
        if (getVelocity() < 0 && !isReversed() || getVelocity() > 0 && isReversed()) {
            velocity.set(0);
            acceleration.set(0);
            return false;
        } else {
            velocity.limit(MAX_VELOCITY);
            return true;
        }
    }

    private Track moveLinkers() {
        Iterator<Linker> iterator = iterator();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track crashedTrack = next.move();
            if ( crashedTrack != null) {
                if(crashedTrack.getLinker()!=null) {
                    crash(next, crashedTrack);
                }else{
                    System.out.println("No hay salida por esta vía!!!!!");
                }
                return crashedTrack;
            }
        }
        return null;
    }

    public Iterator<Linker> iterator() {
        if (!isReversed()) {
            return getLinkers().iterator();
        } else {
            return getLinkers().descendingIterator();
        }
    }

    /***********************************************************
     * Reversible implementation
     **********************************************************/
    @Override
    public void reverse(boolean reversed) {
        this.reversed = reversed;
        getLinkers()
                .stream()
                .forEach(t -> t.reverse(reversed));
    }

    @Override
    public boolean isReversed() {
        return this.reversed;
    }

    /***********************************************************
     * Tractor implementation
     **********************************************************/

    public float getExternalForce() {
        return externalForce.x;
    }

    @Override
    public float getForce() {
        return getTractorsForce();
    }

    public float getLocation() {
        return location.x;
    }

    @Override
    public void setForce(float force) {
        getTractors()
                .forEach(t -> t.setForce(force));
    }

    @Override
    public void incForce() {
        incForce(1);
    }

    @Override
    public void incForce(float force) {
        getTractors()
                .forEach(t -> t.incForce(force));
    }

    @Override
    public void decForce() {
        decForce(1);
    }

    @Override
    public void decForce(float force) {
        getTractors()
                .forEach(t -> t.decForce(force));
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/
    @Override
    public void accept(Visitor visitor) {
        visitor.visitTrain(this);
    }

    /***********************************************************
     * Train
     **********************************************************/
    public float getVelocity() {
        return velocity.x;
    }

    public void addForce(UVector force) {
        acceleration.add(UVector.div(force, getMass()));
    }

    public void applyFriction() {
        addForce(new UVector(getVelocity() * getMass() * FRICTION_COEFICIENT * -1.0f));
    }

    public void applyBrakes() {
        addForce(new UVector(getVelocity()* getBrakes() * BRAKES_COEFICIENT * -1.0f));
    }

    public float getTractorsForce() {
        return (float) getTractors()
                .stream()
                .mapToDouble(Tractor::getForce)
                .sum();
    }


    public boolean isBrakesActivated() {
        return brakesActivated;
    }

    public void setBrakesActivated(boolean brakesActivated) {
        this.brakesActivated = brakesActivated;
    }


    private void crash(Linker crasherLinker, Track track) {
        Train crashedTrain = track.getLinker().getTrain();
        float transmittedForce = Math.abs(getMass() * getAcceleration());
        if(!isAPush(crasherLinker, track.getLinker())) {
            transmittedForce*=-1;
        }
        crashedTrain.applyExternalForce(transmittedForce);

        System.out.println("Transmitiendo fuerza :" + transmittedForce + " a " + crasherLinker);
    }

    private void applyExternalForce(float amount) {
        externalForce.set(amount);
        UVector f = UVector.div(externalForce, getMass());
        acceleration.add(f);
    }

    public boolean isAPush(Linker me, Linker you){
        Dir myDir = me.isReversed()?me.getDir().inverse():me.getDir();
        Dir yourDir = you.isReversed()?you.getDir().inverse():you.getDir();
        int angularDistance = Math.abs(myDir.angularDistance(yourDir));
        if(angularDistance <= 2 ||  angularDistance>= 6) {
            return true;
        }
        return false;
    }

}
