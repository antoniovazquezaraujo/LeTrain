package letrain.vehicle.impl.rail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Train implements Serializable, Trailer<RailTrack>, Renderable, Transportable {

    protected final Deque<Linker> linkers;
    protected final List<Tractor> tractors;
    protected final Deque<Linker> linkersToJoin;

    enum LinkersJoinSense {
        FRONT, BACK
    };

    LinkersJoinSense linkerJoinSense;
    boolean joined = false;
    protected Tractor directorLinker;

    public Train() {
        this.linkers = new LinkedList<>();
        this.tractors = new ArrayList<>();
        this.linkersToJoin = new LinkedList<>();
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
        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }

        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
        return moveLinkers(normalSense);
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
        System.out.println("Choque de :" + linker);
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

    public void setLinkersToJoin(boolean forwardDirection) {
        linkersToJoin.clear();
        Linker lastLinker = null;
        Dir dir;
        if (forwardDirection) {
            lastLinker = getLinkers().getFirst();
            dir = lastLinker.getDir();
            linkerJoinSense = LinkersJoinSense.FRONT;
        } else {
            lastLinker = getLinkers().getLast();
            dir = lastLinker.getDir().inverse();
            linkerJoinSense = LinkersJoinSense.BACK;
        }

        Track track = lastLinker.getTrack();
        Track nextTrack = track.getConnected(dir);
        if (nextTrack.getLinker() != null && nextTrack.getLinker().getTrain() == this) {
            dir = dir.inverse();
            nextTrack = track.getConnected(dir);
        }
        RailIterator iterator = new RailIterator(nextTrack, dir);
        Linker nextLinker = iterator.getTrack().getLinker();
        while (nextLinker != null) {
            linkersToJoin.add(nextLinker);
            iterator.advance();
            nextLinker = iterator.getTrack().getLinker();
        }
    }

    public void toggleLinkersToJoin() {
        if (!joined) {
            if (linkerJoinSense == LinkersJoinSense.FRONT) {
                for (Linker linker : linkersToJoin) {
                    pushFront(linker);
                }
            } else {
                for (Linker linker : linkersToJoin) {
                    pushBack(linker);
                }
            }
            linkersToJoin.clear();
        } else {
            if (linkerJoinSense == LinkersJoinSense.FRONT) {
                for (Linker linker : linkers) {
                    linkersToJoin.addLast(popFront());
                }
            } else {
                for (Linker linker : linkers) {
                    linkersToJoin.addFirst(popBack());
                }
            }
        }
    }
}
