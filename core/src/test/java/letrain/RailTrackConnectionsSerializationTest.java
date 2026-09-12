package letrain;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rail track adjacency survives serialization of a long line")
class RailTrackConnectionsSerializationTest {

    @Test
    @DisplayName("a 600-tile connected line serializes and its connections are rebuilt on load")
    void longLine_roundTrip_rebuildsConnections() {
        Model model = new Model(1);
        RailMap rails = model.getRailMap();
        RailTrack previous = null;
        for (int x = 0; x < 600; x++) {
            RailTrack track = new RailTrack();
            track.addRoute(Dir.E, Dir.W);
            track.addRoute(Dir.W, Dir.E);
            track.setPosition(new Point(x, 0));
            rails.addTrack(track.getPosition(), track);
            if (previous != null) {
                previous.connect(Dir.E, track);
                track.connect(Dir.W, previous);
            }
            previous = track;
        }

        // Before: serializing the adjacency graph recursively blew Jackson's nesting limit (~600
        // tiles -> depth > 1000).
        byte[] bytes = new GameSaveService().toBytes(model);
        assertNotNull(bytes);

        Model restored = new GameSaveService().fromBytes(bytes);
        assertNotNull(restored);
        RailTrack middle = (RailTrack) restored.getRailMap().getTrackAt(300, 0);
        assertNotNull(middle, "the middle track must be present");
        assertNotNull(middle.getConnected(Dir.E), "east neighbour must be rebuilt");
        assertNotNull(middle.getConnected(Dir.W), "west neighbour must be rebuilt");
    }
}
