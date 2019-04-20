package letrain.map;

public interface DynamicRouter extends Router{
    void setAlternativeRoute();

    void setNormalRoute();

    boolean flipRoute();

    boolean isUsingAlternativeRoute();
}
