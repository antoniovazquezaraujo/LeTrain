package letrain.vehicle.impl.road

import letrain.map.Dir
import letrain.physics.Vector2D
import letrain.vehicle.impl.rail.Wagon
import org.mockito.InjectMocks
import org.mockito.MockitoAnnotations
import spock.lang.Specification

class RoadVehicleTest extends Specification {
    @InjectMocks
    Wagon roadVehicle

    def setup() {
        MockitoAnnotations.initMocks(this)
    }

    def "test set Pos"() {
        when:
        roadVehicle.setPosition2D(new Vector2D(300, 3))

        then:
        roadVehicle.getPosition2D().getX()==300
        roadVehicle.getPosition2D().getY()==3
    }

    def "test set Y"() {
        when:
        roadVehicle.getPosition2D().setY(200)

        then:
        roadVehicle.getPosition2D().getY() == 200
    }

    def "test set X"() {
        when:
        roadVehicle.getPosition2D().setX(4)

        then:
        roadVehicle.getPosition2D().getX() == 4
    }

    def "test rotate Left"() {
        when:
        roadVehicle.setDir(Dir.S)
        roadVehicle.rotateLeft()

        then:
        Dir.SE == roadVehicle.getDir()
    }

    def "test rotate Right"() {
        when:
        roadVehicle.setDir(Dir.N)
        roadVehicle.rotateRight()

        then:
        Dir.NE == roadVehicle.getDir()
    }

    def "test rotate"() {
        when:
        roadVehicle.setDir(Dir.N)
        roadVehicle.rotate(3)

        then:
        Dir.SW == roadVehicle.getDir()
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme