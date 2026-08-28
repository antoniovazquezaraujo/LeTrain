package letrain.map.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import letrain.map.Dir;
import letrain.map.DynamicRouter;
import letrain.utils.Pair;

@com.fasterxml.jackson.annotation.JsonTypeName("ForkRouter")
public class ForkRouter extends SimpleRouter implements DynamicRouter {
    @com.fasterxml.jackson.annotation.JsonProperty("alternativeRoute")
    private Pair<Dir, Dir> alternativeRoute = null;

    @com.fasterxml.jackson.annotation.JsonProperty("originalRoute")
    private Pair<Dir, Dir> originalRoute = null;

    @com.fasterxml.jackson.annotation.JsonProperty("usingAlternativeRoute")
    private boolean usingAlternativeRoute = false;

    @Override
    public String toString() {
        String ret = "";
        String first = "", second = "", third = "";
        if (originalRoute != null) {
            first = originalRoute.getKey() + ">" + originalRoute.getValue();
        }
        if (alternativeRoute != null) {
            second = alternativeRoute.getKey() + ">" + alternativeRoute.getValue();
        }
        for (Dir dir : dirMap.keySet()) {
            third = dir + ">" + dirMap.get(dir);
        }
        ret += !isUsingAlternativeRoute() ? "(" + first + ")," : first;
        ret += isUsingAlternativeRoute() ? ("(" + second + ")") : second;
        ret += "," + third;
        return ret;
    }

    @Override
    public Dir getDir(Dir dir) {
        if (dir == null) {
            return null;
        }
        if (originalRoute != null && alternativeRoute != null) {
            if (dir.equals(originalRoute.getValue())) {
                return originalRoute.getKey();
            }
            if (dir.equals(alternativeRoute.getValue())) {
                return alternativeRoute.getKey();
            }
            if (dir.equals(originalRoute.getKey())) {
                return usingAlternativeRoute ? alternativeRoute.getValue()
                        : originalRoute.getValue();
            }
        }
        return dirMap.get(dir);
    }

    @Override
    @JsonIgnore
    public Dir getFirstOpenDir() {
        if (isUsingAlternativeRoute()) {
            return alternativeRoute != null ? alternativeRoute.getValue() : null;
        } else {
            return originalRoute != null ? originalRoute.getValue() : null;
        }
    }

    @Override
    public void setAlternativeRoute() {
        if (alternativeRoute != null) {
            usingAlternativeRoute = true;
            dirMap.put(alternativeRoute.getKey(), alternativeRoute.getValue());
        }
    }

    @Override
    public void setNormalRoute() {
        if (originalRoute != null) {
            usingAlternativeRoute = false;
            dirMap.put(originalRoute.getKey(), originalRoute.getValue());
        }
    }

    @Override
    public boolean flipRoute() {
        if (usingAlternativeRoute) {
            setNormalRoute();
            return false;
        } else {
            setAlternativeRoute();
            return true;
        }
    }

    @Override
    public boolean isUsingAlternativeRoute() {
        return usingAlternativeRoute;
    }

    @Override
    @JsonIgnore
    public Pair<Dir, Dir> getAlternativeRoute() {
        return alternativeRoute;
    }

    @Override
    @JsonIgnore
    public Pair<Dir, Dir> getOriginalRoute() {
        return originalRoute;
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        // ruta repetida
        if (dirMap.containsKey(from) && dirMap.get(from).equals(to)) {
            return;
        }
        if (originalRoute != null && originalRoute.getKey().equals(from)
                && originalRoute.getValue().equals(to)) {
            return;
        }
        if (alternativeRoute != null && alternativeRoute.getKey().equals(from)
                && alternativeRoute.getValue().equals(to)) {
            return;
        }

        // ruta adicional para la ruta from
        if (dirMap.containsKey(from)) { // && !dirMap.containsKey(to)) {
            originalRoute = new Pair<>(from, dirMap.get(from));
            alternativeRoute = new Pair<>(from, to);
            usingAlternativeRoute = true;
        }

        // ruta adicional para la ruta to
        if (dirMap.containsKey(to)) { // && !dirMap.containsKey(from)) {
            originalRoute = new Pair<>(to, dirMap.get(to));
            alternativeRoute = new Pair<>(to, from);
            usingAlternativeRoute = true;
        }
        // agregamos la nueva ruta en ambos sentidos
        dirMap.put(from, to);
        dirMap.put(to, from);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        dirMap.remove(from);
        dirMap.remove(to);
        if (alternativeRoute != null) {
            if (from.equals(alternativeRoute.getKey()) && to.equals(alternativeRoute.getValue())) {
                alternativeRoute = null;
                addRoute(originalRoute.getKey(), originalRoute.getValue());
            }
        }
    }
}
