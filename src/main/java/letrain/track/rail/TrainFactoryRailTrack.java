package letrain.track.rail;

import letrain.render.Visitor;

public class TrainFactoryRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTrainFactoryRailTrack(this);
    }
}
