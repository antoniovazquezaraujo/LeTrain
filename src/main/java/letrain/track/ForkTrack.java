package letrain.track;

import letrain.map.DynamicRouter;
import letrain.map.ForkRouter;
import letrain.render.Renderer;
import letrain.track.rail.RailTrack;

public class ForkTrack extends RailTrack implements DynamicRouter {
    private final DynamicRouter router = new ForkRouter();

    @Override
    public int getNumRoutes() {
        return 3;
    }

    @Override
    public void accept(Renderer renderer) {

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

}
