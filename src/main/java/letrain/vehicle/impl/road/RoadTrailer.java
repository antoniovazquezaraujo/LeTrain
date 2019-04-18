package letrain.vehicle.impl.road;

import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;

import java.util.Deque;
import java.util.List;

public class RoadTrailer implements Trailer{
    @Override
    public Deque<Linker> getLinkers() {
        return null;
    }

    @Override
    public void pushFront(Linker linker) {

    }

    @Override
    public Linker popFront() {
        return null;
    }

    @Override
    public Linker getFront() {
        return null;
    }

    @Override
    public void pushBack(Linker linker) {

    }

    @Override
    public Linker popBack() {
        return null;
    }

    @Override
    public Linker getBack() {
        return null;
    }

    @Override
    public Tractor getMainTractor() {
        return null;
    }

    @Override
    public List<Tractor> getTractors() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void setMainTractor(Tractor tractor) {

    }

    @Override
    public float getTractorsForce() {
        return 0;
    }

    @Override
    public float getFrictionForce() {
        return 0;
    }

    @Override
    public float getTotalForce() {
        return 0;
    }

    @Override
    public float getTotalMass() {
        return 0;
    }

    @Override
    public void applyForces() {

    }

    @Override
    public void move() {

    }

    @Override
    public float getAcceleration() {
        return 0;
    }

    @Override
    public float getSpeed() {
        return 0;
    }

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }

    @Override
    public Trailer divide(Linker p) {
        return null;
    }

    @Override
    public void joinTrailerBack(Trailer t){

    }

    @Override
    public void joinTrailerFront(Trailer t) {

    }
}
