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
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
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
                infoBarText +=
                        "Mode: LINK [Up/Down]: Direction [Left/Right]: Quantity [Space]: Link";
                Locomotive selected = model.getSelectedLocomotive();
                if (selected != null && selected.getTrain() != null) {
                    Train train = selected.getTrain();
                    infoBarText += " Wagons: "
                            + train.getTrainCouplingManager().getSelectedLinkersToJoin(train).size()
                            + "/" + train.getLinkersToJoin().size();
                }
                break;
            case UNLINK:
                infoBarText += "Mode: UNLINK [Arrows]: Select [Space]: Unlink";
                break;
            case MENU:
                infoBarText += "Main Menu";
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

        // Row 3: Vehicle text (left) + System info (right)
        String vehicleText = "";
        Locomotive selectedLoco = model.getSelectedLocomotive();
        if (selectedLoco != null) {
            int trainId = (selectedLoco.getTrain() != null) ? selectedLoco.getTrain().getId()
                    : selectedLoco.getId();
            String notchBar =
                    getNotchBar(selectedLoco.getSpeed(), selectedLoco.getTargetSpeed(), 10);
            String speedStr = String.valueOf(selectedLoco.getSpeed());
            if (selectedLoco.getSpeed() != selectedLoco.getTargetSpeed()) {
                speedStr += "->" + selectedLoco.getTargetSpeed();
            }
            int wagonsCount = (selectedLoco.getTrain() != null
                    && selectedLoco.getTrain().getLinkers() != null)
                            ? Math.max(0, selectedLoco.getTrain().getLinkers().size() - 1)
                            : 0;
            vehicleText = String.format("Train: %d | Notch: %s | Speed: %s | Wagons: %d%s", trainId,
                    notchBar, speedStr, wagonsCount, selectedLoco.isReversed() ? " (Rev)" : "");
        } else if (infoBarText != null) {
            vehicleText = infoBarText;
        }

        int totalWidth = view != null ? Math.max(40, view.getCols() - 2) : 80;

        String page =
                view != null ? view.getMapScrollPage().getX() + "," + view.getMapScrollPage().getY()
                        : "0,0";
        String pos = model.getCursor().getPosition().getX() + ","
                + model.getCursor().getPosition().getY();
        String systemInfo = String.format("|Page:%s|Pos:%s|Step:%d/%d|", page, pos,
                model.getQuantifierSteps(), model.getQuantifier());
        if (model.getLastSaveTime() != null) {
            systemInfo += "Saved:" + model.getLastSaveTime().toString().substring(11, 16) + "|";
        }

        String line1;
        if (vehicleText.length() + systemInfo.length() < totalWidth) {
            int padding = totalWidth - vehicleText.length() - systemInfo.length();
            line1 = vehicleText + " ".repeat(padding) + systemInfo;
        } else {
            line1 = vehicleText + " | " + systemInfo;
        }
        richInfo.append(line1).append("\n");

        // Row 4: Economy info (right aligned)
        EconomyManager economy = model.getEconomyManager();
        String line2 = "";
        if (economy != null) {
            String moneyText = String.format(java.util.Locale.US, "|In:%,.2f|Out:%,.2f|$:%,.2f|",
                    economy.getTotalIncome(), economy.getTotalExpenses(), economy.getBalance());
            if (moneyText.length() < totalWidth) {
                int leftPadding = totalWidth - moneyText.length(); // Right align
                line2 = " ".repeat(leftPadding) + moneyText;
            } else {
                line2 = moneyText;
            }
        }
        richInfo.append(line2).append("\n\n"); // Extra newline to leave Row 4 blank for Specific
                                               // Help

        // Row 6: Global Help
        richInfo.append(
                "[PgUp/Dn]: Scroll | [c/C]: Camera | [r/d/f/s/t/l/u/p/n]: Modes | [Tab]: Toggle Info | [Esc]: Exit");

        view.setInfoBarText(richInfo.toString());
    }

    private String getNotchBar(int current, int target, int max) {
        StringBuilder bar = new StringBuilder("[");
        for (int i = 1; i <= max; i++) {
            char c = ' ';
            if (i <= current) {
                c = '=';
            }
            if (i == target) {
                c = '!';
            }
            bar.append(c);
        }
        bar.append("]");
        return bar.toString();
    }

    private void visitProgram(Model model) {
        infoBarText += model.getLastSaveTime() != null ? "Last save: " + model.getLastSaveTime()
                : "Not saved";
    }

    public String getCommonInfoBarText(Model model) {
        return "| Page " + view.getMapScrollPage() + " | Cursor " + model.getCursor().getPosition()
                + " | Steps " + model.getQuantifierSteps() + "/" + model.getQuantifier() + " |";
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
                + (track.isUsingAlternativeRoute() ? track.getAlternativeRoute()
                        : track.getOriginalRoute());
    }

    private String getDynamicRouterAspect(DynamicRouter router) {
        StringBuffer ret = new StringBuffer();
        router.forEach(t -> {
            if (t.getValue() != null) {
                ret.append("(" + t.getKey() + ">" + t.getValue() + ") ");
            }
        });
        ret.append("\nNorm:" + router.getOriginalRoute() + " Alt:" + router.getAlternativeRoute()
                + " Using Alt:" + (router.isUsingAlternativeRoute() ? "TRUE" : "FALSE"));
        return ret.toString();
    }

    @Override
    public void visitTunnelRailTrack(TunnelRailTrack track) {
        infoBarText += "Track:[" + track.getPosition().getX() + "," + track.getPosition().getY()
                + "]" + getRouterAspect(track.getRouter()) + "\n";
        infoBarText += "Connect:...";
    }

    @Override
    public void visitLocomotive(Locomotive locomotive) {
        String speedStr = String.valueOf(locomotive.getSpeed());
        if (locomotive.getSpeed() != locomotive.getTargetSpeed()) {
            speedStr += "->" + locomotive.getTargetSpeed();
        }
        infoBarText += "Train " + locomotive.getId() + " Speed " + speedStr + " Wagons "
                + (locomotive.getTrain().getLinkers().size() - 1)
                + (locomotive.isReversed() ? " Reversed" : "");
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
        infoBarText += "Semaphore:[" + semaphore.getId() + ":"
                + (semaphore.isOpen() ? "open" : "closed") + "]" + "\n";
    }

    @Override
    public void visitSpeedSignal(letrain.track.SpeedSignal speedSignal) {
        String type = speedSignal.isMax() ? "Max" : "Min";
        infoBarText += "SpeedSignal:[" + speedSignal.getId() + ":" + type + " "
                + speedSignal.getLimit() + "]" + "\n";
    }

    @Override
    public void visitStation(Station station) {
        infoBarText += "Station:[" + station.getId() + "]" + "\n" + "Position:"
                + station.getPosition() + "\n";
    }

    @Override
    public void visitGroundMap(GroundMap groundMap) {}

    @Override
    public void visitGround(Ground ground) {}

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
