package letrain.track

import letrain.map.Dir
import letrain.track.rail.RailTrack
import letrain.vehicle.Vehicle
import letrain.vehicle.impl.rail.Wagon
import spock.lang.Specification

class RailTrackTest extends Specification {
    RailTrack track

    def setup() {
        track = new RailTrack();
        track.addRoute(Dir.N, Dir.S)
        RailTrack connected = new RailTrack()
        track.connect(Dir.S, connected)
    }

    def cleanup() {
        track.clear();
    }

    def "test get Any Dir"() {
        when:
        track.clear()
        track.addRoute(Dir.E, Dir.SE)
        def result = track.getAnyDir()
        then:
        result == Dir.E || result == Dir.SE

    }

    def "test get Dir"() {
        when:
        track.clear()
        track.addRoute(Dir.N, Dir.SE)
        track.addRoute(Dir.SE, Dir.N)
        then:
        Dir.SE == track.getDir(Dir.N)
        Dir.N == track.getDir(Dir.SE)
        null == track.getDir(Dir.E)
    }


    def "test get Num Open Dirs"() {
        when:
        track.addRoute(Dir.W, Dir.SE)
        track.addRoute(Dir.E, Dir.W)
        track.addRoute(Dir.S, Dir.N)
        then:
        5 == track.getNumRoutes()
    }

    def "test add Route"() {
        when:
        track.addRoute(Dir.E, Dir.W)

        then:
        Dir.W == track.getDir(Dir.E)
        Dir.E == track.getDir(Dir.W)

    }

    def "test remove Route"() {
        when:
        track.addRoute(Dir.E, Dir.W)
        track.removeRoute(Dir.W, Dir.E)

        then:
        null == track.getDir(Dir.E)
        null == track.getDir(Dir.W)
    }

    def "test link"() {
        given:
        RailTrack other = new RailTrack()

        when:
        track.connect(Dir.S, other)
        then:
        other.equals(track.getConnected(Dir.S))
    }

    def "test unLink"() {
        given:
        RailTrack other = new RailTrack()

        when:
        track.connect(Dir.S, other)
        track.disconnect(Dir.S)
        then:
        null == track.getConnected(Dir.S)
    }

    def "test addVehicle"() {
        given:
        Vehicle<RailTrack> v = new Wagon()
        boolean added = false
        when:
        added = track.enterLinkerFromDir(Dir.S, v)
        then:
        if (added) {
            v == track.getLinker()
        } else {
            false
        }
    }

    def "test add two Vehicles"() {
        given:
        Vehicle<RailTrack> v = new Wagon()
        Vehicle<RailTrack> v2 = new Wagon()
        boolean added = false
        when:
        added = track.enterLinkerFromDir(Dir.S, v)
        added = track.enterLinkerFromDir(Dir.N, v2)
        then:
        if (added) {
            false
        } else {
            true
        }
    }

    def "test removeVehicle"() {
        given:
        Vehicle<RailTrack> v = new Wagon()
        when:
        track.enterLinkerFromDir(Dir.N, v)
        track.removeLinker()
        then:
        null == track.getLinker()

    }

    def "test removeVehicle when empty"() {
        when:
        track.removeLinker()
        then:
        null == track.getLinker()
    }

 }

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme