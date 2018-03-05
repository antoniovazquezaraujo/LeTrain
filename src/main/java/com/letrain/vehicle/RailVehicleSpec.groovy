package com.letrain.vehicle

import com.letrain.rail.Rail
import com.letrain.rail.StraightRail
import spock.lang.Specification

import static com.letrain.dir.Dir.N
import static com.letrain.dir.Dir.S


class RailVehicleSpec extends Specification {
    def "push"() {
        given:
        RailVehicle v = new RailVehicle();
        RailVehicle other = new RailVehicle();
        Rail r = new StraightRail();

        when:
        v.setDir(N)
        r.addPath(N, S)
        v.gotoRail(r)
        other.setDir(S)
        v.setDir(v.push(other))

        then:
        v.getDir() == S
    }
}