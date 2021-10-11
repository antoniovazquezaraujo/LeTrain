package letrain.vehicle.impl.rail


import letrain.map.Dir
import letrain.map.Point
import letrain.map.RailMap
import letrain.mvp.View
import letrain.mvp.impl.Model
import letrain.mvp.impl.RailTrackMaker
import letrain.track.rail.RailTrack
import spock.lang.Specification

class TrainTest extends Specification {

    final double PRECISION = 0.001
    List<Train> trains = new ArrayList<>()
    Train train1 = new Train()
    Locomotive locomotive1 = new Locomotive('A')
    Wagon wagon1_1 = new Wagon()
    Wagon wagon1_2 = new Wagon()

    Train train2 = new Train()
    Locomotive locomotive2 = new Locomotive('B')
    Wagon wagon2_1 = new Wagon()
    Wagon wagon2_2 = new Wagon()

    RailMap map = new RailMap()

    def setup() {
        for(int n=0; n<40; n++){
            RailTrack track = new RailTrack()
            Point point = new Point(0,n)
            track.setPosition(point)
            track.addRoute(Dir.W, Dir.E)
            RailTrack old = map.getTrackAt(0, n-1);
            if(old!=null) {
                track.connectTrack(Dir.W, old)
                old.connectTrack(Dir.E, track)
            }
            map.addTrack(point, track)
        }

        train1.pushFront(locomotive1)
        train1.pushBack(wagon1_1)
        train1.pushBack(wagon1_2)
        locomotive1.setDir(Dir.W)
        map.getTrackAt(0,10).enter(locomotive1)
        wagon1_2.setDir(Dir.W)
        map.getTrackAt(0,11).enter(wagon1_1)
        wagon1_2.setDir(Dir.W)
        map.getTrackAt(0,12).enter(wagon1_2)

        train2.pushFront(locomotive2)
        train2.pushBack(wagon2_1)
        train2.pushBack(wagon2_2)
        locomotive2.setDir(Dir.W)
        map.getTrackAt(0,10).enter(locomotive2)
        wagon2_2.setDir(Dir.W)
        map.getTrackAt(0,11).enter(wagon2_1)
        wagon2_2.setDir(Dir.W)
        map.getTrackAt(0,12).enter(wagon2_2)

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
        train1.pushFront(wagon1_1)
        then:
        train1.getFront().equals(wagon1_1)

        when:
        train1.popFront()
        then:
        !train1.getFront().equals(wagon1_1)

        when:
        trains.remove(0)
        then:
        trains.isEmpty()

    }

    def "Crear otro tren a partir de dividir uno"() {
        when:
        trains.add(train1)
        Train train3 = train1.divide(wagon1_1)
        then:
        train1.size().equals(2)
        train3.size().equals(1)
    }

    def "Unir dos trenes"() {
        when:
        Train train3 = new Train()
        train3.pushFront(wagon1_2)

        train1.joinTrailerBack(train3)
        then:
        train3.isEmpty()
        train1.size().equals(4)

    }

    def "Seleccionar y obtener el mainTractor de un tren"() {
        when:
        train1.pushFront(wagon1_1)
        train1.pushFront(wagon1_2)
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
        when:
        train1.popBack()
        train1.popBack()
        train1.popBack()
        then:
        compare(train1.getMass(), 0.0F) <= PRECISION

        when:
        train1.pushFront(wagon1_1)
        then:
        compare(train1.getMass(), wagon1_1.getMass()) <= PRECISION

        when:
        train1.pushFront(wagon1_2)
        then:
        compare(train1.getMass(), (float) (wagon1_1.getMass() + wagon1_2.getMass())) <= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getMass(), wagon1_1.getMass()) <= PRECISION

