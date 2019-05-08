package letrain.vehicle.impl.rail

import javafx.scene.input.KeyEvent
import letrain.map.RailMap
import letrain.mvp.View
import letrain.mvp.impl.Model
import letrain.mvp.impl.RailTrackMaker
import letrain.track.rail.RailTrack
import letrain.vehicle.impl.Tractor
import spock.lang.Specification

class TrainTest extends Specification {

    final double PRECISION = 0.001
    List<Train> trains = new ArrayList<>()
    Train train1 = new Train()
    Wagon wagon1 = new Wagon()
    Wagon wagon2 = new Wagon()
    Tractor locomotive1 = new Locomotive('A')
    Tractor locomotive2 = new Locomotive('B')

    def setup() {

    }

    def compare(float f1, float f2) {
        return (float) (f1 - f2)
    }

    def "Crear y eliminar un nuevo tren"() {

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

    def "Crear otro tren a partir de dividir uno"() {
        when:
        trains.add(train1)
        train1.pushFront(wagon1)
        train1.pushFront(wagon2)
        Train train2 = train1.divide(wagon1)
        then:
        train1.size().equals(1)
        train2.size().equals(1)
    }

    def "Unir dos trenes"() {
        when:
        train1.pushFront(wagon1)
        Train train2 = new Train()
        train2.pushFront(wagon2)

        train1.joinTrailerBack(train2)
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
        train1.getDirectorLinker().equals(locomotive1)

        when:
        train1.pushFront(locomotive2)
        train1.setDirectorLinker(locomotive2)

        then:
        train1.getDirectorLinker().equals(locomotive2)
    }

    def "Obtener la masa de todo un tren"() {
        expect:
        compare(train1.getMass(), 0.0F) <= PRECISION

        when:
        train1.pushFront(wagon1)
        then:
        compare(train1.getMass(), wagon1.getMass()) <= PRECISION

        when:
        train1.pushFront(wagon2)
        then:
        compare(train1.getMass(), (float) (wagon1.getMass() + wagon2.getMass())) <= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getMass(), wagon1.getMass()) <= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getMass(), 0.0F) <= PRECISION
    }

    def "suma de fuerzas en un tren"() {
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)

        then:
        train1.getTractors().contains(locomotive1)
        train1.getTractors().contains(locomotive2)
        compare(train1.getForce(), (float) (locomotive1.getForce() + locomotive2.getForce())) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getForce(), (float) ((locomotive1.getForce() + locomotive2.getForce()))) <= PRECISION
    }

    def "aceleración es fuerza dividida por masa"() {
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)

        then:
        compare(train1.getAcceleration(), 0.0F) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getAcceleration(), (float) (train1.getForce() / train1.getMass())) <= PRECISION
    }

    def "aceleración es lo que varió la velocidad en cada turno"() {

    }

    def "velocidad varía en cada turno según aceleración"() {
        when:
        locomotive1.setForce(20)
        train1.pushFront(locomotive1)
        locomotive2.setForce(20)
        train1.pushFront(locomotive2)
        then:
        compare(train1.getMass(), (float) (locomotive1.getMass() + locomotive2.getMass())) < PRECISION
        compare(train1.getSpeed(), 0.0f) <= PRECISION
        compare(train1.getDistanceTraveled(), 0.0f) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(), train1.getSpeed()) <= PRECISION


        //train is moved
        when:
        train1.resetDistanceTraveled()
        then:
        compare(train1.getDistanceTraveled(), 0.0) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getSpeed(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(), train1.getSpeed()) <= PRECISION

        when:
        locomotive1.setForce(0)
        locomotive2.setForce(0)
        train1.applyForce()

        then:
        compare(train1.getForce(), (float) (train1.getFrictionForce())) <= PRECISION

    }

    def "espacio recorrido en cada turno varía según velocidad"() {

    }

    def "si espacio recorrido supera unidad de movimiento, tren se moverá"() {

    }

    def "mover el tren por la vía"() {
        given:
        Model model = new Model();
        View view = new letrain.mvp.impl.View();
        RailTrack track
        RailMap map = model.getRailMap()
        RailTrackMaker maker = new RailTrackMaker(model, view)
        model.setMode(letrain.mvp.Model.GameMode.TRACKS)
        model.getCursor().setDir(Dir.E)
        for (int n = 0; n < 8; n++) {
            maker.onChar()
            maker.rotateCursorRight()
        }
        train1.pushBack(wagon1)
        train1.pushBack(wagon2)
        train1.pushFront(locomotive1)
        train1.assignDefaultDirectorLinker()
        when:
        map.getTrackAt(4, 0).enterLinkerFromDir(Dir.W, locomotive1)
        map.getTrackAt(3, 0).enterLinkerFromDir(Dir.W, wagon1)
        map.getTrackAt(2, 0).enterLinkerFromDir(Dir.W, wagon2)
        then:
        locomotive1.getPosition().getX().equals(4)
        wagon1.getPosition().getX().equals(3)
        wagon2.getPosition().getX().equals(2)

        when:
        train1.move()
        then:
        locomotive1.getPosition().getX().equals(5)
        wagon1.getPosition().getX().equals(4)
        wagon2.getPosition().getX().equals(3)

        when:
        train1.reverse()
        train1.move()
        then:
        locomotive1.getPosition().getX().equals(4)
        wagon1.getPosition().getX().equals(3)
        wagon2.getPosition().getX().equals(2)
    }

}

