package letrain.map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.function.Consumer;
import letrain.utils.Pair;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = letrain.map.impl.SimpleRouter.class, name = "SimpleRouter"),
        @JsonSubTypes.Type(value = letrain.map.impl.ForkRouter.class, name = "ForkRouter")})
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
