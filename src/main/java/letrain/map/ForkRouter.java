package letrain.map;

import javafx.util.Pair;

public class ForkRouter extends SimpleRouter implements DynamicRouter {
    private Pair<Dir, Dir> alternativeRoute= null;
    private Pair<Dir, Dir> originalRoute = null;
    private boolean usingAlternativeRoute = false;

    @Override
    public Dir getFirstOpenDir() {
        if(isUsingAlternativeRoute()){
            return alternativeRoute.getValue();
        }else{
            return originalRoute.getValue();
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
    public void addRoute(Dir from, Dir to) {
        //ruta repetida
        if (dirMap.containsKey(from) && dirMap.get(from).equals(to)) {
            return;
        }

        //ruta adicional para la ruta from
        if (dirMap.containsKey(from)) {//&& !dirMap.containsKey(to)) {
            originalRoute = new Pair<>(from, dirMap.get(from));
            alternativeRoute = new Pair<>(from, to);
            usingAlternativeRoute = true;
        }

        //ruta adicional para la ruta to
        if (dirMap.containsKey(to)) {//&& !dirMap.containsKey(from)) {
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
