package letrain.map

import letrain.track.rail.RailTrack
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class RailTrackMakerTest extends Specification {
    @Mock
    @Shared
    TerrainMap<RailTrack> map
    @Mock
    Point position
    @InjectMocks
    RailTrackMaker maker

    def setup() {
        MockitoAnnotations.initMocks(this)
    }

    def "test set Position"() {
        when:
        maker.setPosition(0, 0)
        then:
        maker.getPosition().equals(new Point(0, 0))
    }

    def "test advance creates curves"() {
        RailTrack track
        given:
        map = new RailMap()
        maker.setMap(map)
        maker.setPosition(0, 0)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.setDirection(Dir.E)
        int n = 3

        for (int x = 0; x < 8; x++) {
            when:
            maker.advance()
            track = map.getTrackAt(maker.getPosition())
            maker.rotateRight()
            then:
            track.isCurve()
        }
    }

    @Ignore
    def "test forks"() {
        RailTrack track
        map = new RailMap()
        maker.setMap(map)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)

        maker.setPosition(8, 5)
        maker.setDirection(Dir.E)
        when:
        for (int n = 0; n < 8; n++) {
            maker.advance(3)
            maker.setMode(TrackMaker.Mode.MAP_WALK)
            maker.reverse()
            maker.advance(3)
            maker.reverse()
            maker.advance()
            maker.rotateRight()
            maker.setMode(TrackMaker.Mode.MAKE_TRACK)
            maker.advance()
        }

        then:
        map.getTrackAt(new Point(a, b)).getRouter().isFork() == true
        where:
        a|b
        7|5
        9|5
        5|7
        11|7
        5|9
        11|9
        7|11
        9|11
    }

//    def info(RailMap map) {
//        println "Map:["
//        map.forEach(new Consumer<RailTrack>() {
//            @Override
//            void accept(RailTrack t) {
//                println "     Pos:" + t.getPosition().getX() + ", " + t.getPosition().getY()
////+ " is curve:"+ t.getRouter().isCurve()
//                t.getRouter().forEach new Consumer<Route>() {
//                    @Override
//                    void accept(Route route) {
//                        print "     " + route.getSource().value + "-> " + route.getTarget().value + ", "
//                    }
//
//                }
//                println " "
//            }
//        })
//        println "]"
//    }


    def "test crosses"() {
        RailTrack track
        map = new RailMap()
        maker.setMap(map)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)

        when:
        maker.setPosition(4, 0)
        maker.setDirection(Dir.S)
        maker.advance(10)

        maker.setMode(TrackMaker.Mode.MAP_WALK);
        maker.setPosition(1, 2)
        maker.setDirection(Dir.E)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(12)

        maker.setMode(TrackMaker.Mode.MAP_WALK);
        maker.setPosition(2, 6)
        maker.setDirection(Dir.NE)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(6)

        maker.setMode(TrackMaker.Mode.MAP_WALK);
        maker.setPosition(5, 9)
        maker.setDirection(Dir.NE)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(7)

        maker.setMode(TrackMaker.Mode.MAP_WALK);
        maker.setPosition(2, 5)
        maker.setDirection(Dir.SE)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(5)

        maker.setMode(TrackMaker.Mode.MAP_WALK);
        maker.setPosition(7, 0)
        maker.setDirection(Dir.SE)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(5)
        then:
        true
        map.getTrackAt(new Point(a, b)).isCross()
        where:
        a | b
        4 | 2
        4 | 4
        6 | 2
        4 | 7
        9 | 2
    }
}
