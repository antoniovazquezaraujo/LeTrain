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

    boolean isFork();

    void clear();

    void setAlternativeRoute();

    void setNormalRoute();

    boolean flipRoute();

    boolean isUsingAlternativeRoute();

    void forEach(Consumer<Pair<Dir, Dir>> routeConsumer);

}
