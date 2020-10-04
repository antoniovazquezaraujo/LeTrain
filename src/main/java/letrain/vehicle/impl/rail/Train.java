package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.map.UVector;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Transportable;
import letrain.vehicle.Vehicle;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Tractor, Transportable {
    protected static final float DISTANCE_UNIT = 50;
    private static final float MAX_VELOCITY = 2f;
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected Tractor directorLinker;

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
                .mapToDouble(linker -> linker.getFrictionCoefficient() * linker.getMass())
                .sum();
    }


    @Override
    public boolean advance() {
        Iterator<Linker> normalIterator = getLinkers().iterator();
        Iterator<Linker> reverseIterator = getLinkers().descendingIterator();
        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }
        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
        if (getDirectorLinker().isReversed()) {
            Dir directorDir = ((Vehicle)getDirectorLinker()).getDir();
            ((Vehicle)getDirectorLinker()).setDir(directorDir.inverse());
        }

        return moveLinkers(normalSense);
    }

    private void setDirTractors(boolean isNormalSense) {
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
            Linker nextLinker = iterator.next();
            Track nextTrack = nextLinker.getTrack();
            nextLinker.setDir(nextTrack.getDir(pushDir));
            pushDir = nextLinker.getDir().inverse();
        }
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
            Linker nextLinker = iterator.next();
            Track nextTrack = nextLinker.getTrack();
            nextLinker.setDir(nextTrack.getDir(pushDir));
            pushDir = nextLinker.getDir().inverse();
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
            Linker nextLinker = iterator.next();
            nextLinker.setDir(nextLinker.getPosition().locate(oldTrack.getPosition()));
            oldTrack = nextLinker.getTrack();
        }
    }


    private boolean moveLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (isNormalSense) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track track = next.getTrack();
            Dir nextDir = next.getDir();
            Track nextTrack = track.getConnected(nextDir);
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


    public void applyExternalForce(float amount, Linker linker) {
        externalForce.set(amount);
        UVector f = UVector.div(externalForce, getMass());
        acceleration.add(f);
    }

    private void setExternalPushedLinker(Linker linker) {
        this.externalPushedLinker = linker;
    }

    private Linker getExternalPushedLinker() {
        return this.externalPushedLinker;
    }

    public Linker getFirstLinker() {
        return linkers.getFirst();
    }

    public Linker getLastLinker() {
        return linkers.getLast();
    }

    float turns = 0.0f;

    @Override
    public void applyForce() {
        update();
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
        applyForce(friction);
    }

    public void applyBrakes(){
        UVector brakes = new UVector(getBrakes());
        brakes.mult(-1);
        applyForce(brakes);
    }
    public void update() {
        System.out.println("Antes: "+ getAcceleration());
        applyForce(externalForce);
        System.out.println("Después:               "+ getAcceleration());
        motorForce.set(getTractorsForce());
        applyForce(motorForce);
        if(Math.abs(velocity.x) > 0) {
            applyFriction();
            applyBrakes();
        }
        velocity.add(acceleration);
        velocity.limit(MAX_VELOCITY);
        location.add(velocity);
//        System.out.println("Acceleration: "+ acceleration.x+ " Location:"+ location.x+ " Velocity:"+ velocity.x+ " motorForce: "+ motorForce.x);
        if (Math.abs(location.x) > 10.0) {
            if(velocity.x < 0){
                setReversed(true);
            }else{
                setReversed(false);
            }
            advance();
            location.set(0);
        }
        acceleration.set(0);
    }

    @Override
    public void setMotorInverted(boolean inverted) {
        getDirectorLinker().setMotorInverted(inverted);
    }

    @Override
    public boolean isMotorInverted() {
        return getDirectorLinker().isMotorInverted();
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
    public void setReversed(boolean reversed) {
        getTractors()
            .stream()
            .forEach(t-> t.setReversed(reversed));

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
        return getTractorsForce();
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

    @Override
    public float getAcceleration() {
        return this.acceleration.x;
    }

    @Override
    public void setAcceleration(float speed) {
        this.acceleration.x = speed;
    }

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }

    public boolean isBrakesActivated() {
        return brakesActivated;
    }

    public void setBrakesActivated(boolean brakesActivated) {
        this.brakesActivated = brakesActivated;
    }

}
