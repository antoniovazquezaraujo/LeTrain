package letrain.map;

import org.junit.jupiter.api.BeforeEach;

import letrain.map.impl.RailMap;

public class RailMapTest {

    private RailMap railMap;

    @BeforeEach
    void setUp() {
        railMap = new RailMap();
    }

    // @Test
    // @DisplayName("test forEach")
    // void testForEach() {
    // // given
    // List<Track> tracks = new ArrayList<>();
    // Point pos = new Point(33, 33);
    // Point pos2 = new Point(323, 323);
    // boolean todoCorrecto = true;

    // // when
    // for (int n = 0; n < 10; n++) {
    // RailTrack track = new RailTrack();
    // tracks.add(track);
    // railMap.addTrack(new Point(n, n), track);
    // }

    // // then
    // for (int i = 0; i < tracks.size(); i++) {
    // if (tracks.get(i).getPosition().getX() != tracks.get(i).getPosition().getY())
    // {
    // todoCorrecto = false;
    // }
    // }
    // assertTrue(todoCorrecto);

    // // when
    // railMap.forEach(new Consumer<RailTrack>() {
    // @Override
    // public void accept(RailTrack track) {
    // track.setPosition(pos);
    // }
    // });
    // todoCorrecto = true;

    // // then
    // for (int i = 0; i < tracks.size(); i++) {
    // if (!pos.equals(tracks.get(i).getPosition())) {
    // todoCorrecto = false;
    // }
    // }
    // assertTrue(todoCorrecto);
    // }

    // @Test
    // @DisplayName("test add, remove, and get")
    // void testAddRemoveAndGet() {
    // // given
    // RailTrack track = new RailTrack();

    // // when
    // railMap.addTrack(new Point(100, 100), track);
    // RailTrack result = railMap.getTrackAt(100, 100);

    // // then
    // assertEquals(track, result);
    // assertNull(railMap.getTrackAt(10, 10));

    // // when
    // railMap.removeTrack(100, 100);

    // // then
    // assertNull(railMap.getTrackAt(100, 100));
    // }
}
