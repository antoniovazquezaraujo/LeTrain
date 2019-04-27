package letrain.track.rail;

import letrain.render.Visitor;

public class TunnelRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitTunnelRailTrack(this);
    }
}
