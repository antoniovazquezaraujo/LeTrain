package letrain.map;

import letrain.visitor.Renderable;

import java.util.function.Consumer;

public interface RailMap<T> extends Renderable {
    void forEach(Consumer<T> c);

    T getTrackAt(int x, int y);

    T getTrackAt(Point pos);

    void addTrack(Point p, T t);

    T removeTrack(Point p);
}
