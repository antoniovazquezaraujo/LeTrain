package letrain.visitor;

import com.googlecode.lanterna.TextColor;

import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.map.Router;
import letrain.mvp.Model;
import letrain.mvp.Model.GameMode;
import letrain.mvp.View;
import letrain.mvp.impl.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
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
            case CREATE_LOAD_PLATFORM:
                break;
            case LOAD_TRAINS:
                break;
            case TRAINS:
                break;
        }
        visitCursor(model.getCursor());
        view.setInfoBarText(infoBarText);
        view.setHelpBarText(getModeHelp(model.getMode()));
    }

    private String getModeHelp(GameMode mode) {
        String[] menuOptions = { "(r)ails", "(d)rive", "(f)orks", "(t)rains", "(l)ink", "(u)nlink" };
        String title = getOptionsWithHighlight(menuOptions, mode.ordinal() - 1) + "\n";
        String ret = title;
        switch (mode) {
            case MENU:
                ret += "escape:exit ";
                break;
            case RAILS:
                ret += "<:left >:right ^:forwd v:backwd shift+^:rail ctrl+^:del";
                break;
            case DRIVE:
                ret += "<:prev >:next ^:accel v:decel space:reverse (pgup, pgdn, ctrl+pgup, ctrl+pgdn):move map";
                break;
            case FORKS:
                ret += "<:prev >:next space:toggle #:select";
                break;
            case TRAINS:
                ret += "A-Z:locomotive a-z:wagon enter:end";
                break;
            case UNLINK:
                ret += "<:front >:back ^:add v:del space:unlink";
                break;
            case LINK:
                ret += "^:front v:back space:link";
                break;
        }
        return ret;
    }

    private String getOptionsWithHighlight(String[] menuOptions, int ordinal) {
        StringBuffer ret = new StringBuffer();
        for (int i = 0; i < menuOptions.length; i++) {
            if (i == ordinal) {
                ret.append("[" + menuOptions[i] + "]");
            } else {
                ret.append(menuOptions[i]);
            }
            if (i < menuOptions.length - 1) {
                ret.append(" ");
            }
        }
        return ret.toString();
    }

    @Override
    public void visitMap(RailMap map) {
        map.forEach(t -> t.accept(this));
    }

    @Override
    public void visitRailTrack(RailTrack track) {
        infoBarText += "Track:{" + track + "}\n";
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
    public void visitStopRailTrack(StopRailTrack track) {
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
        infoBarText += " Speed:" + locomotive.getSpeed() +
                " Rev:" + locomotive.isReversed() +
                " Dir:" + locomotive.getDir() +
                " RealDir:" + locomotive.getRealDir() +
                " ";
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
        infoBarText += "Cursor:[" + cursor.getPosition().getX() + "," + cursor.getPosition().getY() + "]" + "\n";
    }

    @Override
    public void visitSensor(Sensor sensor) {
        infoBarText += "Sensor:[" + sensor.getId() + "]" + "\n";
    }

}