        when:
        train1.popFront()
        then:
        compare(train1.getMass(), 0.0F) <= PRECISION
    }

    def "suma de fuerzas en un tren"() {
        when:
        Train train = new Train();
        Locomotive l1 = new Locomotive('X')
        Locomotive l2 = new Locomotive('Y')
        train.pushFront(l1)
        train.pushFront(l2)
        l1.setForce(20)
        l2.setForce(20)

        then:
        train.getMotorizedVehicles().contains(l1)
        train.getMotorizedVehicles().contains(l2)
        compare(train.getForce(), (float) (l1.getForce() + l2.getForce())) <= PRECISION

        when:
        train.move()
        then:
        compare(train.getForce(), (float) ((l1.getForce() + l2.getForce()))) <= PRECISION
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
        train1.move()
        then:
        compare(train1.getAcceleration(), (float) (train1.getForce() / train1.getMass())) <= PRECISION
    }

    def "aceleración es lo que varió la velocidad en cada turno"() {

    }

    def "velocidad varía en cada turno según aceleración"() {
        when:
        Train train = new Train()
        Locomotive l1 = new Locomotive('X')
        Locomotive l2 = new Locomotive('Y')
        l1.setForce(20)
        train.pushFront(l1)
        l2.setForce(20)
        train.pushFront(l2)
        then:
        compare(train.getMass(), (float) (l1.getMass() + l2.getMass())) < PRECISION
        compare(train.getAcceleration(), 0.0f) <= PRECISION
        compare(train.getDistanceTraveled(), 0.0f) <= PRECISION

        when:
        train.move()
        then:
        compare(train.getAcceleration(), train.getAcceleration()) <= PRECISION
        compare(train.getDistanceTraveled(), train.getAcceleration()) <= PRECISION


        //train is moved
        when:
        train.resetDistanceTraveled()
        then:
        compare(train.getDistanceTraveled(), 0.0) <= PRECISION

        when:
        train.move()
        then:
        compare(train.getAcceleration(), train.getAcceleration()) <= PRECISION
        compare(train.getDistanceTraveled(), train.getAcceleration()) <= PRECISION

        when:
        l1.setForce(0)
        l2.setForce(0)
        train.move()

        then:
        compare(train.getForce(), (float) (train.getFrictionForce())) <= PRECISION

    }

    def "espacio recorrido en cada turno varía según velocidad"() {

    }

    def "si espacio recorrido supera unidad de movimiento, tren se moverá"() {

    }

    def "mover el tren por la vía"() {
        given:
        Model model = new Model()
        View view = GroovyMock(View)
        RailTrack track
        RailMap map = model.getRailMap()
        RailTrackMaker maker = new RailTrackMaker(model, view)
        model.setMode(letrain.mvp.Model.GameMode.TRACKS)
        model.getCursor().setDir(Dir.E)
        model.getCursor().setPosition(new Point(0,0))
        for (int n = 0; n < 8; n++) {
            maker.createTrack()
//            maker.cursorTurnRight()
        }
        Train train = new Train()
        train.pushBack(wagon1_1)
        train.pushBack(wagon1_2)
        train.pushFront(locomotive1)
        train.assignDefaultDirectorLinker()
        when:
        locomotive1.setDir(Dir.W)
        map.getTrackAt(4, 0).enter(locomotive1)
        wagon1_1.setDir(Dir.W)
        map.getTrackAt(3, 0).enter(wagon1_1)
        wagon1_2.setDir(Dir.W)
        map.getTrackAt(2, 0).enter(wagon1_2)
        then:
        locomotive1.getPosition2D().getX().equals(4)
        wagon1_1.getPosition2D().getX().equals(3)
        wagon1_2.getPosition2D().getX().equals(2)

//        when:
//        train.move()
//        then:
//        locomotive1.getPosition().getX().equals(4)
//        wagon1_1.getPosition().getX().equals(4)
//        wagon1_2.getPosition().getX().equals(3)

//        when:
//        train.reverse(true)
//        train.advance()
//        then:
//        locomotive1.getPosition().getX().equals(4)
//        wagon1_1.getPosition().getX().equals(3)
//        wagon1_2.getPosition().getX().equals(2)
    }

}

