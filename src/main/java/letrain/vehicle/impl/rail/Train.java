package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.physics.Vector2D;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.AbstractVehicle;
import letrain.vehicle.Braker;
import letrain.vehicle.Motorized;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Train extends AbstractVehicle implements  Trailer<RailTrack>, Motorized , Braker {
    protected static final double DISTANCE_UNIT = 50;
    private static final double MAX_VELOCITY = 8f;
    public static final double BRAKES_COEFICIENT = 0.5f;
    public static final double FRICTION_COEFICIENT = 0.01f;
    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected Motorized directorLinker;
    private static final Logger log = LoggerFactory.getLogger(Train.class);
    boolean reversed = false;

    protected boolean brakesActivated = false;
    private Linker externalPushedLinker;

    public Train() {
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
    }

    @Override
    public Deque<Linker> getLinkers() {
        return linkers;
    }
    @Override
    public void pushFront(Linker linker) {
        Trailer.super.pushFront(linker);
        assignDefaultDirectorLinker();
        linker.setTrain(this);
    }
    @Override
    public  Linker popFront() {
        Linker linker = Trailer.super.popFront();
        assignDefaultDirectorLinker();
        return linker;
    }


    @Override
    public void pushBack(Linker linker) {
        Trailer.super.pushBack(linker);
        linker.setTrain(this);
        assignDefaultDirectorLinker();
    }
    @Override
    public  Linker popBack() {
        Linker linker = Trailer.super.popBack();
        assignDefaultDirectorLinker();
        linker.setTrain(null);
        return linker;
    }

    public void assignDefaultDirectorLinker() {
        setDirectorLinker(getMotorizedVehicles() != null && !getMotorizedVehicles().isEmpty() ? (Motorized) getMotorizedVehicles().get(0) : null);
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
    public Trailer divide(Linker p) {
        Trailer<RailTrack> ret = Trailer.super.divide(p);
        assignDefaultDirectorLinker();
        return ret;
    }
    @Override
    public void setDirectorLinker(Motorized linker) {
        this.directorLinker = linker;
    }

    @Override
    public Motorized getDirectorLinker() {
        return directorLinker;
    }


    /***********************************************************
     * Transportable implementation
     **********************************************************/

//    @Override
//    public double getDistanceTraveled() {
//        return 0;
//    }
//
//    @Override
//    public void resetDistanceTraveled() {
//
//    }

    @Override
    public double getMass() {
        return (double) getLinkers()
                .stream()
                .mapToDouble(t -> {
                    return t.getMass();
                })
                .sum();
    }

    @Override
    public void incMotorForce(double value) {
        getMotorizedVehicles().stream().forEach(t->t.incMotorForce(value));
    }

    @Override
    public void decMotorForce(double value) {
        getMotorizedVehicles().stream().forEach(t->t.decMotorForce(value));
    }

    @Override
    public void setMotorForce(double value) {
        getMotorizedVehicles().stream().forEach(t->t.setMotorForce(value));
    }

    @Override
    public double getMotorForce() {
        return getMotorizedVehicles().stream().mapToDouble(t->t.getMotorForce()).sum();
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
    public double getBrakes() {
        return getLinkers().stream().mapToDouble(t -> t.getBrakes()).sum();
    }

    @Override
    public void setBrakes(double i) {
        getLinkers().forEach(t -> t.setBrakes(i));
    }

    @Override
    public void activateBrakes(boolean active) {
        getLinkers().forEach(t -> t.activateBrakes(active));
        this.brakesActivated=active;
    }

    @Override
    public boolean isBrakesActivated() {
        return this.brakesActivated;
    }

//    @Override
//    public void move() {
//        if (applyForces()) {
//            Track t = moveLinkers();
//            if(t==null) {
//                location.set(0);
//            }else{
////                velocity.set(0);
//            }
//            return t;
//        }
//        acceleration.set(0);
//        return null;
//    }
    public void beginStep() {

    }


    public void endStep() {
        if (getDistanceTraveledInStep() > 0) {
            setDistanceTraveledInStep(0.0);
            move();
        }
    }


    public void move() {
        Locomotive master = (Locomotive)getDirectorLinker();
        master.move(master.getPosition2D(), master.getVelocity2D());
    }
    public static void move(Vector2D from, Vector2D movement) {
        Dir dir = movement.toDir();
        int hor = 0;
        int ver = 0;
        switch (dir) {
            case E:
                hor++;
                break;
            case NE:
                hor++;
                ver--;
                break;
            case N:
                ver--;
                break;
            case NW:
                ver--;
                hor--;
                break;
            case W:
                hor--;
                break;
            case SW:
                hor--;
                ver++;
                break;
            case S:
                ver++;
                break;
            case SE:
                ver++;
                hor++;
        }
        from.setX(from.x + hor);
        from.setY(from.y + ver);
        from.round();
    }

//    public void applyForces() {
//        double prevVelocitySign = Math.signum(velocity.x);
//        addForce(externalForce);
//        externalForce.set(0);
//        motorForce.set(getTractorsForce());
//        addForce(motorForce);
//        applyFriction();
//        applyBrakes();
//        velocity.add(acceleration);
////        if(limitVelocity()){
//            location.add(velocity);
////        }
////        return Math.abs(location.x) > 10.0;
//    }

//    private void reverseIfChangedSense(){
//        if((getVelocity() < 0 && !isReversed() )){
//            reverse(true);
//            velocity.mult(-1);
////            acceleration.set(0);
//        }else if (getVelocity() > 0 && isReversed()){
//            reverse(false);
//            velocity.mult(-1);
////            acceleration.set(0);
//        }
//    }
//    private boolean limitVelocity() {
//        if (getVelocity() < 0 && !isReversed() || getVelocity() > 0 && isReversed()) {
//            velocity.set(0);
//            acceleration.set(0);
//            return false;
//        } else {
//            velocity.limit(MAX_VELOCITY);
//            return true;
//        }
//    }

    private Track moveLinkers() {
        Iterator<Linker> iterator = iterator();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            next.move();
//            if ( crashedTrack != null) {
//                if(crashedTrack.getLinker()!=null) {
//                    crash(next, crashedTrack);
//                }else{
//                    System.out.println("No hay salida por esta vía!!!!!");
//                }
//                return crashedTrack;
//            }
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

//    public double getExternalForce() {
//        return externalForce.x;
//    }

//    @Override
//    public double getForce() {
//        return getTractorsForce();
//    }

//    public double getLocation() {
//        return location.x;
//    }

//    @Override
//    public void setForce(double force) {
//        getMotorizedVehicles()
//                .forEach(t -> t.setForce(force));
//    }
//
//    @Override
//    public void incForce() {
//        incForce(1);
//    }
//
//    @Override
//    public void incForce(double force) {
//        getMotorizedVehicles()
//                .forEach(t -> t.incForce(force));
//    }

//    @Override
//    public void decForce() {
//        decForce(1);
//    }
//
//    @Override
//    public void decForce(double force) {
//        getMotorizedVehicles()
//                .forEach(t -> t.decForce(force));
//    }

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
//    public double getVelocity() {
//        return velocity.x;
//    }
//
//    public void addForce(UVector force) {
//        acceleration.add(UVector.div(force, getMass()));
//    }
//
//    public void applyFriction() {
//        addForce(new UVector(getVelocity() * getMass() * FRICTION_COEFICIENT * -1.0f));
//    }
//
//    public void applyBrakes() {
//        addForce(new UVector(getVelocity()* getBrakes() * BRAKES_COEFICIENT * -1.0f));
//    }
//
//    public double getTractorsForce() {
//        return (double) getMotorizedVehicles()
//                .stream()
//                .mapToDouble(Tractor::getForce)
//                .sum();
//    }
//
//
//    public boolean isBrakesActivated() {
//        return brakesActivated;
//    }
//
//    public void setBrakesActivated(boolean brakesActivated) {
//        this.brakesActivated = brakesActivated;
//    }
//
//
//    private void crash(Linker crasherLinker, Track track) {
//        Train crashedTrain = track.getLinker().getTrain();
//        double transmittedForce = Math.abs(getMass() * getAcceleration());
//        this.applyExternalForce(transmittedForce/2 *-1);
//        if(!isAPush(crasherLinker, track.getLinker())) {
//            transmittedForce*=-1;
//        }
//        crashedTrain.applyExternalForce(transmittedForce/2);
//        System.out.println("Transmitiendo fuerza :" + transmittedForce + " a " + crasherLinker);
//    }
//
//    private void applyExternalForce(double amount) {
//        externalForce.set(amount);
//        UVector f = UVector.div(externalForce, getMass());
//        acceleration.add(f);
//    }
//
//    public boolean isAPush(Linker me, Linker you){
//        Dir myDir = me.isReversed()?me.getDir().inverse():me.getDir();
//        Dir yourDir = you.isReversed()?you.getDir().inverse():you.getDir();
//        int angularDistance = Math.abs(myDir.angularDistance(yourDir));
//        if(angularDistance <= 2 ||  angularDistance>= 6) {
//            return true;
//        }
//        return false;
//    }

}
