package letrain.track.rail;

import javafx.util.Pair;
import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.ForkRouter;
import letrain.map.Router;
import letrain.render.Renderer;
import letrain.track.rail.RailTrack;

import java.util.function.Consumer;

public class ForkRailTrack extends RailTrack implements DynamicRouter {
    private final DynamicRouter router = new ForkRouter();

    @Override
    public Router getRouter() {
        return router;
    }

    /***********************************************************
     * Router implementation
     **********************************************************/

    @Override
    public int getNumRoutes() {
        return 3;
    }

    @Override
    public void accept(Renderer renderer) {
        renderer.renderForkRailTrack(this);
    }
    @Override
    public void setAlternativeRoute() {
        router.setAlternativeRoute();
    }

    @Override
    public void setNormalRoute() {
        router.setNormalRoute();
    }

    @Override
    public boolean flipRoute() {
        return router.flipRoute();
    }
    @Override
    public boolean isUsingAlternativeRoute() {
        return router.isUsingAlternativeRoute();
    }
    @Override
    public Dir getAnyDir() {
        return router.getAnyDir();
    }

    @Override
    public boolean isStraight() {
        return router.isStraight();
    }

    @Override
    public boolean isCurve() {
        return router.isCurve();
    }

    @Override
    public boolean isCross() {
        return router.isCross();
    }

    @Override
    public Dir getDir(Dir dir) {
        return router.getDir(dir);
    }

    @Override
    public Dir getFirstOpenDir() {
        return router.getFirstOpenDir();
    }


    @Override
    public void addRoute(Dir from, Dir to) {
        router.addRoute(from, to);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        router.removeRoute(from, to);
    }

    @Override
    public void clear() {
        router.clear();
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        router.forEach(routeConsumer);
    }
}
