package letrain.vehicle.impl

import letrain.map.*
import letrain.track.rail.RailTrack
import letrain.vehicle.Linkable
import letrain.vehicle.impl.rail.Wagon
import spock.lang.Specification

class LinkerTest extends Specification {

    Linker linker1 = new Wagon();
    Linker linker2 = new Wagon();

    def setup() {
    }

    def "test link"() {
        when:
        linker1.link(Linkable.LinkSide.FRONT, linker2)
        then:
        linker1.getLinked(Linkable.LinkSide.FRONT).equals(linker2)
    }

    def "test unlink"() {
        when:
        linker1.link(Linkable.LinkSide.FRONT, linker2)
        Linkable result = linker1.unlink(Linkable.LinkSide.FRONT)
        then:
        result == linker2
    }

    def "test advance in curves"() {
        given:
        RailMap map = new RailMap()
        RailTrackMaker maker = new RailTrackMaker()
        maker.setMap(map)
        maker.setPosition(0, 0)
        maker.setDirection(Dir.E)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(2)
        maker.rotateRight()
        maker.advance(2)
        maker.rotateLeft()
        maker.advance(2)

        when:
        RailTrack track = map.getTrackAt(0, 0)
        track.enterLinker(Dir.W, linker1)

        then:
        linker1.getPosition().equals(new Point(0, 0))
        linker1.advance()
        linker1.getPosition().equals(new Point(1, 0))
        linker1.advance()
        linker1.getPosition().equals(new Point(2, 0))
        linker1.advance()
        linker1.getPosition().equals(new Point(3, 1))
        linker1.advance()
        linker1.getPosition().equals(new Point(4, 2))
        linker1.advance()
        linker1.getPosition().equals(new Point(5, 2))
        linker1.advance()
        linker1.getPosition().equals(new Point(6, 2))
    }

    def "test advance in crosses"() {
        given:
        RailMap map = new RailMap()
        RailTrackMaker maker = new RailTrackMaker()
        maker.setMap(map)
        maker.setPosition(10, 10)
        maker.setDirection(Dir.E)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(10)
        maker.setDirection(Dir.S)
        maker.setPosition(12, 8)
        maker.advance(10)

        when:
        RailTrack track = map.getTrackAt(10, 10)
        track.enterLinker(Dir.W, linker1)

        then:
        linker1.getPosition().equals(new Point(10, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(11, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(13, 10))


        RailTrack track2 = map.getTrackAt(12, 9)
        linker1.setTrack(null);
        linker1.setPosition(new Point(0, 0))
        linker1.setDir(Dir.S)
        track2.enterLinker(Dir.N, linker1)


        linker1.getPosition().equals(new Point(12, 9))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 11))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 12))
    }

    def "test advance in forks"() {
        given:
        RailMap map = new RailMap()
        RailTrackMaker maker = new RailTrackMaker()
        maker.setMap(map)
        maker.setPosition(10, 10)
        maker.setDirection(Dir.E)
        maker.setMode(TrackMaker.Mode.MAKE_TRACK)
        maker.advance(4)

        maker.setPosition(10, 10)
        maker.setDirection(Dir.E)
        maker.advance(2)
        maker.rotateRight()
        maker.advance(2)

        when:
        RailTrack track = map.getTrackAt(10, 10)
        track.enterLinker(Dir.W, linker1)

        then:
        map.getTrackAt(12, 10).isUsingAlternativeRoute()
        linker1.getPosition().equals(new Point(10, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(11, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(13, 11))
        linker1.advance()
        linker1.getPosition().equals(new Point(14, 12))


        map.getTrackAt(12, 10).setNormalRoute()
        !map.getTrackAt(12, 10).isUsingAlternativeRoute()
        RailTrack track2 = map.getTrackAt(10, 10)
        linker1.setTrack(null);
        linker1.setPosition(new Point(0, 0))
        linker1.setDir(Dir.S)
        track2.enterLinker(Dir.W, linker1)


        linker1.getPosition().equals(new Point(10, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(11, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(12, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(13, 10))
        linker1.advance()
        linker1.getPosition().equals(new Point(14, 10))
    }


}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme