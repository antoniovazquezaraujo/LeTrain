package com.letrain.rail

import com.letrain.map.Point
import com.letrain.vehicle.RailVehicle
import spock.lang.Specification
import spock.lang.Unroll

import static com.letrain.dir.Dir.*

class RailSpec extends Specification {
    def "when vehicle enters exit by the correct direction"() {
        given:
        RailVehicle v = new RailVehicle()

        expect:
        Rail rail = new StraightRail()
        rail.addPath(dirFrom, dirTo)
        v.setDir(dirEnter)
        rail.enterVehicle(v)
        v.getDir() == dirExit

        where:
        dirFrom | dirTo | dirEnter || dirExit
        N       | SE    | NW       || N
        E       | W     | W        || W
        W       | NE    | E        || NE
        W       | NE    | N        || null
    }

    def "when vehicle enters obtains the rail position"() {
        given:
        RailVehicle v = new RailVehicle()

        expect:
        Rail rail = new StraightRail()
        rail.addPath(N, S)
        rail.setPos(new Point(row, col))
        v.setDir(S)
        rail.enterVehicle(v)
        v.getPos() == new Point(row, col)

        where:
        row   | col
        0     | 0
        1     | 0
        -1    | 1
        -1    | -1
        1000  | 1000
        -1000 | -1000
    }

    def "when vehicle exits it goes to the linked rail"() {

        given:
        Rail r1 = new StraightRail()
        r1.setPos(new Point(0, 0));
        Rail r2 = new StraightRail()
        RailVehicle vehicle = new RailVehicle()

        when:
        r1.addPath(dirFrom, E)
        r2 = r1.linkStraight(W)
        vehicle.setDir(dirFrom.inverse())
        r1.enterVehicle(vehicle)
        r1.exitVehicle()

        then:
        r2.getVehicle() == vehicle

        where:
        dirFrom << [NW, W, SW]

    }

    @Unroll
    def "link straight rail en todas direcciones"() {
        given:
        Rail r = new StraightRail()
        r.setPos(0, 0)

        expect:
        r.addPath(dir.inverse(), dir)
        Rail rail = r.linkStraight(dir)
        rail.getLinkedRailAt(dir.inverse()) == r
        rail.getPos() == r.getPos().copy(dir)

        where:
        dir << [E, NE, N, NW, W, SW, S, SE]
    }

    @Unroll
    def "link curve rail en todas direcciones"() {
        given:
        Rail r = new CurveRail()
        r.setPos(0, 0)

        expect:
        r.addPath(dir.inverse(), dir)
        Rail rail = r.linkCurve(dir, dir.turnLeft())
        rail.getLinkedRailAt(dir.inverse()) == r
        rail.getPos() == r.getPos().copy(dir)
        rail.getPath(dir.inverse()) == dir.turnLeft()
        where:
        dir << [E, NE, N, NW, W, SW, S, SE]
    }

    def "link fork rail en todas direcciones"() {
        given:
        Rail r = new StraightRail()
        r.setPos(0, 0)

        expect:
        r.addPath(dir.inverse(), dir)
        ForkRail rail = r.linkFork(dir, dir, dir.turnLeft())

        rail.getLinkedRailAt(dir.inverse()) == r
        rail.getPos() == r.getPos().copy(dir)
        rail.selectLeftOut()
        rail.getPath(dir.inverse()) == dir

        rail.selectRightOut()
        rail.getPath(dir.inverse()) == dir.turnLeft()

        where:
        dir << [E, NE, N, NW, W, SW, S, SE]
    }
}
