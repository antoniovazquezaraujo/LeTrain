package letrain.track.rail;

import letrain.visitor.Visitor;

public class TunnelGateRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTunnelGateRailTrack(this);
    }
}
