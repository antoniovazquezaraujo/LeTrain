package letrain.ground;

import java.util.function.Consumer;

import letrain.map.Point;
import letrain.visitor.Renderable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = letrain.ground.impl.GroundMap.class)
public interface GroundMap extends Renderable {
    public static final int GROUND = 0;
    public static final int WATER = 1;
    public static final int ROCK = 2;

    // Producers (10-19)
    public static final int GOLD_MINE = 10;
    public static final int MINE = 11;
    public static final int RUBY_MINE = 12;

    // Consumers (20-29)
    public static final int JEWELRY_STORE = 20;
    public static final int POWER_PLANT = 21;
    public static final int RUBY_STORE = 22;

    void renderBlock(int startx, int starty, int width, int height);

    Integer getValueAt(int x, int y);

    Integer getValueAt(Point pos);

    void setValueAt(int x, int y, Integer value);

    void setValueAt(Point pos, Integer value);

    Integer removeValueAt(int x, int y);

    Integer removeValueAt(Point pos);

    public void forEach(Consumer<Ground> c);

    Integer findClosestIndustry(Point center, int radius);

    int countIndustryDensity(Point center, int radius, int industryType);
}
