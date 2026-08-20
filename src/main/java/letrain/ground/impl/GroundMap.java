package letrain.ground.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import letrain.economy.EconomyManager;
import letrain.ground.Ground;
import letrain.ground.PerlinNoise;
import letrain.map.Point;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class GroundMap implements letrain.ground.GroundMap, Serializable {
    private static final long serialVersionUID = 1L;
    Logger log = LoggerFactory.getLogger(getClass());
    @com.fasterxml.jackson.annotation.JsonIgnore
    Map<Integer, Map<Integer, Integer>> cells;
    @com.fasterxml.jackson.annotation.JsonIgnore
    PerlinNoise noise = null;
    @com.fasterxml.jackson.annotation.JsonProperty("blocks")
    Set<Block> blocks;
    private EconomyManager economyManager;

    int OCTAVES = 5;
    int col = 1000;
    int row = 1000;
    int WATER = 113;
    int GROUND = 158;
    int ROCK = 200;

    // Constructor
    public GroundMap(int seed, EconomyManager economyManager) {
        noise = new PerlinNoise(seed);
        cells = new HashMap<>();
        blocks = new HashSet<>();
        this.economyManager = economyManager;
    }

    /**
     * Public default constructor for Jackson deserialization.
     */
    public GroundMap() {
        cells = new HashMap<>();
        blocks = new HashSet<>();
    }

    record CellEnv(int ground, int rock, int water) {

    }

    record Block(int x, int y, int width, int height) {
    }

    @Override
    public void forEach(Consumer<Ground> c) {
        for (int row : cells.keySet()) {
            for (int col : cells.get(row).keySet()) {
                c.accept(new Ground(col, row, cells.get(row).get(col)));
            }
        }
    }

    @Override
    public void forEachInRange(int minX, int minY, int maxX, int maxY, Consumer<Ground> c) {
        letrain.map.Point tempPoint = new letrain.map.Point(0, 0);
        Ground tempGround = new Ground(0, 0, 0);
        // Spatial optimization: only iterate over the relevant rows/columns
        for (int row = minY; row <= maxY; row++) {
            Map<Integer, Integer> mapRow = cells.get(row);
            if (mapRow != null) {
                for (int col = minX; col <= maxX; col++) {
                    Integer value = mapRow.get(col);
                    if (value != null) {
                        tempPoint.setX(col);
                        tempPoint.setY(row);
                        tempGround.setPosition(tempPoint);
                        tempGround.setType(value);
                        c.accept(tempGround);
                    }
                }
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

    public void setValueAt(Point point, Integer value) {
        setValueAt(point.getX(), point.getY(), value);
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

    public Integer removeValueAt(Point point) {
        return removeValueAt(point.getX(), point.getY());
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

    public int getBackgroundTerrain(int col, int row) {
        if (noise == null) return letrain.ground.GroundMap.GROUND;
        float baseNoise = noise.smoothNoise(Math.abs(col * 0.01F), Math.abs(row * 0.02F), 0, OCTAVES);
        float scaledBase = scaleAndShift(baseNoise, -0.7F, 0.7F, 0F, 255F);
        float waterThreshold = (economyManager != null) ? economyManager.getWaterThreshold() : 130f;
        float rockThreshold = (economyManager != null) ? economyManager.getRockThreshold() : 180f;
        if (scaledBase < waterThreshold) return letrain.ground.GroundMap.WATER;
        if (scaledBase > rockThreshold) return letrain.ground.GroundMap.ROCK;
        return letrain.ground.GroundMap.GROUND;
    }

    void generateTerrain(int startX, int startY, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int colIndex = ((startX) + col);
                int rowIndex = ((startY) + row);

                // LAYER 0: Base Terrain
                float baseNoise = noise.smoothNoise(Math.abs(colIndex * 0.01F), Math.abs(rowIndex * 0.02F), 0, OCTAVES);
                float scaledBase = scaleAndShift(baseNoise, -0.7F, 0.7F, 0F, 255F);

                float waterThreshold = (economyManager != null) ? economyManager.getWaterThreshold() : 130f;
                float rockThreshold = (economyManager != null) ? economyManager.getRockThreshold() : 180f;

                if (scaledBase < waterThreshold) {
                    setValueAt(colIndex, rowIndex, 1);
                } else if (scaledBase > rockThreshold) {
                    setValueAt(colIndex, rowIndex, 2);
                } else {
                    // GROUND - check for industries
                    int terrain = 0; // Default Ground
                    
                    float goldThreshold = (economyManager != null) ? economyManager.getGoldThreshold() : 0.28f;
                    float coalThreshold = (economyManager != null) ? economyManager.getCoalThreshold() : 0.28f;
                    float rubyThreshold = (economyManager != null) ? economyManager.getRubyThreshold() : 0.28f;

                    // LAYER 1: Gold Industry (z=1)
                    float woodNoise = noise.smoothNoise(Math.abs(colIndex * 0.01F), Math.abs(rowIndex * 0.02F), 1,
                            OCTAVES);
                    if (woodNoise > goldThreshold) {
                        terrain = GOLD_MINE;
                    } else if (woodNoise < -goldThreshold) {
                        terrain = JEWELRY_STORE;
                    }

                    // LAYER 2: Coal Industry (z=2) - Only if no gold
                    if (terrain == 0) {
                        float coalNoise = noise.smoothNoise(Math.abs(colIndex * 0.01F), Math.abs(rowIndex * 0.02F), 2,
                                OCTAVES);
                        if (coalNoise > coalThreshold) {
                            terrain = MINE;
                        } else if (coalNoise < -coalThreshold) {
                            terrain = POWER_PLANT;
                        }
                    }

                    // LAYER 3: Ruby Industry (z=3) - Only if no gold or coal
                    if (terrain == 0) {
                        float fishNoise = noise.smoothNoise(Math.abs(colIndex * 0.01F), Math.abs(rowIndex * 0.02F), 3,
                                OCTAVES);
                        if (fishNoise > rubyThreshold) {
                            terrain = RUBY_MINE;
                        } else if (fishNoise < -rubyThreshold) {
                            terrain = RUBY_STORE;
                        }
                    }

                    setValueAt(colIndex, rowIndex, terrain);
                }
            }
        }
    }

    public void compactBlocks() {
        if (cells == null || cells.isEmpty()) return;
        
        Set<Long> explored = new HashSet<>();
        for (Integer r : cells.keySet()) {
            Map<Integer, Integer> rowCells = cells.get(r);
            if (rowCells != null) {
                for (Integer c : rowCells.keySet()) {
                    long packed = ((long) c << 32) | (r & 0xFFFFFFFFL);
                    explored.add(packed);
                }
            }
        }
        
        List<Long> sortedCells = new ArrayList<>(explored);
        sortedCells.sort((a, b) -> {
            int yA = (int) (a.longValue());
            int yB = (int) (b.longValue());
            if (yA != yB) return Integer.compare(yA, yB);
            int xA = (int) (a.longValue() >> 32);
            int xB = (int) (b.longValue() >> 32);
            return Integer.compare(xA, xB);
        });
        
        Set<Block> compacted = new HashSet<>();
        
        for (Long p : sortedCells) {
            if (!explored.contains(p)) continue;
            
            int startX = (int) (p >> 32);
            int startY = (int) (p.longValue());
            
            int maxX = startX;
            int maxY = startY;
            
            // Expand right
            while (explored.contains(((long) (maxX + 1) << 32) | (startY & 0xFFFFFFFFL))) {
                maxX++;
            }
            
            // Expand down
            boolean canExpandY = true;
            while (canExpandY) {
                int nextY = maxY + 1;
                for (int x = startX; x <= maxX; x++) {
                    if (!explored.contains(((long) x << 32) | (nextY & 0xFFFFFFFFL))) {
                        canExpandY = false;
                        break;
                    }
                }
                if (canExpandY) {
                    maxY++;
                }
            }
            
            // Mark as covered
            for (int y = startY; y <= maxY; y++) {
                for (int x = startX; x <= maxX; x++) {
                    explored.remove(((long) x << 32) | (y & 0xFFFFFFFFL));
                }
            }
            
            compacted.add(new Block(startX, startY, maxX - startX + 1, maxY - startY + 1));
        }
        
        this.blocks = compacted;
    }

    public void rebuildCellsFromBlocks() {
        if (blocks != null) {
            for (Block block : blocks) {
                generateTerrain(block.x(), block.y(), block.width(), block.height());
            }
        }
    }

    float scaleAndShift(float value, float inMin, float inMax, float outMin, float outMax) {
        return ((value - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
    }

    @Override
    public Integer findClosestIndustry(Point center, int radius) {
        Integer foundTerrain = null;
        double minDistance = Double.MAX_VALUE;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = center.getX() + dx;
                int cy = center.getY() + dy;
                Integer terrain = getValueAt(cx, cy);
                if (terrain != null && terrain >= 10 && terrain <= 29) {
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < minDistance) {
                        minDistance = dist;
                        foundTerrain = terrain;
                    }
                }
            }
        }
        return foundTerrain;
    }

    @Override
    public int countIndustryDensity(Point center, int radius, int industryType) {
        int count = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                Integer terrain = getValueAt(center.getX() + dx, center.getY() + dy);
                if (terrain != null && terrain == industryType) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitGroundMap(this);
    }
}
