package letrain.track.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.impl.ForkRouter;
import letrain.track.ForkEventListener;
import letrain.utils.Pair;
import letrain.visitor.Visitor;

public class ForkRailTrack extends RailTrack implements DynamicRouter {

    int id;
    private letrain.map.Dir creationDir = letrain.map.Dir.E;
    private List<ForkEventListener> listeners = new ArrayList<>();

    public void addForkEventListener(ForkEventListener listener) {
        listeners.add(listener);
    }

    public void removeForkEventListener(ForkEventListener listener) {
        listeners.remove(listener);
    }

    public void removeAllForkEventListeners() {
        listeners.clear();
    }

    public ForkRailTrack(int id) {
        setId(id);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train) {
        onEnterTrain(train, calculateIsForward(train));
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        for (ForkEventListener listener : listeners) {
            listener.onEnterTrain(train, isForward);
        }
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train) {
        onExitTrain(train, calculateIsForward(train));
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        for (ForkEventListener listener : listeners) {
            listener.onExitTrain(train, isForward);
        }
    }

    private boolean calculateIsForward(letrain.vehicle.impl.rail.Train train) {
        if (train.getDirectorLinker() == null) {
            return true;
        }
        letrain.map.Dir realDir = train.getDirectorLinker().getRealDir();
        if (getOriginalRoute() == null) {
            return true;
        }
        letrain.map.Dir root = getOriginalRoute().getKey();
        // Forward is divergent (moving AWAY from root)
        return realDir != root;
    }

    public letrain.map.Dir getCreationDir() {
        return creationDir;
    }

    public void setCreationDir(letrain.map.Dir creationDir) {
        this.creationDir = creationDir;
    }

    @Override
    public DynamicRouter getRouter() {
        if (router == null) {
            router = new ForkRouter();
        }
        return (DynamicRouter) router;
    }

    /***********************************************************
     * Router implementation
     **********************************************************/

    @Override
    public int getNumRoutes() {
        return 3;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitForkRailTrack(this);
    }

    @Override
    public void setAlternativeRoute() {
        boolean changed = !getRouter().isUsingAlternativeRoute();
        getRouter().setAlternativeRoute();
        if (changed) {
            for (ForkEventListener listener : listeners) {
                listener.onDirectionChanged(false);
            }
        }
    }

    @Override
    public void setNormalRoute() {
        boolean changed = getRouter().isUsingAlternativeRoute();
        getRouter().setNormalRoute();
        if (changed) {
            for (ForkEventListener listener : listeners) {
                listener.onDirectionChanged(true);
            }
        }
    }

    @Override
    public boolean flipRoute() {
        boolean ret = getRouter().flipRoute();
        for (ForkEventListener listener : listeners) {
            listener.onDirectionChanged(!getRouter().isUsingAlternativeRoute());
        }
        return ret;
    }

    @Override
    public boolean isUsingAlternativeRoute() {
        return getRouter().isUsingAlternativeRoute();
    }

    @Override
    public Pair<Dir, Dir> getAlternativeRoute() {
        return getRouter().getAlternativeRoute();
    }

    @Override
    public Pair<Dir, Dir> getOriginalRoute() {
        return getRouter().getOriginalRoute();
    }

    @Override
    public Dir getAnyDir() {
        return getRouter().getAnyDir();
    }

    @Override
    public boolean isStraight() {
        return getRouter().isStraight();
    }

    @Override
    public boolean isCurve() {
        return getRouter().isCurve();
    }

    @Override
    public boolean isCross() {
        return getRouter().isCross();
    }

    @Override
    public Dir getDir(Dir dir) {
        return getRouter().getDir(dir);
    }

    @Override
    public Dir getFirstOpenDir() {
        return getRouter().getFirstOpenDir();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        getRouter().addRoute(from, to);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        getRouter().removeRoute(from, to);
    }

    @Override
    public void clear() {
        getRouter().clear();
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        getRouter().forEach(routeConsumer);
    }
}
