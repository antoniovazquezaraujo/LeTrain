package letrain.map


import letrain.track.rail.RailTrack
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import spock.lang.Shared
import spock.lang.Specification

class RailTrackMakerTest extends Specification {
    @Mock
    @Shared
    TerrainMap<RailTrack> map
    @Mock
    Point position

    def setup() {
        MockitoAnnotations.initMocks(this)
    }

//    def "test set Position"() {
//        when:
//        maker.setCursorPosition(0, 0)
//        then:
//        maker.getCursorPosition().equals(new Point(0, 0))
//    }
//
//    def "test advance creates curves"() {
//        RailTrack track
//        given:
//        map = new RailMap()
//        maker.setMap(map)
//        maker.setCursorPosition(0, 0)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.setCursorDirection(Dir.E)
//
//        for (int x = 0; x < 8; x++) {
//            when:
//            maker.advanceCursor()
//            track = map.getTrackAt(maker.getCursorPosition())
//            maker.rotateCursorRight()
//            then:
//            track.isCurve()
//        }
//    }
//
//    @Ignore
//    def "test forks"() {
//        map = new RailMap()
//        maker.setMap(map)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//
//        maker.setCursorPosition(8, 5)
//        maker.setCursorDirection(Dir.E)
//        when:
//        for (int n = 0; n < 8; n++) {
//            maker.advanceCursor(3)
//            maker.setMode(Model.Mode.MAP_WALK)
//            maker.reverseCursor()
//            maker.advanceCursor(3)
//            maker.reverseCursor()
//            maker.advanceCursor()
//            maker.rotateCursorRight()
//            maker.setMode(Model.Mode.MAKE_TRACKS)
//            maker.advanceCursor()
//        }
//
//        then:
//        map.getTrackAt(new Point(a, b)).getRouter().isFork()
//        where:
//        a|b
//        7|5
//        9|5
//        5|7
//        11|7
//        5|9
//        11|9
//        7|11
//        9|11
//    }
//
////    def info(RailMap map) {
////        println "Map:["
////        map.forEach(new Consumer<RailTrack>() {
////            @Override
////            void accept(RailTrack t) {
////                println "     Pos:" + t.getCursorPosition().getX() + ", " + t.getCursorPosition().getY()
//////+ " is curve:"+ t.getRouter().isCurve()
////                t.getRouter().forEach new Consumer<Route>() {
////                    @Override
////                    void accept(Route route) {
////                        print "     " + route.getSource().value + "-> " + route.getTarget().value + ", "
////                    }
////
////                }
////                println " "
////            }
////        })
////        println "]"
////    }
//
//
//    def "test crosses"() {
//        map = new RailMap()
//        maker.setMap(map)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//
//        when:
//        maker.setCursorPosition(4, 0)
//        maker.setCursorDirection(Dir.S)
//        maker.advanceCursor(10)
//
//        maker.setMode(Model.Mode.MAP_WALK)
//        maker.setCursorPosition(1, 2)
//        maker.setCursorDirection(Dir.E)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.advanceCursor(12)
//
//        maker.setMode(Model.Mode.MAP_WALK)
//        maker.setCursorPosition(2, 6)
//        maker.setCursorDirection(Dir.NE)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.advanceCursor(6)
//
//        maker.setMode(Model.Mode.MAP_WALK)
//        maker.setCursorPosition(5, 9)
//        maker.setCursorDirection(Dir.NE)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.advanceCursor(7)
//
//        maker.setMode(Model.Mode.MAP_WALK)
//        maker.setCursorPosition(2, 5)
//        maker.setCursorDirection(Dir.SE)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.advanceCursor(5)
//
//        maker.setMode(Model.Mode.MAP_WALK)
//        maker.setCursorPosition(7, 0)
//        maker.setCursorDirection(Dir.SE)
//        maker.setMode(Model.Mode.MAKE_TRACKS)
//        maker.advanceCursor(5)
//        then:
//        true
//        map.getTrackAt(new Point(a, b)).isCross()
//        where:
//        a | b
//        4 | 2
//        4 | 4
//        6 | 2
//        4 | 7
//        9 | 2
//    }
}
