package letrain.track.rail;

import javafx.util.Pair;
import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.ForkRouter;
import letrain.visitor.Visitor;

import java.util.function.Consumer;

public class ForkRailTrack extends RailTrack implements DynamicRouter {

    @Override
    public DynamicRouter getRouter() {
        if(router == null){
            router = new ForkRouter();
        }
        return (DynamicRouter) router;
    }

    @Override
    public String toString() {
        String ret = "";
        ret = getRouter().toString();
        return ret;
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
        getRouter().setAlternativeRoute();
    }

    @Override
    public void setNormalRoute() {
        getRouter().setNormalRoute();
    }

    @Override
    public boolean flipRoute() {
        return getRouter().flipRoute();
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
    public Dir getDirWhenEnteringFrom(Dir dir) {
        return getRouter().getDirWhenEnteringFrom(dir);
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
