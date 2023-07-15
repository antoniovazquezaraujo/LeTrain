package letrain.track.rail;

import letrain.visitor.Visitor;

public class BridgeGateRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitBridgeGateRailTrack(this);
    }
}
