package letrain.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import letrain.map.impl.ForkRouter;

public class ForkRouterTest {

    // Test de addRoute parametrizado con todas las rutas posibles
    @ParameterizedTest(name = "addRoute({0}, {1}) should return {2}")
    @CsvSource({

            "E ",
            "NE ",
            "N ",
            "NW ",
            "W ",
            "SW ",
            "S ",
            "SE "
    })
    void testAddRoute(Dir from) {
        ForkRouter router = new ForkRouter();
        Dir to = from.inverse();
        Dir toTurnedLeft = to.turnLeft();
        router.addRoute(from, to);
        router.addRoute(from, toTurnedLeft);
        // TODO: to assertions here!
        // System.out.println(router.toString());
    }

}
