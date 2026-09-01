package letrain.map;

import letrain.utils.Pair;

public interface DynamicRouter extends Router {
    void setAlternativeRoute();

    void setNormalRoute();

    boolean flipRoute();

    boolean isUsingAlternativeRoute();

    Pair<Dir, Dir> getAlternativeRoute();

    Pair<Dir, Dir> getOriginalRoute();
}
