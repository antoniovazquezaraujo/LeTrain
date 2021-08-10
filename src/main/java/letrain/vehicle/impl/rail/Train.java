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
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected Tractor directorLinker;
    private static final Logger log = LoggerFactory.getLogger(Train.class);
    UVector location;

    UVector velocity;
    UVector acceleration;
    UVector motorForce;
    UVector externalForce;

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
    public boolean advance() {
        return moveLinkers(getDirectorLinker().isReversed());
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

    /***********************************************************
     * Reversible implementation
     **********************************************************/
    @Override
    public void reverse(boolean reversed) {
        getTractors()
            .stream()
            .forEach(t-> t.reverse(reversed));
    }

    @Override
    public boolean isReversed() {
        if (getDirectorLinker() != null) {
            return getDirectorLinker().isReversed();
        }
        return false;
    }

    /***********************************************************
     * Tractor implementation
     **********************************************************/

    @Override
    public void move() {
        log.debug("Force:"+ getForce());
        applyForce(externalForce);
        log.debug("Force después de aplicar external force:"+ getForce());
        motorForce.set(getTractorsForce());
        log.debug("Motorforce: "+ motorForce);
        applyForce(motorForce);
        log.debug("Force después de aplicar motor force:"+ getForce());
        if(Math.abs(velocity.x) > 0) {
            log.debug("Velocity > 0 :"+ getVelocity());
            applyFriction();
            log.debug("Acceleration después de aplicar friction:"+ getAcceleration());
            applyBrakes();
            log.debug("Acceleration después de aplicar brakes: "+ getAcceleration());
        }
        log.debug("Antes de updateLocation. Acceleration:"+ getAcceleration()+ " Location:"+ getLocation()+ " Velocity:"+ getVelocity());
        velocity.add(acceleration);
        velocity.limit(MAX_VELOCITY);
        location.add(velocity);
        log.debug("Después de updateLocation. Acceleration:"+ getAcceleration()+ " Location:"+ getLocation()+ " Velocity:"+ getVelocity());
        if((Math.signum(velocity.x) < 0.0 ) && !isReversed()){
            log.debug("Velocidad negativa :"+ getVelocity() + " Reversed: "+ isReversed());
            reverse(true);
            log.debug("Después de revertir, Reversed: "+ isReversed());
        }
        if (Math.abs(location.x) > 10.0) {
            log.debug("location > 10: "+getLocation()+ " DirectorLinker is reversed:"+ getDirectorLinker().isReversed());
            updateLinkersSense();
            log.debug("Después de updateLinkersSense DirectorLinker is reversed:"+ getDirectorLinker().isReversed());
            advance();
            log.debug("Después de avance is reversed:"+ getDirectorLinker().isReversed());
            location.set(0);
        }
        acceleration.set(0);
    }
    @Override
    public float getForce() {
        return getTractorsForce();
    }

    public float getLocation(){
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
    public void applyForce(UVector force) {
        UVector f = UVector.div(force, getMass());
        acceleration.add(f);
    }

    public void applyFriction() {
        float c = 100f;
        UVector friction = new UVector(velocity.x);
        friction.mult(-1);
        friction.normalize();
        friction.mult(c);
        boolean prevAccelerationSign = acceleration.x >=0.0;
        if(getVelocity()>0.0) {
            applyForce(friction);
        }
        boolean postAccelerationSign = acceleration.x >=0.0;
        if (prevAccelerationSign != postAccelerationSign){
            acceleration.set(0);
        }
    }

    public void applyBrakes(){
        UVector brakes = new UVector(getBrakes());
        brakes.mult(-1);
        boolean prevVelocitySign = velocity.x >0.0;
        if(getVelocity() >0.0) {
            applyForce(brakes);
        }
        boolean postVelocitySign = velocity.x >0.0;
        if (prevVelocitySign != postVelocitySign){
            velocity.set(0);
        }
    }
    public float getTractorsForce() {
        return (float) getTractors()
                .stream()
                .mapToDouble(Tractor::getForce)
                .sum();
    }

    private void updateLinkersSense(){
        boolean reversed = (velocity.x<0);
        setDirDirectorLinker(reversed);
        setDirPushedLinkers(reversed);
        setDirTowedLinkers(reversed);
    }

    private void setDirDirectorLinker(boolean reversed) {
        Locomotive locomotive = (Locomotive) getDirectorLinker();
        if(reversed)locomotive.setDir(locomotive.getDir().inverse());
    }

    public boolean isBrakesActivated() {
        return brakesActivated;
    }

    public void setBrakesActivated(boolean brakesActivated) {
        this.brakesActivated = brakesActivated;
    }

    //////////////////PRIVATE/////////////////////////////////////////////

    private void setDirPushedLinkers(boolean reversed) {
        Iterator<Linker> iterator;
        if (reversed) {
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
            Linker nextLinker = iterator.next();
            Track nextTrack = nextLinker.getTrack();
            nextLinker.setDir(nextTrack.getDirWhenEnteringFrom(pushDir.inverse()));
            pushDir = nextLinker.getDir();
        }
    }

    private void setDirTowedLinkers(boolean reversed) {
        Iterator<Linker> iterator;
        if (!reversed) {
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
            Linker nextLinker = iterator.next();
            nextLinker.setDir(nextLinker.getPosition().locate(oldTrack.getPosition()));
            oldTrack = nextLinker.getTrack();
        }
    }

    private boolean moveLinkers(boolean reversed) {
        Iterator<Linker> iterator;
        if (!reversed) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track track = next.getTrack();
            Dir nextDir = next.getDir();
            Track nextTrack = null;
            nextTrack = track.getConnected(nextDir);
            if (nextTrack != null) {
                if (nextTrack.getLinker() == null) {
                    next.getTrack().removeLinker();
                    if (nextTrack.canEnter(next.getDir().inverse(), next)) {
                        nextTrack.enterLinkerFromDir(next.getDir().inverse(), next);
                    } else {
                        System.out.println("NO PUEDO ENTRAR AQUÍ !!!");
                        return false;
                    }
                } else {
                    crash(nextTrack.getLinker());
                    return false;
                }
            } else {
                System.out.println("Ojo, no hay track en " + track.getPosition() + " -> " + next.getDir());
                return false;
            }
        }
        return true;
    }

    private void crash(Linker linker) {
        Train crashedTrain = linker.getTrain();
        float transmittedForce = Math.abs(getMass() * getAcceleration());
        crashedTrain.applyExternalForce(transmittedForce, linker);
        System.out.println("Transmitiendo fuerza :" + transmittedForce + " a " + linker);
    }

    private  void applyExternalForce(float amount, Linker linker) {
        externalForce.set(amount);
        UVector f = UVector.div(externalForce, getMass());
        acceleration.add(f);
    }


}
