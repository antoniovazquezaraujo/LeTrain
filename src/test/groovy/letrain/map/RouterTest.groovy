package letrain.map


import spock.lang.Specification;

class RouterTest extends Specification {
    SimpleRouter router = new SimpleRouter()


    def "test get Dir"() {
        expect:
        router.addRoute(a, b)
        router.addRoute(b, a)
        router.getDirWhenEnteringFrom(a).equals(b)
        router.getDirWhenEnteringFrom(b).equals(a)
        where:
        a      | b
        Dir.N  | Dir.S
        Dir.S  | Dir.N
        Dir.E  | Dir.W
        Dir.W  | Dir.E
        Dir.NE | Dir.SW
        Dir.SW | Dir.NE
        Dir.NW | Dir.SE
        Dir.SE | Dir.NW


    }

    def "test get First Open Dir"() {
        when:
        router.addRoute(Dir.N, Dir.S)
        Dir result = router.getFirstOpenDir()

        then:
        result != null
    }

    def "test get Num Open Dirs"() {
        when:
        router.addRoute(Dir.N, Dir.S)
        router.addRoute(Dir.E, Dir.NW)
        int result = router.getNumRoutes()

        then:
        result == 4

        when:
        router.addRoute(Dir.E, Dir.W)
        then:
        result == 4

    }

    def "test add Route"() {
        when:
        router.addRoute(Dir.E, Dir.W)

        then:
        router.getDirWhenEnteringFrom(Dir.E) == Dir.W
        router.getDirWhenEnteringFrom(Dir.N) == null
    }

    def "test remove Route"() {
        when:
        router.addRoute(Dir.E, Dir.W)
        router.removeRoute(Dir.E, Dir.W)

        then:
        router.getDirWhenEnteringFrom(Dir.E) == null
    }


    def "adding new routes that makes a cross"() {
        when:
        router.addRoute(Dir.NE, Dir.SW)
        router.addRoute(Dir.N, Dir.S)
        then:
        router.getDirWhenEnteringFrom(Dir.N) == Dir.S
        router.getDirWhenEnteringFrom(Dir.S) == Dir.N
        router.getDirWhenEnteringFrom(Dir.NE) == Dir.SW
        router.getDirWhenEnteringFrom(Dir.SW) == Dir.NE
        router.isStraight() == false
        router.isCurve() == false
        router.isCross() == true
    }

    def "removing routes transform a cross in a straight"() {
        when:
        router.addRoute(Dir.NE, Dir.SW)
        router.addRoute(Dir.N, Dir.S)
        then:
        router.isCross() == true
        when:
        router.removeRoute(Dir.N, Dir.S)
        then:
        router.isCross() == false
        router.isStraight() == true
    }

    def "add dir and router dont contains any of both"() {
        when:
        router.addRoute(Dir.N, Dir.S)
        then:
        router.isStraight() == true
        when:
        router.addRoute(Dir.E, Dir.SW)
        then:
        router.isCross() == true

    }

    def "add dir and router contains one of both"() {
        when:
        router.addRoute(Dir.N, Dir.S)
        then:
        router.isStraight() == true
        when:
        router.addRoute(Dir.N, Dir.SW)
        then:
        router.isCross() == false
        router.getNumRoutes() == 3
        when:
        router.removeRoute(Dir.N, Dir.SW)
        router.addRoute(Dir.SW, Dir.N)
        then:
        router.isCross() == false
    }

    def "add dir and router contains the conter route"() {
        when:
        router.addRoute(Dir.N, Dir.S)
        then:
        router.isStraight() == true
        when:
        router.addRoute(Dir.S, Dir.NW)
        then:
        router.isCross() == false
        router.getNumRoutes() == 3
        when:
        router.addRoute(Dir.NW, Dir.S)
        then:
        router.isCross() == false
    }

    def "changing directions"() {
        ForkRouter router = new ForkRouter();
        when:
        router.addRoute(Dir.E, Dir.W)
        then:
        router.getDirWhenEnteringFrom(Dir.E) == Dir.W
        router.getDirWhenEnteringFrom(Dir.W) == Dir.E
        router.isStraight() == true

        when:
        router.addRoute(Dir.W, Dir.NE)
        then:
        router.getDirWhenEnteringFrom(Dir.E) == Dir.W
        router.getDirWhenEnteringFrom(Dir.W) == Dir.NE
        router.isStraight() == false
        router.isCurve() == false

        when:

        then:
        router.isUsingAlternativeRoute()
        router.getDirWhenEnteringFrom(Dir.W).equals(Dir.NE)

        when:
        router.setNormalRoute()
        then:
        !router.isUsingAlternativeRoute()
        router.getDirWhenEnteringFrom(Dir.W).equals(Dir.E)

        when:
        router.setAlternativeRoute()
        then:
        router.isUsingAlternativeRoute()
        router.getDirWhenEnteringFrom(Dir.W).equals(Dir.NE)

    }

}
