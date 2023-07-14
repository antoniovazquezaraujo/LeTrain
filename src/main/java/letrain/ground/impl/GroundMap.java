package letrain.ground.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.ground.Ground;
import letrain.ground.NoiseGenerator;
import letrain.map.Point;
import letrain.visitor.Visitor;

public class GroundMap implements letrain.ground.GroundMap {
    Logger log = LoggerFactory.getLogger(getClass());
    MessageDigest digest;
    static final int ITERACTIONS = 5;
    final Map<Integer, Map<Integer, Integer>> cells;
    NoiseGenerator noiseGenerator = new NoiseGenerator(123456789);

    record CellEnv(int ground, int rock, int water) {

    }

    record Block(int x, int y, int width, int height) {
    }

    Set<Block> blocks;

    public GroundMap() {
        cells = new HashMap<>();
        blocks = new HashSet<>();
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

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

    public Integer getValueAt(int x, int y) {
        Map<Integer, Integer> m = cells.get(y);
        if (m != null) {
            Integer value = m.get(x);
            return value != null ? value : -1;
        }
        return -1;
    }

    public void setValueAt(Point p, Integer value) {
        setValueAt(p.getX(), p.getY(), value);
    }

    @Override
    public void setValueAt(int x, int y, Integer value) {
        if (!cells.containsKey(y)) {
            cells.put(y, new HashMap<>());
        }
        Map<Integer, Integer> cols = cells.get(y);
        if (value != null) {
            cols.put(x, value);
        } else {
            cols.remove(x);
        }
    }

    public Integer removeValueAt(Point p) {
        return removeValueAt(p.getX(), p.getY());
    }

    public Integer removeValueAt(int x, int y) {
        Integer ret = getValueAt(x, y);
        cells.get(y).remove(x);
        return ret;
    }

    public void renderBlock(int startx, int starty, int width, int height) {
        Block block = new Block(startx, starty, width, height);
        if (blocks.contains(block)) {
            return;
        }
        blocks.add(block);
        randomizeBlock(startx, starty, width, height);
        //generateTerrain(startx, starty, width, height);
    }

    void randomizeBlock(int startX, int startY, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int column = startX + x;
                int row = startY + y;
                     double scl = 0.8F;
                double v = noiseGenerator.noise(column * scl, row * scl, 1000);                
                log.debug("v:"+ v);      
                if (v < 0.7D) {
                  setValueAt(x,y,0);
                } else if (v < 0.5D) {
                  setValueAt(x,y,1);
                } else {
                    setValueAt(x,y,2);
                }
            }
        }
    }

    static int bytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= (bytes[i] & 0xFF);
        }
        return result;
    }

    void generateTerrain(int x, int y, int width, int height) {
        for (int iteraction = 0; iteraction < ITERACTIONS; iteraction++) {
            for (int col = 0; col < width; col++) {
                for (int row = 0; row < height; row++) {
                    int colIndex = x + col;
                    int rowIndex = y + row;

                    int type = getValueAt(colIndex, rowIndex);
                    CellEnv env = getCellEnv(colIndex, rowIndex);
                    if (type == GROUND) {
                        if (env.ground <= 3) {
                            setValueAt(x, y, WATER);
                        } else if (env.water > 4) {
                            setValueAt(x, y, ROCK);
                        }
                    } else if (type == WATER) {
                        if (env.water <= 4) {
                            setValueAt(x, y, ROCK);
                        } else if (env.rock > 4) {
                            setValueAt(x, y, GROUND);
                        }
                    } else if (type == ROCK) {
                        if (env.rock <= 5) {
                            setValueAt(x, y, GROUND);
                        } else if (env.ground > 4) {
                            setValueAt(x, y, WATER);
                        }
                    }
                }
            }
        }
    }

    CellEnv getCellEnv(int x, int y) {
        int countGround = 0;
        int countRock = 0;
        int countWater = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int neighborX = x + i;
                int neighborY = y + j;
                if (i == 0 && j == 0)
                    continue; // Exclude the current cell
                if (GROUND == getValueAt(neighborX, neighborY)) {
                    countGround++;
                }
                if (ROCK == getValueAt(neighborX, neighborY)) {
                    countRock++;
                }
                if (WATER == getValueAt(neighborX, neighborY)) {
                    countWater++;
                }
            }
        }
        return new CellEnv(countGround, countRock, countWater);
    }

    int countAliveNeighbors(int x, int y) {
        int count = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int neighborX = x + i;
                int neighborY = y + j;
                if (i == 0 && j == 0)
                    continue; // Exclude the current cell
                if (1 == getValueAt(neighborX, neighborY)) {
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