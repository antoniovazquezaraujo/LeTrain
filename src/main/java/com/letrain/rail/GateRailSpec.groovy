package com.letrain.rail

import static com.letrain.dir.Dir.*
import com.letrain.vehicle.RailVehicle
import com.letrain.vehicle.Train
import spock.lang.Specification
import com.letrain.map.Point

class GateRailSpec extends Specification {
    def ""() {
        given:
        Train t = new Train()
        RailVehicle v1 = new RailVehicle()
        RailVehicle v2 = new RailVehicle()
        Rail r1 = new StraightRail()

        when:
        r1.setPos(0,0)
        t.setPos(new Point(0,0))
        t.setDir(E)
        r1.addPath(W, E)
        v1.setDir(E)
        v2.setDir(E)
        t.addVehicle(Train.TrainSide.BACK,v1)
        t.addVehicle(Train.TrainSide.BACK, v2)
        Rail r2 = r1.linkStraight(W )
        Rail r3 = r2.linkStraight(W )

        GateRail gateRail = (GateRail)(r1.linkGate(E, null))
        t.gotoRail(r1)
        r3.getVehicle() == v2
        r2.getVehicle() == v1
        r1.getVehicle() == t
        t.getRail() == r1
        v1.getRail() == r2
        v2.getRail() == r3
        t.getRail() == gateRail
        t.moveTrain()
        r3.getVehicle() == null
        r2.getVehicle() == v2
        r1.getVehicle() == v1
        v1.getRail() == r1
        v2.getRail() == r2
        t.getRail() == gateRail
        then:
        gateRail.numVehiclesInside == 1
        t.moveTrain()
        gateRail.numVehiclesInside == 2

     }
}