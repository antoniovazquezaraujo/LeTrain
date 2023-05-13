package letrain.track.rail;

import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.ForkRouter;
import letrain.utils.Pair;
import letrain.visitor.Visitor;

public class ForkRailTrack extends RailTrack implements DynamicRouter {

    private static int numForksCreated = 0;

    int id;

    public ForkRailTrack() {
        super();
        setId(++numForksCreated);
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
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
