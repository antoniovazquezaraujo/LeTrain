package letrain.readmaps


import letrain.map.RailMapReader
import spock.lang.Specification

class ReadMapsFromFileTest extends Specification {
    def "test from Int"() {
        when:
        RailMapReader reader = new RailMapReader()
        def textMap = "10,10 e1 l1 r1 l1 r1 l1 r1"
        reader.read(textMap)
        then:
        reader.getRailMap().forEach({ t -> System.out.println(t) })

    }

}
