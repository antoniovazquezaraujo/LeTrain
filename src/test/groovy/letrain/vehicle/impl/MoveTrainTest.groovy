package letrain.vehicle.impl

import letrain.map.TestCircuit1Trait
import letrain.map.Dir
import letrain.vehicle.impl.Tractor
import letrain.vehicle.impl.rail.Locomotive
import letrain.vehicle.impl.rail.Train
import letrain.vehicle.impl.rail.Wagon
import spock.lang.Specification

class MoveTrainTest extends Specification implements TestCircuit1Trait {

    List<Train> trains = new ArrayList<>()
    Train train1 = new Train()
    Wagon wagon1 = new Wagon('a')
    Wagon wagon2 = new Wagon('b')
    Locomotive locomotive1 = new Locomotive('A')

    def setup() {

    }

    def "mover el tren por la vía"() {
        given:
        train1.pushBack(wagon1)
        train1.pushBack(locomotive1)
        train1.pushBack(wagon2)
        train1.assignDefaultDirectorLinker()
        when:
        railMap.getTrackAt(11, 0).enterLinkerFromDir(Dir.E, wagon1)
        railMap.getTrackAt(12, 0).enterLinkerFromDir(Dir.E, locomotive1)
        railMap.getTrackAt(13, 0).enterLinkerFromDir(Dir.E, wagon2)
        train1.advance()
        then:
        wagon1.getPosition().getX().equals(10)
        locomotive1.getPosition().getX().equals(11)
        wagon2.getPosition().getX().equals(12)

        when:
        train1.advance()
        then:
        wagon1.getPosition().getY().equals(0)
        wagon1.getPosition().getX().equals(9)
        locomotive1.getPosition().getX().equals(10)
        wagon2.getPosition().getX().equals(11)
    }

}

