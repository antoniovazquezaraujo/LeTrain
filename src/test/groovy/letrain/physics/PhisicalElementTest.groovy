package letrain.physics

import letrain.map.Dir
import spock.lang.Specification

class PhisicalElementTest extends Specification {
    PhysicEntity a;

    void setup() {
        a = new PhysicEntity(Math.PI/2, 1);
    }

    void cleanup() {
        a = null;
    }

    def "apply forces move to correct direction"() {
        given:
        a.beginStep()

        when:
        a.addForce(VectorXY.fromDir(Dir.S, 100));
        a.applyForces()
        a.endStep()
        then:
        a.velocity.toDir().equals(Dir.S);

        when:
        a.addForce(VectorXY.fromDir(Dir.NE, 2000));
        a.applyForces()
        a.endStep()
        then:
        a.velocity.toDir().equals(Dir.NE);
    }

    def "brakes deccelerate"(){
        given:
        MotorizedEntity m = new MotorizedEntity();
        m.addForce(VectorXY.fromDir(Dir.NW, 10));
        m.applyForces()
        m.endStep()
        m.beginStep()

        when:
        m.setBrakesForce(5)
        then:
        def preDistance = 0;
        10.times{ it ->
            VectorXY prePosition = m.position
            m.applyForces()
            m.endStep()
            VectorXY postPosition = m.position
            double postDistance = prePosition.distance(postPosition)
            print postDistance +","+ preDistance
        }

    }
    def "MotorizedEntity motor force accelerate while friction deccelerate"() {
        given:
        MotorizedEntity m = new MotorizedEntity();
        m.addForce(VectorXY.fromDir(Dir.S, 1));
        m.applyForces()
        m.endStep()
        m.beginStep()

        when:
        m.setMotorForce(10)
        then:
        def preDistance = 0;
        10.times{ it ->
            VectorXY prePosition = m.position
            m.applyForces()
            m.endStep()
            VectorXY postPosition = m.position
            double postDistance = prePosition.distance(postPosition)
            assert postDistance >= preDistance
        }
        m.invert(true)
        10.times{ it ->
            VectorXY prePosition = m.position
            m.applyForces()
            m.endStep()
            VectorXY postPosition = m.position
            double postDistance = prePosition.distance(postPosition)
            assert postDistance <= preDistance
        }
    }

}
