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
    public void joinTrailerBack(Trailer t) {

    }

    @Override
    public void joinTrailerFront(Trailer t) {

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
    public Linker getDirectorLinker() {
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
    public void setDirectorLinker(Linker linker) {

    }

    @Override
    public float getFrictionForce() {
        return 0;
    }

    @Override
    public Trailer divide(Linker p) {
        return null;
    }

}
