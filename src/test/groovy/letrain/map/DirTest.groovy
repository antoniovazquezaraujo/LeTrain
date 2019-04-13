package letrain.map


import spock.lang.Specification

class DirTest extends Specification {
    Dir dir = Dir.E

    def "test from Int"() {
        expect:
        Dir.fromInt(a) == b

        where:
        a  | b
        0  | Dir.E
        1  | Dir.NE
        2  | Dir.N
        3  | Dir.NW
        4  | Dir.W
        5  | Dir.SW
        6  | Dir.S
        7  | Dir.SE
        8  | Dir.E
        -1 | Dir.SE
        -2 | Dir.S
        -3 | Dir.SW
        -4 | Dir.W
        -5 | Dir.NW
        -6 | Dir.N
        -7 | Dir.NE
        -8 | Dir.E
    }

    def "test add"() {
        given:
        dir = Dir.E
        expect:
        Dir.add(dir, a) == b

        where:
        a  | b
        0  | Dir.E
        1  | Dir.NE
        2  | Dir.N
        3  | Dir.NW
        4  | Dir.W
        5  | Dir.SW
        6  | Dir.S
        7  | Dir.SE
        8  | Dir.E
        -1 | Dir.SE
        -2 | Dir.S
        -3 | Dir.SW
        -4 | Dir.W
        -5 | Dir.NW
        -6 | Dir.N
        -7 | Dir.NE
        -8 | Dir.E
    }

    def "test is Valid Value"() {
        given:
        dir = Dir.E
        expect:
        Dir.isValidValue(a) == b

        where:
        a  | b
        0  | true
        1  | true
        2  | true
        3  | true
        4  | true
        5  | true
        6  | true
        7  | true
        8  | false
        -1 | false
        -2 | false
        -3 | false
        -4 | false
        -5 | false
        -6 | false
        -7 | false
        -8 | false
    }

    def "test invert"() {
        given:
        dir = Dir.E
        expect:
        Dir.invert(a) == b

        where:
        a      | b
        Dir.W  | Dir.E
        Dir.SW | Dir.NE
        Dir.S  | Dir.N
        Dir.SE | Dir.NW
        Dir.E  | Dir.W
        Dir.NE | Dir.SW
        Dir.N  | Dir.S
        Dir.NW | Dir.SE
    }


    def "test short Way"() {
        given:
        dir = Dir.E
        expect:
        Dir.shortWay(a) == b

        where:
        a  | b
        0  | 0
        1  | 1
        2  | 2
        3  | 3
        4  | 4
        5  | -3
        6  | -2
        7  | -1
        -1 | -1
        -2 | -2
        -3 | -3
        -4 | -4
        -5 | 3
        -6 | 2
        -7 | 1
    }

    def "test angular Distance"() {
        given:
        dir = Dir.E
        expect:
        dir.angularDistance(a) == b

        where:
        a      | b
        Dir.E  | 0
        Dir.NE | 1
        Dir.N  | 2
        Dir.NW | 3
        Dir.W  | 4
        Dir.SW | -3
        Dir.S  | -2
        Dir.SE | -1
    }

    def "test inverse"() {
        expect:
        a.inverse() == b

        where:
        a      | b
        Dir.W  | Dir.E
        Dir.SW | Dir.NE
        Dir.S  | Dir.N
        Dir.SE | Dir.NW
        Dir.E  | Dir.W
        Dir.NE | Dir.SW
        Dir.N  | Dir.S
        Dir.NW | Dir.SE
    }

    def "test turn Left"() {
        expect:
        a.turnLeft() == b

        where:
        a      | b
        Dir.SE | Dir.E
        Dir.E  | Dir.NE
        Dir.NE | Dir.N
        Dir.N  | Dir.NW
        Dir.NW | Dir.W
        Dir.W  | Dir.SW
        Dir.SW | Dir.S
        Dir.S  | Dir.SE

    }

    def "test turn Right"() {
        expect:
        a.turnRight() == b

        where:
        a      | b
        Dir.E  | Dir.SE
        Dir.NE | Dir.E
        Dir.N  | Dir.NE
        Dir.NW | Dir.N
        Dir.W  | Dir.NW
        Dir.SW | Dir.W
        Dir.S  | Dir.SW
        Dir.SE | Dir.S

    }

    def "test is Curve"() {
        given:
        dir = Dir.E
        expect:
        dir.isCurve(a) == b

        where:
        a      | b
        Dir.E  | false
        Dir.NE | false
        Dir.N  | false
        Dir.NW | true
        Dir.W  | false
        Dir.SW | true
        Dir.S  | false
        Dir.SE | false
    }

    def "test is Straight"() {
        given:
        dir = Dir.E
        expect:
        dir.isStraight(a) == b
        a.isStraight(a.inverse())

        where:
        a      | b
        Dir.E  | false
        Dir.NE | false
        Dir.N  | false
        Dir.NW | false
        Dir.W  | true
        Dir.SW | false
        Dir.S  | false
        Dir.SE | false
    }
}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme