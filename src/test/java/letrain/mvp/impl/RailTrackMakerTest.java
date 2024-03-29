package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RailTrackMakerTest {

        @ParameterizedTest(name = "fromInt({0})")
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
        void testConnectTrack(Dir from) {
                RailTrackMaker maker = new RailTrackMaker(null);
                RailTrack track = new RailTrack();
                ForkRailTrack fork = new ForkRailTrack(1);
                Dir to = from.inverse();
                Dir toTurnedLeft = to.turnLeft();
                track.addRoute(from, to);
                fork.addRoute(from, toTurnedLeft);
                maker.addTrackConnectionsToFork(track, fork);
                // System.out.println("T:" + track);
                // System.out.println("F:" + fork);

        }
}
