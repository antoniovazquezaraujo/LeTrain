package letrain.track.rail;

import letrain.render.Renderer;

public class TrainFactoryRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderTrainFactoryRailTrack(this);
    }
}
