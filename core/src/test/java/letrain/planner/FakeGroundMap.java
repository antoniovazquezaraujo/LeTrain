package letrain.planner;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import letrain.ground.Ground;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.visitor.Visitor;

/**
 * Small finite in-memory terrain stub for planner tests. Reads outside the populated rectangle
 * return {@code null}, which models "no terrain data" and bounds the A* search.
 */
final class FakeGroundMap implements GroundMap {

    private record Cell(int x, int y) {
    }

    private final Map<Cell, Integer> cells = new HashMap<>();

    FakeGroundMap rect(int minX, int minY, int maxX, int maxY, int terrain) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                cells.put(new Cell(x, y), terrain);
            }
        }
        return this;
    }

    FakeGroundMap put(int x, int y, int terrain) {
        cells.put(new Cell(x, y), terrain);
        return this;
    }

    @Override
    public void renderBlock(int startx, int starty, int width, int height) {
        // No rendering in planner tests.
    }

    @Override
    public int getBackgroundTerrain(int x, int y) {
        return cells.getOrDefault(new Cell(x, y), GROUND);
    }

    @Override
    public Integer getValueAt(int x, int y) {
        return cells.get(new Cell(x, y));
    }

    @Override
    public Integer getValueAt(Point pos) {
        return getValueAt(pos.getX(), pos.getY());
    }

    @Override
    public void setValueAt(int x, int y, Integer value) {
        if (value == null) {
            cells.remove(new Cell(x, y));
        } else {
            cells.put(new Cell(x, y), value);
        }
    }

    @Override
    public void setValueAt(Point pos, Integer value) {
        setValueAt(pos.getX(), pos.getY(), value);
    }

    @Override
    public Integer removeValueAt(int x, int y) {
        return cells.remove(new Cell(x, y));
    }

    @Override
    public Integer removeValueAt(Point pos) {
        return removeValueAt(pos.getX(), pos.getY());
    }

    @Override
    public void forEach(Consumer<Ground> consumer) {
        cells.forEach((cell, terrain) -> consumer.accept(new Ground(cell.x(), cell.y(), terrain)));
    }

    @Override
    public void forEachInRange(int minX, int minY, int maxX, int maxY, Consumer<Ground> consumer) {
        forEach(ground -> {
            if (ground.getPosition().getX() >= minX && ground.getPosition().getX() <= maxX
                    && ground.getPosition().getY() >= minY && ground.getPosition().getY() <= maxY) {
                consumer.accept(ground);
            }
        });
    }

    @Override
    public Integer findClosestIndustry(Point center, int radius) {
        return null;
    }

    @Override
    public int countIndustryDensity(Point center, int radius, int industryType) {
        return 0;
    }

    @Override
    public void accept(Visitor visitor) {
        // No rendering in planner tests.
    }
}
