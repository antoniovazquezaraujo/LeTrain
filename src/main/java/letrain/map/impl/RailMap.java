package letrain.map.impl;

import letrain.visitor.Visitor;
import letrain.map.Point;
 
import letrain.track.rail.RailTrack;
import letrain.visitor.Renderable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RailMap implements Serializable, letrain.map.RailMap<RailTrack>, Renderable {

    private final Map<Integer, Map<Integer, RailTrack>> rails;

    public RailMap() {
        rails = new HashMap<>();
    }

    @Override
    public void forEach(Consumer<RailTrack> c) {
        for (int row : rails.keySet()) {
            for (RailTrack track : rails.get(row).values()) {
                c.accept(track);
            }
        }
    }

    @Override
    public RailTrack getTrackAt(Point pos) {
        return getTrackAt(pos.getX(), pos.getY());
    }

    @Override
    public RailTrack getTrackAt(int x, int y) {
        Map<Integer, RailTrack> m = rails.get(y);
        if (m != null) {
            return m.get(x);
        }
        return null;
    }

    @Override
    public void addTrack(Point p, RailTrack rail) {
        int x = p.getX();
        int y = p.getY();
        if (!rails.containsKey(y)) {
            rails.put(y, new HashMap<>());
        }
        Map<Integer, RailTrack> cols = rails.get(y);
        if (rail != null) {
            rail.setPosition(new Point(x, y));
            cols.put(x, rail);
        } else {
            cols.remove(x);
        }

    }

    @Override
    public RailTrack removeTrack(Point p) {
        RailTrack ret = getTrackAt(p);
        rails.get(p.getY()).remove(p.getX());
        return ret;
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitRailMap(this);
    }
}
