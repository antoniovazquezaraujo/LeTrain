package letrain.track.rail;

import letrain.render.Visitor;

public class StopRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStopRailTrack(this);
    }
}
