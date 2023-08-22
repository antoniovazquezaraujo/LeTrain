package letrain.visitor;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
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
import letrain.utils.Pair;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

public class InfoVisitor implements Visitor {

    public record GameModeMenuOption(
            String gameModeName,
            String gameModeDescription,
            Supplier<Boolean> enabledIf,
            Supplier<Boolean> selectedIf,
            Supplier<GameMode> doWhenSelected) {
    }

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

        List<GameModeMenuOption> gameModeMenuOptions = Arrays.asList(
                new GameModeMenuOption(
                        "&rails",
                        "<:left >:right ^:forwd v:backwd shift+^:rail ctrl+^:del insert:add sensor delete:delete sensor home:insert semaphore end:delete semaphore",
                        () -> true,
                        () -> (model.getMode() == GameMode.RAILS),
                        () -> (GameMode.RAILS)),
                new GameModeMenuOption(
                        "&drive",
                        "<:prev >:next ^:accel v:decel space:reverse (pgup, pgdn, ctrl+pgup, ctrl+pgdn):move map",
                        () -> !model.getLocomotives().isEmpty(),
                        () -> model.getMode() == GameMode.DRIVE,
                        () -> GameMode.DRIVE),
                new GameModeMenuOption(
                        "&forks",
                        "<:prev >:next space:toggle #:select",
                        () -> !model.getForks().isEmpty(),
                        () -> model.getMode() == GameMode.FORKS,
                        () -> GameMode.FORKS),
                new GameModeMenuOption(
                        "&semaphores",
                        "<:prev >:next space:toggle #:select",
                        () -> !model.getSemaphores().isEmpty(),
                        () -> model.getMode() == GameMode.SEMAPHORES,
                        () -> GameMode.SEMAPHORES),
                new GameModeMenuOption(
                        "&trains",
                        "A-Z:locomotive a-z:wagon enter:end",
                        () -> model.getCursorRailTrack() != null,
                        () -> model.getMode() == GameMode.TRAINS,
                        () -> GameMode.TRAINS),
                new GameModeMenuOption(
                        "&link",
                        "^:front v:back space:link",
                        () -> !model.getLocomotives().isEmpty(),
                        () -> model.getMode() == GameMode.LINK,
                        () -> GameMode.LINK),
                new GameModeMenuOption(
                        "&unlink",
                        "<:front >:back ^:add v:del space:unlink",
                        () -> !model.getLocomotives().isEmpty(),
                        () -> model.getMode() == GameMode.UNLINK,
                        () -> GameMode.UNLINK),
                new GameModeMenuOption(
                        "statio&ns",
                        "<:prev >:next -:load/unload passengers space:clean selection backspace:del number #:select",
                        () -> !model.getStations().isEmpty(),
                        () -> model.getMode() == GameMode.STATIONS,
                        () -> GameMode.STATIONS));

        visitEconomyManager(model.getEconomyManager());
        //visitMenu(gameModeMenuOptions);
        view.setMenu(gameModeMenuOptions);
        view.setHelpBarText(getModeHelp(model.getMode()));
        view.setInfoBarText(infoBarText);
    }
    static final TextColor NORMAL_MENU_FG_COLOR = ANSI.WHITE;
    static final TextColor NORMAL_MENU_BG_COLOR = ANSI.BLACK;
    static final TextColor DISABLED_FG_COLOR = ANSI.YELLOW;
    static final TextColor SELECTED_FG_COLOR = ANSI.BLUE;
    static final TextColor SHORTCUT_COLOR = ANSI.YELLOW;

    private void visitMenu(List<GameModeMenuOption> gameModeMenuOptions) {
                int length = 1;
        // for (GameModeMenuOption option : gameModeMenuOptions) {
        //     String[] parts = option.gameModeName.split("&");
        //     String firstPart = parts[0];
        //     String shortcutPart = parts[1].substring(0, 1);
        //     String thirdPart = parts[1].substring(1);
        //     view.menuBox.setFgColor(NORMAL_MENU_FG_COLOR);
        //     if (!option.getSecond().enabledIf().get()) {
        //         menuBox.setForegroundColor(DISABLED_FG_COLOR);
        //     }

        //     if (option.getSecond().selectedIf().get()) {
        //         menuBox.setBackgroundColor(SELECTED_FG_COLOR);
        //     } else {
        //         menuBox.setBackgroundColor(NORMAL_MENU_FG_COLOR);
        //     }
        //     menuBox.putString(menuBoxPosition.withRelative(length, 1), firstPart);
        //     length += firstPart.length();

        //     menuBox.setForegroundColor(SHORTCUT_COLOR);
        //     menuBox.putString(menuBoxPosition.withRelative(length, 1), shortcutPart);
        //     length += shortcutPart.length();

        //     menuBox.setForegroundColor(NORMAL_MENU_FG_COLOR);
        //     if (!option.getSecond().enabledIf().get()) {
        //         menuBox.setForegroundColor(DISABLED_FG_COLOR);
        //     }
        //     menuBox.putString(menuBoxPosition.withRelative(length, 1), thirdPart);

        //     // menuBox.setForegroundColor(fgColor);
        //     length += thirdPart.length() + 1;
        //     // menuBox.setBackgroundColor(oldBgColor);
        //     // bgColor = oldBgColor;
        //     // fgColor = oldFgColor;
        // }
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
                ret += "<:prev >:next -:load/unload passengers space:clean selection backspace:del number #:select";
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
