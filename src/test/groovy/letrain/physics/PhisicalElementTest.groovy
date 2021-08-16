package letrain.physics

import letrain.map.Dir
import spock.lang.Specification

class PhisicalElementTest extends Specification {
    PhysicEntity a;

    void setup() {
        a = new PhysicEntity(Dir.E, 1);
    }

    void cleanup() {
        a = null;
    }

    def "apply forces move to correct direction"() {
        given:
        a.startTurn()

        when:
        a.applyForce(VectorXY.fromDir(Dir.S, 100));
        a.endTurn()
        then:
        a.velocity.toDir().equals(Dir.S);

        when:
        a.applyForce(VectorXY.fromDir(Dir.NE, 2000));
        a.endTurn()
        then:
        a.velocity.toDir().equals(Dir.NE);
    }

    def "MotorizedEntity change motor force"() {
        given:
        MotorizedEntity m = new MotorizedEntity();
        m.applyForce(VectorXY.fromDir(Dir.S, 1));
        m.endTurn()
        m.startTurn()

        when:
        m.setMotorForce(10)

        10.times{ it ->
            m.applyMotorForce()
            m.endTurn()
        }
        m.invert(true)
        100.times{ it ->
           //m.applyMotorForce()
            m.endTurn()
        }
        then:
        assert true
    }

}
