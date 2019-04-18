package letrain.map;

import letrain.view.Renderable;

import java.util.function.Consumer;

public interface TerrainMap<T> extends Renderable {
    void forEach(Consumer<T> c);

    T getTrackAt(int x, int y);

    T getTrackAt(Point pos);

    void addTrack(Point p, T t);

    T removeTrack(int x, int y);
}
