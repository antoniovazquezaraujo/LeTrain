package letrain.visitor.terminal;

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
import letrain.mvp.impl.terminal.TerminalView;
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
import letrain.visitor.Visitor;

public class InfoVisitor implements Visitor {

    static final TextColor NORMAL_MENU_FG_COLOR = ANSI.WHITE;
    static final TextColor NORMAL_MENU_BG_COLOR = ANSI.BLACK;
    static final TextColor DISABLED_FG_COLOR = ANSI.YELLOW;
    static final TextColor SELECTED_FG_COLOR = ANSI.BLUE;
    static final TextColor SHORTCUT_COLOR = ANSI.YELLOW;

    String infoBarText = "";
    String helpBarText = "";
    private final TerminalView view;

    public InfoVisitor(TerminalView view) {
        this.view = view;
    }

    @Override
    public void visitModel(Model model) {
        infoBarText = "";
        switch (model.getMode()) {
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
            case SEMAPHORES:
                RailSemaphore semaphore = model.getSelectedSemaphore();
                if (semaphore != null) {
                    visitSemaphore(semaphore);
                }
                break;
            case STATIONS:
                Station station = model.getSelectedStation();
                if (station != null) {
                    visitStation(station);
                }
                break;
            case LINK:
                infoBarText += "Modo: VINCULAR (LINK) [Arriba/Abajo]: Sentido [Izqu/Der]: Cantidad [Espacio]: Vincular";
                Locomotive selected = model.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    infoBarText += " Vagones: " + selected.getTrain().getSelectedLinkersToJoin().size() + "/"
                            + selected.getTrain().getLinkersToJoin().size();
                }
                break;
            case UNLINK:
                infoBarText += "Modo: DESVINCULAR (UNLINK) [Flechas]: Seleccionar [Espacio]: Desvincular";
                break;
            case MENU:
                infoBarText += "Menu Principal";
                break;
            case PROGRAM:
                visitProgram(model);
                break;
            case RAILS:
            case TRAINS:
            case LOAD_TRAINS:
            default:
                break;
        }

        view.setMenu(model.getMenuModel());

        StringBuilder richInfo = new StringBuilder();
        // Row 3: Finances
        EconomyManager economy = model.getEconomyManager();
        richInfo.append(String.format("$: %.2f | Ingresos(+): %.2f | Gastos(-): %.2f\n",
                economy.getBalance(), economy.getTotalIncome(), economy.getTotalExpenses()));

        // Row 4: Vehicle Status / Mode Help fallback
        Locomotive selectedLoco = model.getSelectedLocomotive();
        if (selectedLoco != null) {
            String notchBar = getNotchBar(selectedLoco.getSpeed(), selectedLoco.getTargetSpeed(), 10);
            richInfo.append(String.format("Notch: %s | Vagones: %d\n",
                    notchBar, selectedLoco.getTrain().getLinkers().size() - 1));
        } else {
            richInfo.append(infoBarText).append("\n");
        }

        // Row 5: System info
        String commonText = getCommonInfoBarText(model);
        String lastSave = model.getLastSaveTime() != null ? " | Guardado: " + model.getLastSaveTime().toString().substring(11, 16) : "";
        richInfo.append(commonText).append(lastSave).append("\n");

        // Row 6: Global Help
        richInfo.append("[PgUp/PgDn]: Scroll Mapa | [r/d/f/s/t/l/u/p/n]: Modos | [Esc]: Salir");

        view.setInfoBarText(richInfo.toString());
    }

    private String getNotchBar(int current, int target, int max) {
        StringBuilder bar = new StringBuilder("[");
        for (int i = 1; i <= max; i++) {
            char c = ' ';
            if (i <= current) c = '=';
            if (i == target) c = '!';
            bar.append(c);
        }
        bar.append("]");
        return bar.toString();
    }

    private void visitProgram(Model model) {
        infoBarText += model.getLastSaveTime() != null ? "Last save: " + model.getLastSaveTime() : "Not saved";
    }

    public String getCommonInfoBarText(Model model) {
        return "| Pag " + view.getMapScrollPage() +
                "| Cursor " + model.getCursor().getPosition() +
                "| Steps " + model.getQuantifierSteps() + "/" + model.getQuantifier() +
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
        for (Dir dir : Dir.values()) {
            Track connected = track.getConnected(dir);
            if (connected != null) {
                ret.append("(" + dir + "->" + connected + ")");
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
        String speedStr = String.valueOf(locomotive.getSpeed());
        if (locomotive.getSpeed() != locomotive.getTargetSpeed()) {
            speedStr += "->" + locomotive.getTargetSpeed();
        }
        infoBarText += "Train " + locomotive.getId() + " Speed " + speedStr + " Wagons "
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
    public void visitStation(Station station) {
        infoBarText += "Station:[" + station.getId() + "]" + "\n" + "Position:" + station.getPosition() + "\n";
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {
    }

    @Override
    public void visitGround(Ground ground) {
    }

    @Override
    public void visitBridgeGateRailTrack(BridgeGateRailTrack bridgeGateRailTrack) {
        // No extra info in terminal mode
    }

    @Override
    public void visitBridgeRailTrack(BridgeRailTrack bridgeRailTrack) {
        // No extra info in terminal mode
    }

    @Override
    public void visitTunnelGateRailTrack(TunnelGateRailTrack tunnelGateRailTrack) {
        // No extra info in terminal mode
    }

    @Override
    public void visitEconomyManager(EconomyManager economyManager) {
        String info = "$: " + economyManager.getBalance() + " ";
        infoBarText += info;
    }

    @Override
    public void visitCursor(Cursor cursor) {
        // No extra info in terminal mode
    }

}
