package letrain.map

import letrain.track.Track
import letrain.track.rail.RailTrack
import org.mockito.MockitoAnnotations
import spock.lang.Specification

import java.util.function.Consumer

class RailMapTest extends Specification {
    RailMap railMap = new RailMap()

    def setup() {
        MockitoAnnotations.initMocks(this)
    }

    def "test for Each"() {
        given:
        List<Track> tracks = new ArrayList<>();
        Point pos = new Point(33, 33)
        Point pos2 = new Point(323, 323)
        boolean todoCorrecto = true

        when:
        for (int n = 0; n < 10; n++) {
            def track = new RailTrack()
            tracks.add(track)
            railMap.addTrack(new Point(n, n), track)
        }
        a.each {
            if (tracks.get(it).getPosition().getX() != tracks.get(it).getPosition().getY()) {
                todoCorrecto = false
            }
        }

        then:
        todoCorrecto == true

        when:
        railMap.forEach new Consumer<RailTrack>() {
            @Override
            void accept(RailTrack track) {
                track.setPosition(pos)
            }
        }
        todoCorrecto = true
        a.each {
            if (pos != tracks.get(it).getPosition()) {
                todoCorrecto = false
            }
        }

        then:
        todoCorrecto == true

        where:

        a << [0..9]

    }

    def "test add remove and get "() {
        def track = new RailTrack()
        when:
        railMap.addTrack(new Point(100, 100), track)
        RailTrack result = railMap.getTrackAt(100, 100)

        then:

        result == track
        railMap.getTrackAt(10, 10) == null

        when:

        railMap.removeTrack(100, 100)
        then:
        railMap.getTrackAt(100, 100) == null

    }

}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme