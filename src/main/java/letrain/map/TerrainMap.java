package letrain.map;

import letrain.physics.Vector2D;
import letrain.visitor.Renderable;

import java.util.function.Consumer;

public interface TerrainMap<T> extends Renderable {
    void forEach(Consumer<T> c);

    T getTrackAt(double x, double y);

    T getTrackAt(Vector2D pos);

    void addTrack(Vector2D p, T t);

    T removeTrack(double x, double y);
}
