package letrain.track.rail;

import letrain.view.Renderer;

public class TrainFactoryRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Renderer renderer) {
        renderer.renderTrainFactoryRailTrack(this);
    }
}
