package letrain.vehicle.impl.rail

import letrain.track.rail.RailTrack
import letrain.vehicle.impl.Linker
import letrain.vehicle.impl.Tractor
import spock.lang.*

class TrainTest extends Specification {

    final double PRECISION = 0.001
    List<Train> trains = new ArrayList<>()
    Train train1 = new Train()
    Wagon wagon1 = new Wagon()
    Wagon wagon2 = new Wagon()
    Linker<RailTrack> locomotive1 = new Locomotive()
    Linker<RailTrack> locomotive2 = new Locomotive()

    def setup() {

    }
    def compare(float f1, float f2){
        return (float)(f1-f2)
    }
    def "Crear y eliminar un nuevo tren"(){

        when:
        trains.add(train1)
        then:
        trains.get(0).equals(train1)

        when:
        train1.pushFront(wagon1)
        then:
        train1.getFront().equals(wagon1)

        when:
        train1.popFront()
        then:
        train1.isEmpty()

        when:
        trains.remove(0)
        then:
        trains.isEmpty()

    }
    def "Crear otro tren a partir de dividir uno"(){
        when:
        trains.add(train1)
        train1.pushFront(wagon1)
        train1.pushFront(wagon2)
        Train train2 = train1.divide(wagon1)
        then:
        train1.size().equals(1)
        train2.size().equals(1)
    }
    def "Unir dos trenes"(){
        when:
        train1.pushFront(wagon1)
        Train train2 = new Train()
        train2.pushFront(wagon2)

        train1.pushBack(train2)
        then:
        train2.isEmpty()
        train1.size().equals(2)

    }
    def "Seleccionar y obtener el mainTractor de un tren"() {
        when:
        train1.pushFront(wagon1)
        train1.pushFront(wagon2)
        train1.pushFront(locomotive1)
        then:
        train1.getMainTractor().equals(null)

        when:
        train1.setMainTractor(locomotive1)
        then:
        train1.getMainTractor().equals(locomotive1)
    }
    def "Obtener la masa de todo un tren"(){
        expect:
        compare(train1.getTotalMass(),0.0F)<= PRECISION

        when:
        train1.pushFront(wagon1)
        then:
        compare(train1.getTotalMass(),wagon1.getMass())<= PRECISION

        when:
        train1.pushFront(wagon2)
        then:
        compare(train1.getTotalMass(),(float)(wagon1.getMass()+wagon2.getMass()))<= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getTotalMass(),wagon1.getMass())<= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getTotalMass(),0.0F)<= PRECISION
    }
    def "suma de fuerzas en un tren"(){
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)

        then:
        train1.getTractors().contains(locomotive1)
        train1.getTractors().contains(locomotive2)
        compare(train1.getTotalForce(),(float)(locomotive1.getForce()+ locomotive2.getForce()))<= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getTotalForce(),(float)((locomotive1.getForce()+ locomotive2.getForce())-train1.getFrictionForce()))<= PRECISION
    }
    def "aceleración es fuerza dividida por masa"(){
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)

        then:
        compare(train1.getAcceleration(), 0.0F)<= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getAcceleration(), (float)(train1.getTotalForce()/train1.getTotalMass()))<= PRECISION
    }
    def "aceleración es lo que varió la velocidad en cada turno"(){

    }
    def "velocidad varía en cada turno según aceleración"(){
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)
        then:
        compare(train1.getTotalMass(), (float)(locomotive1.getMass()+ locomotive2.getMass()))<PRECISION
        compare(train1.getSpeed(), 0.0f)<= PRECISION
        compare(train1.getDistanceTraveled(), 0.0f) <= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(),train1.getSpeed()) <= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(),train1.getSpeed()) <= PRECISION

        when:
        train1.applyForces()
        then:
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(),train1.getSpeed()) <= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(),train1.getSpeed()) <= PRECISION

        //train is moved
        when:
        train1.resetDistanceTraveled()
        then:
        compare(train1.getDistanceTraveled(),0.0) <= PRECISION

        when:
        train1.applyForces()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(),train1.getSpeed()) <= PRECISION

        when:
        locomotive1.setForce(0)
        locomotive2.setForce(0)
        train1.applyForces()

        then:
        compare(train1.getTotalForce(), (float) (-1*train1.getFrictionForce())) <= PRECISION

    }
    def "espacio recorrido en cada turno varía según velocidad"(){

    }
    def "si espacio recorrido supera unidad de movimiento, tren se moverá"(){

    }

}

