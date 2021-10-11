package letrain.physics

import letrain.map.Dir
import spock.lang.Specification

class Vector2DTest extends Specification {
    Vector2D v;

    void setup() {
        v = new Vector2D(10, 10)
    }

    void cleanup() {
        v = null;
    }

    def "GetX"() {
        v.x == 10
    }

    def "GetY"() {
        v.y == 10
    }

    def "Magnitude"() {
    }

    def "Add"() {

    }

    def "Sub"() {
    }

    def "Mult"() {
    }

    def "TestMult"() {
    }

    def "Div"() {
    }

    def "TestDiv"() {
    }

//    def "Distance"() {
//        given:
//        def v2 = new Vector2D(10, 20)
//        expect:
//        v.distance(v2) == 10
//
//    }

    def "Dot"() {
    }

    def "TestDot"() {
    }

    def "Normalize"() {
    }

    def "Limit"() {
    }

    def "AngleInRadians"() {
        when:
            v.setX(20)
            v.setY(0)
        then:
            v.angleInRadians() == 0
        when:
            v.setX(20)
            v.setY(20)
        then:
            v.angleInRadians() == Math.PI / 4
        when:
            v.setX(0)
            v.setY(20)
        then:
            v.angleInRadians() == Math.PI / 2
        when:
            v.setX(-20)
            v.setY(20)
        then:
            v.angleInRadians() == (Math.PI / 4) * 3
        when:
            v.setX(-20)
            v.setY(0)
        then:
            v.angleInRadians() == Math.PI
        //////////// Negatives
        when:
            v.setX(-20)
            v.setY(-20)
        then:
            v.angleInRadians() == -(Math.PI / 4) * 3
        when:
            v.setX(0)
            v.setY(-20)
        then:
            v.angleInRadians() == -(Math.PI / 2)
        when:
            v.setX(20)
            v.setY(-20)
        then:
            v.angleInRadians() == -(Math.PI / 4)
    }

    def "ClampRadians"() {
    }

    def "Rotate"() {
    }

    def "FromPolar"() {
    }

    def "FromDir"() {
    }

    def "Move"() {
    }

    def "Locate"() {
        expect:
        v.locate(new Vector2D(0, 10)) == Dir.W
        v.locate(new Vector2D(0, 0)) == Dir.SW
        v.locate(new Vector2D(10, 0)) == Dir.S
        v.locate(new Vector2D(20, 0)) == Dir.SE
        v.locate(new Vector2D(20, 10)) == Dir.E
        v.locate(new Vector2D(20, 20)) == Dir.NE
        v.locate(new Vector2D(10, 20)) == Dir.N
        v.locate(new Vector2D(0, 20)) == Dir.NW
    }

    def "ToDir"() {
    }

    def "TestDot1"() {
    }

    def "TestDiv1"() {
    }

    def "TestDiv2"() {
    }

    def "TestMult1"() {
    }

    def "TestMult2"() {
    }

    def "TestAdd"() {
    }

    def "TestSub"() {
    }

    def "TestDistance"() {
    }

    def "DegreesToRadians"() {
    }

    def "RadiansToDegrees"() {
    }

    def "AngleBetween"() {
    }

    def "SimpleAngleBetween"() {
    }

    def "DegreesToCardinal"() {
    }

    def "RadiansToCardinal"() {
    }

    def "AlmostEquals"() {
    }

    def "Round"() {
    }
}
