package letrain.vehicle.impl

import letrain.map.*
import letrain.mvp.GameModel
import letrain.track.rail.RailTrack
import letrain.vehicle.impl.rail.Wagon
import spock.lang.Specification

class LinkerTest extends Specification {

    Linker linker1 = new Wagon()


    def setup() {
    }


//    def "test advance in curves"() {
//        given:
//        RailMap map = new RailMap()
//
//        when:
//        RailTrack track = map.getTrackAt(0, 0)
//        track.enterLinkerFromDir(Dir.W, linker1)
//
//        then:
//        linker1.getPosition().equals(new Point(0, 0))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(1, 0))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(2, 0))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(3, 1))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(4, 2))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(5, 2))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(6, 2))
//    }
//
//    def "test advance in crosses"() {
//        given:
//        RailMap map = new RailMap()
//
//
//        when:
//        RailTrack track = map.getTrackAt(10, 10)
//        track.enterLinkerFromDir(Dir.W, linker1)
//
//        then:
//        linker1.getPosition().equals(new Point(10, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(11, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(13, 10))
//
//
//        RailTrack track2 = map.getTrackAt(12, 9)
//        linker1.setTrack(null);
//        linker1.setPosition(new Point(0, 0))
//        linker1.setDir(Dir.S)
//        track2.enterLinkerFromDir(Dir.N, linker1)
//
//
//        linker1.getPosition().equals(new Point(12, 9))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 11))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 12))
//    }
//
//    def "test advance in forks"() {
//        given:
//        RailMap map = new RailMap()
//
//
//        when:
//        RailTrack track = map.getTrackAt(10, 10)
//        track.enterLinkerFromDir(Dir.W, linker1)
//
//        then:
//        map.getTrackAt(12, 10).isUsingAlternativeRoute()
//        linker1.getPosition().equals(new Point(10, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(11, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(13, 11))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(14, 12))
//
//
//        map.getTrackAt(12, 10).setNormalRoute()
//        !map.getTrackAt(12, 10).isUsingAlternativeRoute()
//        RailTrack track2 = map.getTrackAt(10, 10)
//        linker1.setTrack(null);
//        linker1.setPosition(new Point(0, 0))
//        linker1.setDir(Dir.S)
//        track2.enterLinkerFromDir(Dir.W, linker1)
//
//
//        linker1.getPosition().equals(new Point(10, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(11, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(12, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(13, 10))
//        linker1.advance()
//        linker1.getPosition().equals(new Point(14, 10))
//    }
//

}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme