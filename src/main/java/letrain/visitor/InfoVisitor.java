package letrain.visitor;

import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.impl.RailMap;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.View;
import letrain.track.Station;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public class InfoVisitor implements Visitor {

    String infoBarText = "";
    String helpBarText = "";
    private final View view;

    public InfoVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(Model model) {
        infoBarText = "";
        switch (model.getMode()) {
            case RAILS:
                Point pos = model.getCursor().getPosition();
                RailTrack track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());
                if (track != null) {
                    visitRailTrack(track);
                }
                break;
            case DRIVE:
                Locomotive locomotive = model.getSelectedLocomotive();
                if (locomotive != null) {
                    visitLocomotive(locomotive);
                }
                break;
            case FORKS:
                ForkRailTrack fork = model.getSelectedFork();
                if (fork != null) {
                    visitForkRailTrack(fork);
                }
                break;
            case STATIONS:
                Station Station = model.getSelectedStation();
                if (Station != null) {
                    visitStation(Station);
                }
                break;
            case LOAD_TRAINS:
                break;
            case TRAINS:
                break;
        }
        visitCursor(model.getCursor());
        String[] menuOptions = { "&rails", "&drive", "&forks", "&semaphores", "&trains", "&link",
                "&unlink", "statio&ns" };

        visitEconomyManager(model.getEconomyManager());
        view.setMenu(menuOptions, model.getMode().ordinal() - 1);
        view.setHelpBarText(getModeHelp(model.getMode()));
        view.setInfoBarText(infoBarText);
    }

    private String getModeHelp(GameMode mode) {
        String ret = "";
        switch (mode) {
            case MENU:
                ret += "escape:exit ";
                break;
            case RAILS:
                ret += "<:left >:right ^:forwd v:backwd shift+^:rail ctrl+^:del insert:add sensor delete:delete sensor home:insert semaphore end:delete semaphore";
                break;
            case DRIVE:
                ret += "<:prev >:next ^:accel v:decel space:reverse (pgup, pgdn, ctrl+pgup, ctrl+pgdn):move map";
                break;
            case FORKS:
                ret += "<:prev >:next space:toggle #:select";
                break;
            case SEMAPHORES:
                ret += "<:prev >:next space:toggle #:select";
                break;
            case TRAINS:
                ret += "A-Z:locomotive a-z:wagon enter:end";
                break;
            case LINK:
                ret += "^:front v:back space:link";
                break;
            case UNLINK:
                ret += "<:front >:back ^:add v:del space:unlink";
                break;
            case STATIONS:
                ret += "<:prev >:next space:toggle #:select";
                break;
        }
        return ret;
    }

    @Override
    public void visitRailMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        // infoBarText += "Track:{" + track + "}\n";
    }

    private String getRouterAspect(Router router) {
        StringBuffer ret = new StringBuffer();
        router.forEach(t -> {
            ret.append("(" + t.getKey() + ">" + t.getValue() + ") ");
        });
        return ret.toString();
    }

    private String getTrackConnectionsAspect(RailTrack track) {
        StringBuffer ret = new StringBuffer();
        for (Dir d : Dir.values()) {
            Track connected = track.getConnected(d);
            if (connected != null) {
                ret.append("(" + d + "->" + connected + ")");
            }
        }
        return ret.toString();
    }


    @Override
    public void visitForkRailTrack(ForkRailTrack track) {
        infoBarText += "Fork:{" + track.getId() + " Dir:"
                + (track.isUsingAlternativeRoute() ? track.getAlternativeRoute() : track.getOriginalRoute()) + "}\n";
    }

    private String getDynamicRouterAspect(DynamicRouter router) {
        StringBuffer ret = new StringBuffer();
        router.forEach(t -> {
            if (t.getValue() != null) {
                ret.append("(" + t.getKey() + ">" + t.getValue() + ") ");
            }
        });
        ret.append("\nNorm:" + router.getOriginalRoute() + " Alt:" + router.getAlternativeRoute() + " Using Alt:"
                + (router.isUsingAlternativeRoute() ? "TRUE" : "FALSE"));
        return ret.toString();
    }

    @Override
    public void visitTrainFactoryRailTrack(TrainFactoryRailTrack track) {
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        infoBarText += "Track:[" + track.getPosition().getX() + "," + track.getPosition().getY() + "]"
                + getRouterAspect(track.getRouter()) + "\n";
        infoBarText += "Connect:...";
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        infoBarText += " Speed:" + locomotive.getSpeed() + " ";
    }

    @Override
    public void visitLinker(Linker linker) {

    }

    @Override
    public void visitWagon(Wagon wagon) {
        infoBarText += "Wagon:" + wagon.getAspect() + " Dir" + wagon.getDir() + "\n";
    }

    @Override
    public void visitCursor(Cursor cursor) {
        infoBarText += "Cursor:[" + cursor.getPosition().getX() + "," + cursor.getPosition().getY() + "]";
    }

    @Override
    public void visitSensor(Sensor sensor) {
        infoBarText += "Sensor:[" + sensor.getId() + "]" + "\n";
    }

    @Override
    public void visitSemaphore(RailSemaphore semaphore) {
        infoBarText += "Semaphore:[" + semaphore.getId() + ":" + (semaphore.isOpen() ? "open" : "closed") + "]" + "\n";
    }

    @Override
    public void visitStation(Station Station) {
        infoBarText += "Station:[" + Station.getId() + "]" + "\n" + "Position:" + Station.getPosition() + "\n";
    }

	@Override
	public void visitGroundMap(GroundMap groundMap) {
	}

	@Override
	public void visitGround(Ground ground) {
	}

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitBridgeGateRailTrack'");
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitBridgeRailTrack'");
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitTunnelGateRailTrack'");
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
        String info = "$: " + economyManager.getBalance() + " ";
        infoBarText += info;
    }

}
