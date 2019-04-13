package letrain.tui

import letrain.map.RailMap
import spock.lang.Ignore
import spock.lang.Specification

class TuiTest extends Specification {
    Tui tui = new Tui()
    RailMap railMap = new RailMap()

    @Ignore
    def "test paint"() {
        when:
        tui.set(0, 0, (char) '#')
        tui.set(79, 0, (char) '#')
        tui.set(0, 24, (char) '#')
        tui.set(79, 24, (char) '#')
        tui.paint()
        then:
        true
    }

    @Ignore
    def "test clear"() {
        when:
        tui.clear()
        tui.paint()
        then:
        true
    }

    @Ignore
    def "test set"() {
        when:
        tui.set(10, 10, (char) 'X')
        tui.paint()
        then:
        true
    }

    @Ignore
    def "test clear 2"() {
        when:
        tui.clear(10, 10)
        tui.paint()
        then:
        true
    }

    @Ignore
    def "test box"() {
        when:
        tui.clear()
        tui.box(12, 10, 20, 10)
        tui.box(40, 5, 8, 4)
        tui.box(5, 5, 5, 5)
        tui.paint()
        then:
        true
    }

}

//Generated with love by TestMe :) Please report issues and submit feature requests at: http://weirddev.com/forum#!/testme