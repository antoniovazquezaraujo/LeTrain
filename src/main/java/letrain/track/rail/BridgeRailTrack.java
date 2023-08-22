package letrain.track.rail;

import letrain.visitor.Visitor;

public class BridgeRailTrack extends RailTrack {
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitBridgeRailTrack(this);
    }
}
