package letrain.track.rail;

import letrain.visitor.Visitor;

public class TunnelRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTunnelRailTrack(this);
    }
}
