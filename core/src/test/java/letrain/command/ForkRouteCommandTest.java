package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.rail.ForkRailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code fork N set straight|curved;} DSL command is what a recorded keyboard flip replays through
 * (ADR-020): a scenario rebuilds the fork from the {@code write} recipe and then restores its route.
 */
@DisplayName("Direct fork route command (fork N set straight|curved)")
class ForkRouteCommandTest {

    /** A fork splitting an incoming west line into east (normal) and south (alternative). */
    private static ForkRailTrack addFork(Model model, int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.setCreationDir(Dir.E);
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.S);
        fork.addRoute(Dir.S, Dir.W);
        fork.setNormalRoute();
        model.getRailMap().addTrack(fork.getPosition(), fork);
        model.addFork(fork);
        return fork;
    }

    private static void run(Model model, String script) {
        String error = PlayerCommandExecutor.execute(script, model, null, null, null);
        assertNull(error, error);
    }

    @Test
    @DisplayName("set curved selects the alternative route and set straight the normal one")
    void setRoute() {
        Model model = new Model(1);
        ForkRailTrack fork = addFork(model, 0, 0);
        assertFalse(fork.isUsingAlternativeRoute(), "forks start on the normal route");

        run(model, "go 0,0; face e; fork " + fork.getId() + " set curved;");
        assertTrue(fork.isUsingAlternativeRoute());

        run(model, "go 0,0; face e; fork " + fork.getId() + " set straight;");
        assertFalse(fork.isUsingAlternativeRoute());
    }
}
