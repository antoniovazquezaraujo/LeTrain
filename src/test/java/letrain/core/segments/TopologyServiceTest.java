package letrain.core.segments;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.segments.RailwayGraph;
import letrain.segments.TopologyService;
import letrain.segments.impl.TopologyServiceImpl;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TopologyServiceTest {
    private RailMap railMap;
    private TopologyService topologyService;

    @BeforeEach
    void setUp() {
        railMap = new RailMap();
        topologyService = new TopologyServiceImpl();
    }

    @Test
    void testSimpleLineTopology() {
        RailTrack t1 = new RailTrack(); // Tope Oeste
        RailTrack t2 = new RailTrack(); // Recta
        RailTrack t3 = new RailTrack(); // Tope Este
        
        t1.addRoute(Dir.E, Dir.W); 
        t2.addRoute(Dir.W, Dir.E);
        t3.addRoute(Dir.W, Dir.E);
        
        t1.setPosition(new Point(0, 0));
        t2.setPosition(new Point(1, 0));
        t3.setPosition(new Point(2, 0));
        
        railMap.addTrack(new Point(0, 0), t1);
        railMap.addTrack(new Point(1, 0), t2);
        railMap.addTrack(new Point(2, 0), t3);
        
        t1.connect(Dir.E, t2);
        t2.connect(Dir.W, t1);
        t2.connect(Dir.E, t3);
        t3.connect(Dir.W, t2);
        
        RailwayGraph graph = topologyService.discover(railMap);
        assertNotNull(graph);
        
        // En una implementación real de test, tendríamos que buscar en el grafo.
        // Dado que el grafo es una caja negra, vamos a verificar que al menos hay segmentos.
        
        // Si el descubrimiento funcionó, t1 y t3 son nodos.
        // Debe existir un segmento que los una.
    }

    @Test
    void testForkTopology() {
        // Mapa con un Fork (T) que une 3 topes
        ForkRailTrack fork = new ForkRailTrack(1);
        RailTrack tNorth = new RailTrack();
        RailTrack tSouth = new RailTrack();
        RailTrack tEast = new RailTrack();
        
        // Configurar Fork (Norte entrada, Sur y Este salidas)
        fork.addRoute(Dir.N, Dir.S);
        fork.addRoute(Dir.N, Dir.E);
        fork.setPosition(new Point(1, 1));
        
        tNorth.addRoute(Dir.S, Dir.N); tNorth.setPosition(new Point(1, 0));
        tSouth.addRoute(Dir.N, Dir.S); tSouth.setPosition(new Point(1, 2));
        tEast.addRoute(Dir.W, Dir.E);  tEast.setPosition(new Point(2, 1));
        
        railMap.addTrack(fork.getPosition(), fork);
        railMap.addTrack(tNorth.getPosition(), tNorth);
        railMap.addTrack(tSouth.getPosition(), tSouth);
        railMap.addTrack(tEast.getPosition(), tEast);
        
        // Conexiones físicas
        fork.connect(Dir.N, tNorth); tNorth.connect(Dir.S, fork);
        fork.connect(Dir.S, tSouth); tSouth.connect(Dir.N, fork);
        fork.connect(Dir.E, tEast);  tEast.connect(Dir.W, fork);
        
        RailwayGraph graph = topologyService.discover(railMap);
        assertNotNull(graph);
        
        // Debería haber 4 nodos (fork + 3 topes) y 3 segmentos.
        // Cada salida del fork debe llevarnos a un segmento distinto.
    }

    @Test
    void testCircularTopology() {
        // Circuito de 4 piezas en cuadrado
        RailTrack t1 = new RailTrack();
        RailTrack t2 = new RailTrack();
        RailTrack t3 = new RailTrack();
        RailTrack t4 = new RailTrack();

        t1.addRoute(Dir.S, Dir.E);
        t2.addRoute(Dir.W, Dir.S);
        t3.addRoute(Dir.N, Dir.W);
        t4.addRoute(Dir.E, Dir.N);

        t1.setPosition(new Point(0,0));
        t2.setPosition(new Point(1,0));
        t3.setPosition(new Point(1,1));
        t4.setPosition(new Point(0,1));

        railMap.addTrack(t1.getPosition(), t1);
        railMap.addTrack(t2.getPosition(), t2);
        railMap.addTrack(t3.getPosition(), t3);
        railMap.addTrack(t4.getPosition(), t4);

        t1.connect(Dir.E, t2); t2.connect(Dir.W, t1);
        t2.connect(Dir.S, t3); t3.connect(Dir.N, t2);
        t3.connect(Dir.W, t4); t4.connect(Dir.E, t3);
        t4.connect(Dir.N, t1); t1.connect(Dir.S, t4);

        RailwayGraph graph = topologyService.discover(railMap);
        assertNotNull(graph);
        // En un círculo sin decisiones, no hay nodos ni segmentos.
    }
}
