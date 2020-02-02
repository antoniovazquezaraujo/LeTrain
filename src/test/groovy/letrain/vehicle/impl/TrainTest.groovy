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
                track.connect(Dir.W, old)
                old.connect(Dir.E, track)
            }
            map.addTrack(point, track)
        }

        train1.pushFront(locomotive1)
        train1.pushBack(wagon1_1)
        train1.pushBack(wagon1_2)
        map.getTrackAt(0,10).enterLinkerFromDir(Dir.W,locomotive1)
        map.getTrackAt(0,11).enterLinkerFromDir(Dir.W,wagon1_1)
        map.getTrackAt(0,12).enterLinkerFromDir(Dir.W,wagon1_2)

        train2.pushFront(locomotive2)
        train2.pushBack(wagon2_1)
        train2.pushBack(wagon2_2)
        map.getTrackAt(0,14).enterLinkerFromDir(Dir.W,locomotive2)
        map.getTrackAt(0,15).enterLinkerFromDir(Dir.W,wagon2_1)
        map.getTrackAt(0,16).enterLinkerFromDir(Dir.W,wagon2_2)

    }

    def borrame(){
        when:
        println "Acelerando"
        train1.setForce(10000)
        for (int i = 0; i < 1000; i++) {
            train1.applyForce()
            println "Friction:"+ train1.getFrictionForce()+
                    " Speed:"+ train1.getAcceleration()+
                    " Pos:"+ locomotive1.getPosition()+
                    " Distance:"+ train1.getDistanceTraveled()

        }
        println "Frenando"
        train1.setForce(0)
        train1.setBrakes(100)
        for (int i = 0; i < 10; i++) {
            train1.applyForce()
            println train1.getAcceleration() + ": "+ locomotive1.getPosition()

        }
        then:
        true

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
        train1.isEmpty()

        when:
        trains.remove(0)
        then:
        trains.isEmpty()

    }

    def "Crear otro tren a partir de dividir uno"() {
        when:
        trains.add(train1)
        train1.pushFront(wagon1_1)
        train1.pushFront(wagon1_2)
        Train train2 = train1.divide(wagon1_1)
        then:
        train1.size().equals(1)
        train2.size().equals(1)
    }

    def "Unir dos trenes"() {
        when:
        train1.pushFront(wagon1_1)
        Train train2 = new Train()
        train2.pushFront(wagon1_2)

        train1.joinTrailerBack(train2)
        then:
        train2.isEmpty()
        train1.size().equals(2)

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
        expect:
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
        compare(train1.getAcceleration(), 0.0f) <= PRECISION
        compare(train1.getDistanceTraveled(), 0.0f) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getAcceleration(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(), train1.getAcceleration()) <= PRECISION


        //train is moved
        when:
        train1.resetDistanceTraveled()
        then:
        compare(train1.getDistanceTraveled(), 0.0) <= PRECISION

        when:
        train1.applyForce()
        then:
        compare(train1.getAcceleration(), train1.getAcceleration()) <= PRECISION
        compare(train1.getDistanceTraveled(), train1.getAcceleration()) <= PRECISION

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
        train1.pushBack(wagon1_1)
        train1.pushBack(wagon1_2)
        train1.pushFront(locomotive1)
        train1.assignDefaultDirectorLinker()
        when:
        map.getTrackAt(4, 0).enterLinkerFromDir(Dir.W, locomotive1)
        map.getTrackAt(3, 0).enterLinkerFromDir(Dir.W, wagon1_1)
        map.getTrackAt(2, 0).enterLinkerFromDir(Dir.W, wagon1_2)
        then:
        locomotive1.getPosition().getX().equals(4)
        wagon1_1.getPosition().getX().equals(3)
        wagon1_2.getPosition().getX().equals(2)

        when:
        train1.move()
        then:
        locomotive1.getPosition().getX().equals(5)
        wagon1_1.getPosition().getX().equals(4)
        wagon1_2.getPosition().getX().equals(3)

        when:
        train1.reverse()
        train1.move()
        then:
        locomotive1.getPosition().getX().equals(4)
        wagon1_1.getPosition().getX().equals(3)
        wagon1_2.getPosition().getX().equals(2)
    }

}

