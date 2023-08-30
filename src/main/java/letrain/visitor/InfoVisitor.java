package letrain.visitor;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.Router;
import letrain.map.impl.RailMap;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public class InfoVisitor implements Visitor {

    static final TextColor NORMAL_MENU_FG_COLOR = ANSI.WHITE;
    static final TextColor NORMAL_MENU_BG_COLOR = ANSI.BLACK;
    static final TextColor DISABLED_FG_COLOR = ANSI.YELLOW;
    static final TextColor SELECTED_FG_COLOR = ANSI.BLUE;
    static final TextColor SHORTCUT_COLOR = ANSI.YELLOW;

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
                Station station = model.getSelectedStation();
                if (station != null) {
                    visitStation(station);
                }
                break;
            case LOAD_TRAINS:
                break;
            case TRAINS:
                break;
            case PERSIST:
                visitPersistence(model);
                break;
        }

        view.setMenu(model.getMenuModel());
        String commonText = getCommonInfoBarText(model);
        int fillSpaces = view.getCols() - (infoBarText.length());
        commonText = String.format("%" + fillSpaces + "s", commonText);
        view.setInfoBarText(infoBarText + commonText);
    }

    private void visitPersistence(Model model) {
        infoBarText += model.getLastSaveTime() != null ? "Last save: " + model.getLastSaveTime() : "Not saved";
    }

    public String getCommonInfoBarText(Model model) {
        return "| Pag " + view.getMapScrollPage() +
                "| Cursor " + model.getCursor().getPosition() +
                "| Steps " + model.getQuantifierSteps() + "/" + model.getQuantifier() +
                "| Balance " + model.getEconomyManager().getBalance() +
                "|";
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
        infoBarText += "Fork " + track.getId() + " Dirs "
                + (track.isUsingAlternativeRoute() ? track.getAlternativeRoute() : track.getOriginalRoute());
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
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        infoBarText += "Track:[" + track.getPosition().getX() + "," + track.getPosition().getY() + "]"
                + getRouterAspect(track.getRouter()) + "\n";
        infoBarText += "Connect:...";
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        infoBarText += "Train " + locomotive.getId() + " Speed " + locomotive.getSpeed() + " Wagons "
                + (locomotive.getTrain().getLinkers().size() - 1) + (locomotive.isReversed() ? " Reversed" : "");
    }

    @Override
    public void visitWagon(Wagon wagon) {
        infoBarText += "Wagon:" + wagon.getAspect() + " Dir" + wagon.getDir();
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

    @Override
    public void visitCursor(Cursor cursor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitCursor'");
    }

}
