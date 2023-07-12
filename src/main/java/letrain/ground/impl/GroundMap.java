package letrain.ground.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import letrain.ground.Ground;
import letrain.ground.Ground.GroundType;
import letrain.map.Point;
import letrain.visitor.Visitor;

public class GroundMap implements letrain.ground.GroundMap {
    MessageDigest digest;
    static final int ITERACTIONS = 5;
    final Map<Integer, Map<Integer, Integer>> cells;

    public GroundMap() {
        cells = new HashMap<>();
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public void forEach(Consumer<Ground> c) {
        for (int row : cells.keySet()) {
            for(int col: cells.get(row).keySet()) {
                c.accept(new Ground(new Point(col, row), 1==cells.get(row).get(col)?GroundType.MOUNTAIN: GroundType.GROUND));
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
        randomizeBlock(startx, starty, width, height);
        generateTerrain(startx, starty, width, height);
    }

    void randomizeBlock(int startX, int startY, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int column = startX + x;
                int row = startY + y;
                String coordenadas = (column) + "-" + (row);
                byte[] hashBytes = digest.digest(coordenadas.getBytes(StandardCharsets.UTF_8));
                byte numero = (byte) (Math.abs(bytesToInt(hashBytes)) % 100);
                setValueAt(column, row, (numero % 100) > 50 ? 1 : 0);
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
                    int aliveNeighbors = countAliveNeighbors(colIndex, rowIndex);
                    if (1 == getValueAt(colIndex, rowIndex)) {
                        if (aliveNeighbors < 4) {
                            setValueAt(colIndex, rowIndex, 0); // Cell dies due to underpopulation
                        } else {
                            setValueAt(colIndex, rowIndex, 1); // Cell survives
                        }
                    } else {
                        if (aliveNeighbors > 4) {
                            setValueAt(colIndex, rowIndex, 1); // Cell becomes alive due to reproduction
                        } else {
                            setValueAt(colIndex, rowIndex, 0); // Cell remains dead
                        }
                    }
                }
            }
        }
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