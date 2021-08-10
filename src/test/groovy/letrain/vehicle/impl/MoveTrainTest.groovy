package letrain.vehicle.impl

import letrain.map.RailMapFactory
import letrain.map.TestCircuit1Trait
import letrain.mvp.impl.Model
import letrain.vehicle.impl.rail.TrainFactory
import spock.lang.Specification

class MoveTrainTest extends Specification implements TestCircuit1Trait {

    Model model;

    def setup() {
        model = new Model();
        RailMapFactory railMapFactory = new RailMapFactory(model);
        railMapFactory.read("40,20 e30 r1 r1 r1 r35 r1 r1 r1 r5 l5 r5 r5 l3");
        TrainFactory trainFactory = new TrainFactory(model);
        trainFactory.read("50,20 e #Letrain");
    }

    def "mover el tren por la via"() {

    }

}

