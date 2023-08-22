package letrain.visitor;

import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.impl.RailMap;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public interface Visitor {
    void visitEconomyManager(EconomyManager economyManager);

    void visitModel(Model model);

    void visitRailMap(RailMap map);

    void visitRailTrack(RailTrack track);

    void visitForkRailTrack(ForkRailTrack track);

    void visitTunnelRailTrack(TunnelRailTrack track);

    void visitLocomotive(Locomotive locomotive);

    void visitWagon(Wagon wagon);

    void visitCursor(Cursor cursor);

    void visitSensor(Sensor sensor);

    void visitSemaphore(RailSemaphore semaphore);

    void visitStation(Station Station);

    void visitGroundMap(GroundMap groundMap);

    void visitGround(Ground ground);

    void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack);

    void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack);

    void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack);
}
