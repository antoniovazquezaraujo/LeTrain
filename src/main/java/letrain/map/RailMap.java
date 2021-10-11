package letrain.map;

import letrain.physics.Vector2D;
import letrain.track.rail.RailTrack;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RailMap implements Serializable, TerrainMap<RailTrack> , Renderable {

    private final Map<Double, Map<Double, RailTrack>> rails;

    public RailMap() {
        rails = new HashMap<>();
    }

    @Override
    public void forEach(Consumer<RailTrack> c) {
        for (double row : rails.keySet()) {
            for (RailTrack track : rails.get(row).values()) {
                c.accept(track);
            }
        }
    }

    @Override
    public RailTrack getTrackAt(Vector2D pos) {
        return getTrackAt(pos.getX(), pos.getY());
    }

    @Override
    public RailTrack getTrackAt(double x, double y) {
        Map<Double, RailTrack> m = rails.get(y);
        if (m != null) {
            return m.get(x);
        }
        return null;
    }

    @Override
    public void addTrack(Vector2D p, RailTrack rail) {
        double x = p.getX();
        double y = p.getY();
        if (!rails.containsKey(y)) {
            rails.put(y, new HashMap<>());
        }
        Map<Double, RailTrack> cols = rails.get(y);
        if (rail != null) {
            rail.setPosition2D(new Vector2D(x, y));
            cols.put(x, rail);
        } else {
            cols.remove(x);
        }

    }

    @Override
    public RailTrack removeTrack(double x, double y) {
        RailTrack ret = getTrackAt(x, y);
        rails.get(y).remove(x);
        return ret;
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitMap(this);
    }
}
