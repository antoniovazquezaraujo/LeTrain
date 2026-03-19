package letrain.map.impl;
 
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.map.Router;
import letrain.utils.Pair;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.annotation.JsonTypeName("SimpleRouter")
public class SimpleRouter implements Router {

    @com.fasterxml.jackson.annotation.JsonProperty("dirMap")
    protected Map<Dir, Dir> dirMap = new HashMap<>();

    public SimpleRouter() {

    }

    @JsonIgnore
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
    @JsonIgnore
    public Dir getAnyDir() {
        return getFirstOpenDir();
    }

    @Override
    @JsonIgnore
    public boolean isStraight() {
        return getNumRoutes() <= 2
                &&
                allRoutesAreStright();
    }

    @Override
    @JsonIgnore
    public boolean isCurve() {
        return getNumRoutes() == 2
                &&
                !allRoutesAreStright();
    }

    @Override
    @JsonIgnore
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
    @JsonIgnore
    public Dir getFirstOpenDir() {
        if (getNumRoutes() > 0) {
            return dirMap.keySet().iterator().next();
        }
        return null;
    }

    @Override
    @JsonIgnore
    public int getNumRoutes() {
        return dirMap.keySet().size();
    }

    @Override
    public void addRoute(Dir from, Dir to) {
        if (from == null || to == null) {
            // Null directions cause NullPointerExceptions later in Map.get or equals
            return;
        }
        if (from == to) {
            // Self-loops cause immediate 180-degree flips and infinite loops in some logic
            return;
        }
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