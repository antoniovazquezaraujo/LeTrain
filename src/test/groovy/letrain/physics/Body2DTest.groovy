package letrain.physics

import letrain.map.Dir
import spock.lang.Specification

class Body2DTest extends Specification {

    void setup() {
    }

    void cleanup() {
    }

    def "apply forces move to correct direction"() {
        given:
        Body2D m = new Body2D(Dir.S);
        m.beginStep()

        when:
        m.setMotorForce(100);
        m.applyForces()
        m.endStep()
        then:
        m.velocity.toDir().equals(Dir.S);

        when:
        m.setMotorForce(2000);
        m.setDir(Dir.NE)
        m.applyForces()
        m.endStep()
        then:
        m.velocity.toDir().equals(Dir.NE);
    }

    def "brakes deccelerate"(){
        given:
        Body2D m = new Body2D();
        m.setMotorForce(100);
        m.applyForces()
        m.endStep()

        when:
        m.setBrakesForce(10)
        def prePosition =  m.position2D
        then:
        10.times{ it ->
            m.beginStep()
            m.applyForces()
            m.endStep()
        }
        assert  m.position2D.equals(prePosition)

        when:
        m.setBrakesForce(1)
        then:
        10.times{ it ->
            m.beginStep()
            m.applyForces()
            m.endStep()
        }
//        assert  !m.position.equals(prePosition)
    }
    def "MotorizedEntity motor force accelerate while friction deccelerate"() {
        given:
        Body2D m = new Body2D();
        m.setMotorForce(1);
        m.applyForces()
        m.endStep()
        m.beginStep()

        when:
        m.setMotorForce(1)
        then:
        def speed = m.getSpeed()
        10.times{ it ->
            m.beginStep()
            m.applyForces()
            m.endStep()
            assert m.getSpeed() >= speed
        }

        when:
        speed = m.getSpeed()
        m.setMotorForce(0);
        m.setBrakesForce(10)
        then:
        10.times{ it ->
            m.beginStep()
            m.applyForces()
            m.endStep()
            assert m.getSpeed() <= speed
        }
    }

}
