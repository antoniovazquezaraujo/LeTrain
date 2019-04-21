package letrain.map;

import javafx.util.Pair;

import java.util.function.Consumer;

public interface Router {
    Dir getDir(Dir dir);

    Dir getFirstOpenDir();

    int getNumRoutes();

    void addRoute(Dir from, Dir to);

    void removeRoute(Dir from, Dir to);

    Dir getAnyDir();

    boolean isStraight();

    boolean isCurve();

    boolean isCross();

    void clear();

    void forEach(Consumer<Pair<Dir, Dir>> routeConsumer);
}
