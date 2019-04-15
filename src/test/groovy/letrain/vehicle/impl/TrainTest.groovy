package letrain.vehicle.impl.rail

import letrain.map.Point
import letrain.track.rail.RailTrack
import letrain.vehicle.Linkable
import letrain.vehicle.impl.Linker
import letrain.vehicle.impl.Tractor
import spock.lang.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import static org.mockito.Mockito.*

class TrainTest extends Specification {

    List<Train> trains = new ArrayList<>();
    Train train1 = new Train()
    Wagon wagon1 = new Wagon()
    Wagon wagon2 = new Wagon()
    Tractor locomotive1 = new Locomotive()
    Tractor locomotive2 = new Locomotive()

    def setup() {

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
        Train oldTrain = train1.join(train2)
        then:
        oldTrain.isEmpty()
        train1.size().equals(2)

    }
    def "Seleccionar y obtener el mainTractor de un tren"(){
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
        train1.getMass().equals(0)

        when:
        train1.pushFront(wagon1)
        then:
        train1.getMass().equals(1)

        when:
        train1.pushFront(wagon2)
        then:
        train1.getMass().equals(2)

        when:
        train1.popFront()
        then:
        train1.getMass().equals(1)

        when:
        train1.popFront()
        then:
        train1.getMass().equals(0)
    }
    def "Consumir la energía para mover el tren"(){
        when:
        train1.addEnergy(10)
        train1.consumeEnergy(5)
        then:
        train1.getEnergy().equals(5)

        when:
        train1.consumeEnergy(5)
        then:
        train1.getEnergy().equals(0)

    }
    def "suma de fuerzas en un tren"(){
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)
        train1.applyForces()

        then:
        train1.getForce().equals(40)



    }
    def "aceleración es fuerza dividida por masa"(){
        when:
        locomotive1.setForce(20)
        locomotive1.setMass(10)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        locomotive2.setMass(10)
        train1.pushFront(locomotive2)
        then:
        train1.getMass().equals(20)

        when:
        train1.applyForces()
        then:
        train1.getForce().equals(40)
        train1.getAcceleration().equals((float)(40/20))
    }
    def "aceleración es lo que varió la velocidad en cada turno"(){

    }
    def "velocidad varía en cada turno según aceleración"(){
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.setFrictionCoefficient(0.2)
        train1.pushFront(locomotive2)
        then:
        train1.getTotalMass().equals(20)
        train1.getSpeed().equals(0)
        train1.distanceTraveled().equals(0)

        when:
        train1.applyForces()
        then:
        train1.getTotalForce().equals(train1.getTractorsForce()-train1.getFrictionForce())
        train1.getAcceleration().equals(train1.getTotalForce()/getTotalMass())
        train1.getSpeed().equals(2)
        train1.distanceTraveled().equals(2)
        when:
        train1.applyForces()
        then:
        train1.getAcceleration().equals(2)
        train1.getSpeed().equals(4)
        train1.distanceTraveled().equals(6)
        when:
        train1.applyForces()
        then:
        train1.getAcceleration().equals(2)
        train1.getSpeed().equals(6)
        train1.distanceTraveled().equals(12)
        when:
        train1.applyForces()
        then:
        train1.getAcceleration().equals(2)
        train1.getSpeed().equals(8)
        train1.distanceTraveled().equals(20)

        //train is moved
        when:
        train1.resetDistanceTraveled()
        train1.distanceTraveled().equals(0)
        train1.applyForces()
        then:
        train1.getSpeed().equals(10)
        train1.distanceTraveled().equals(10)

        when:
        train1.applyForces()
        then:
        train1.getSpeed().equals(12)
        train1.getDistanceTraveled().equals(22)

        //train is moved
        when:
        train1.resetDistanceTraveled()
        train1.distanceTraveled().equals(2) // sobraron 2 metros
        locomotive1.setForce(0)
        locomotive2.setForce(0)
        train1.applyForces()

        then:
        train1.getFrictionForce().equals(train1.getTotalMass()*train1.getAcceleration())
        train1.getAcceleration().equals(train1.getTotalForce()-train1.getFrictionForce())
        train1.getSpeed().equals(10)
        train1.getDistanceTraveled().equals(10)

        when:
        train1.applyForces()
        then:
        train1.getSpeed().equals(12)
        train1.distanceTraveled().equals(22)





    }
    def "espacio recorrido en cada turno varía según velocidad"(){

    }
    def "si espacio recorrido supera unidad de movimiento, tren se moverá"(){

    }

}

