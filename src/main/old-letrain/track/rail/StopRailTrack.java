package letrain.track.rail;

import letrain.visitor.Visitor;

public class StopRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStopRailTrack(this);
    }
}
