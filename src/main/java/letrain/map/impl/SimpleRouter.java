package letrain.map.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.map.Router;
import letrain.utils.Pair;

public class SimpleRouter implements Serializable, Router {

    protected final Map<Dir, Dir> dirMap = new HashMap<>();

    public SimpleRouter() {

    }

    public boolean isHorizontalOrVertical() {
        return dirMap.keySet().stream()
                .filter(t -> {
                    return (t == Dir.N) || t == Dir.S | t == Dir.E | t == Dir.W;
                }).count() > 0;
    }

    @Override
    public String toString() {
        return "{" + dirMap + '}';
    }

    @Override
    public Dir getAnyDir() {
        return getFirstOpenDir();
    }

    @Override
    public boolean isStraight() {
        return getNumRoutes() <= 2
                &&
                allRoutesAreStright();
    }

    @Override
    public boolean isCurve() {
        return getNumRoutes() == 2
                &&
                !allRoutesAreStright();
    }

    @Override
    public boolean isCross() {
        return getNumRoutes() > 3;
    }

    @Override
    public void clear() {
        dirMap.clear();
    }

    private boolean allRoutesAreStright() {
        return dirMap.entrySet().stream().noneMatch(t -> !t.getKey().isStraight(t.getValue()));
    }

    @Override
    public Dir getDir(Dir dir) {
        return dirMap.get(dir);
    }

    @Override
    public Dir getFirstOpenDir() {
        if (getNumRoutes() > 0) {
            return dirMap.keySet().iterator().next();
        }
        return null;
    }

    @Override
    public int getNumRoutes() {
        return dirMap.keySet().size();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        // ruta repetida
        if (dirMap.containsKey(from) && dirMap.get(from).equals(to)) {
            return;
        }
        // agregamos la nueva ruta en ambos sentidos
        dirMap.put(from, to);
        dirMap.put(to, from);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        dirMap.remove(from);
        dirMap.remove(to);
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        dirMap.entrySet().stream()
                .map(t -> new Pair<>(t.getKey(), t.getValue()))
                .forEach(routeConsumer);
    }
}