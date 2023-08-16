package letrain.ground.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import letrain.ground.Ground;
import letrain.ground.PerlinNoise;
import letrain.map.Point;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroundMap implements letrain.ground.GroundMap, Serializable {
    private static final long serialVersionUID = 1L;
    Logger log = LoggerFactory.getLogger(getClass());
    final Map<Integer, Map<Integer, Integer>> cells;
    PerlinNoise noise = null;
    Set<Block> blocks;

    int OCTAVES = 5;
    int col = 1000;
    int row = 1000;
    int WATER = 113;
    int GROUND = 158;
    int ROCK = 200;

    // Constructor
    public GroundMap(int seed) {
        noise = new PerlinNoise(seed);
        cells = new HashMap<>();
        blocks = new HashSet<>();
    }

    record CellEnv(int ground, int rock, int water) {

    }

    record Block(int x, int y, int width, int height) implements Serializable {
    }

    @Override
    public void forEach(Consumer<Ground> c) {
        for (int row : cells.keySet()) {
            for (int col : cells.get(row).keySet()) {
                c.accept(new Ground(col, row, cells.get(row).get(col)));
            }
        }
    }

    public Integer getValueAt(Point pos) {
        return getValueAt(pos.getX(), pos.getY());
    }

    public Integer getValueAt(int col, int row) {
        Map<Integer, Integer> mapRow = cells.get(row);
        if (mapRow != null) {
            Integer value = mapRow.get(col);
            if (value != null) {
                return value;
            }
        }
        return -1;
    }

    public void setValueAt(Point p, Integer value) {
        setValueAt(p.getX(), p.getY(), value);
    }

    @Override
    public void setValueAt(int col, int row, Integer value) {
        if (!cells.containsKey(row)) {
            cells.put(row, new HashMap<>());
        }
        Map<Integer, Integer> mapRow = cells.get(row);
        if (value != null) {
            mapRow.put(col, value);
        } else {
            mapRow.remove(col);
        }
    }

    public Integer removeValueAt(Point p) {
        return removeValueAt(p.getX(), p.getY());
    }

    public Integer removeValueAt(int row, int col) {
        Integer ret = getValueAt(row, col);
        cells.get(row).remove(col);
        return ret;
    }

    public void renderBlock(int startx, int starty, int width, int height) {
        Block block = new Block(startx, starty, width, height);
        if (blocks.contains(block)) {
            return;
        }
        blocks.add(block);

        generateTerrain(startx, starty, width, height);
    }

    void generateTerrain(int startX, int startY, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int colIndex = ((startX) + col);
                int rowIndex = ((startY) + row);
                float rand = (noise.smoothNoise(Math.abs(colIndex * 0.01F), Math.abs(rowIndex * 0.02F), 0,
                        OCTAVES));
                rand = scaleAndShift(rand, -0.7F, 0.7F, 0F, 255F);
                int intColor = (int) rand;
                if (rand < WATER) {
                    setValueAt(colIndex, rowIndex, 1);
                } else if (intColor < GROUND) {
                    setValueAt(colIndex, rowIndex, 0);
                } else {
                    setValueAt(colIndex, rowIndex, 2);
                }
            }
        }
    }

    float scaleAndShift(float value, float inMin, float inMax, float outMin, float outMax) {
        return ((value - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitGroundMap(this);
    }
}