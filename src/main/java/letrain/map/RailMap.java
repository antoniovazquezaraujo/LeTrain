package letrain.map;

import java.util.function.Consumer;

import letrain.visitor.Renderable;

public interface RailMap<T> extends Renderable {
    void forEach(Consumer<T> c);

    T getTrackAt(int x, int y);

    T getTrackAt(Point pos);

    void addTrack(Point p, T t);

    T removeTrack(Point p);
}
