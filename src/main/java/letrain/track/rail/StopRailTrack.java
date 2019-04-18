package letrain.track.rail;

import letrain.view.Renderer;

public class StopRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderStopRailTrack(this);
    }
}
