package letrain.map;

import javafx.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


public class SimpleRouter implements Router {


    Map<Dir, Dir> dirMap = new HashMap<>();
    Pair<Dir, Dir> alternativeRoute= null;
    Pair<Dir, Dir> originalRoute = null;
    private boolean usingAlternativeRoute = false;

    public SimpleRouter() {

    }

    @Override
    public String toString() {
        return "SimpleRouter{" +
                "dirMap=" + dirMap +
                ", alternativeRoute=" + alternativeRoute +
                ", originalRoute=" + originalRoute +
                ", usingAlternativeRoute=" + usingAlternativeRoute +
                '}';
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
        return getNumRoutes() > 2
                &&
                !isFork();
    }
    @Override
    public boolean isFork(){
        return alternativeRoute!=null;
    }

    @Override
    public void clear() {
        dirMap.clear();
        alternativeRoute=null;
    }

    private boolean allRoutesAreStright(){
        return dirMap.entrySet().stream()
                .filter(t -> !t.getKey().isStraight(t.getValue()))
                .count() == 0;
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
        //ruta repetida
        if (dirMap.containsKey(from) && dirMap.get(from).equals(to)) {
            return;
        }

        //ruta adicional para la ruta from
        if (dirMap.containsKey(from) ){//&& !dirMap.containsKey(to)) {
            originalRoute = new Pair<>(from, dirMap.get(from));
            alternativeRoute = new Pair<>(from, to);
            usingAlternativeRoute=true;
        }

        //ruta adicional para la ruta to
        if (dirMap.containsKey(to) ){//&& !dirMap.containsKey(from)) {
            originalRoute = new Pair<>(to, dirMap.get(to));
            alternativeRoute = new Pair<>(to, from);
            usingAlternativeRoute=true;
        }
        // agregamos la nueva ruta en ambos sentidos
        dirMap.put(from, to);
        dirMap.put(to, from);
    }

    @Override
    public void removeRoute(Dir from, Dir to) {
        dirMap.remove(from);
        dirMap.remove(to);
        if(alternativeRoute!=null) {
            if (from.equals(alternativeRoute.getKey()) && to.equals(alternativeRoute.getValue())) {
                alternativeRoute = null;
                addRoute(originalRoute.getKey(), originalRoute.getValue());
            }
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
    public boolean flipRoute(){
        if(usingAlternativeRoute){
            setNormalRoute();
            return false;
        }else{
            setAlternativeRoute();
            return true;
        }
    }
    @Override
    public boolean isUsingAlternativeRoute(){
        return usingAlternativeRoute;
    }

    @Override
    public void forEach(Consumer<Pair<Dir, Dir>> routeConsumer) {
        dirMap.entrySet().stream()
                .map(t-> new Pair<>(t.getKey(), t.getValue()))
                .forEach(routeConsumer);
    }
}