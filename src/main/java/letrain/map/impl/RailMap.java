package letrain.map.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import letrain.map.Point;
import letrain.track.rail.RailTrack;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class RailMap implements letrain.map.RailMap<RailTrack>, Renderable {

    private Map<Integer, Map<Integer, RailTrack>> rails;

    public RailMap() {
        rails = new TreeMap<>();
    }

    public Map<Integer, Map<Integer, RailTrack>> getRails() {
        return rails;
    }

    public void setRails(Map<Integer, Map<Integer, RailTrack>> rails) {
        this.rails = rails;
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
    public void forEachInRange(int minX, int minY, int maxX, int maxY, Consumer<RailTrack> c) {
        for (int row = minY; row <= maxY; row++) {
            Map<Integer, RailTrack> rowRails = rails.get(row);
            if (rowRails != null) {
                for (int col = minX; col <= maxX; col++) {
                    RailTrack track = rowRails.get(col);
                    if (track != null) {
                        c.accept(track);
                    }
                }
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
    public void addTrack(Point point, RailTrack rail) {
        int x = point.getX();
        int y = point.getY();
        if (!rails.containsKey(y)) {
            rails.put(y, new TreeMap<>());
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
    public RailTrack removeTrack(Point point) {
        RailTrack ret = getTrackAt(point);
        rails.get(point.getY()).remove(point.getX());
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
