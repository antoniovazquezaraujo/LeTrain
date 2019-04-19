package letrain.track.rail;

import letrain.render.Renderer;

public class TunnelRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderTunnelRailTrack(this);
    }
}
