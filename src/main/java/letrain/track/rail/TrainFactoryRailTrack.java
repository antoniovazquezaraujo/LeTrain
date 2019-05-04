package letrain.track.rail;

import letrain.visitor.Visitor;

public class TrainFactoryRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTrainFactoryRailTrack(this);
    }
}
