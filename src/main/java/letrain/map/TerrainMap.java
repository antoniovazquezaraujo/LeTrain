package letrain.map;

import java.util.function.Consumer;

public interface TerrainMap<T> {
    void forEach(Consumer<T> c);

    T getTrackAt(int x, int y);

    T getTrackAt(Point pos);

    void addTrack(Point p, T t);

    T removeTrack(int x, int y);
}
