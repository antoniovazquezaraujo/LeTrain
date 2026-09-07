package letrain.vehicle.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests deterministas de la regla de descarrilamiento por curvas/desvíos (issue #350, ADR-019).
 *
 * <p>
 * Geometrías montadas con {@code RailTrack.addRoute} y {@code connect}, estilo
 * {@code AutoPilotIntegrationTest}. El tiempo se mide en ticks de simulación (el tren recorre una
 * casilla cada {@code 50 / speed} ticks), por lo que el instante de la última curva codifica la
 * velocidad efectiva. Config por defecto del motor: {@code derail.minCurveInterval = 12},
 * {@code derail.minSpeed = 3}, {@code derail.forkMaxSpeed = 3}.
 */
@DisplayName("Derailment: curves too close + fork over-speed (issue #350)")
class DerailmentCurvesSpeedTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Curvas muy seguidas a alta velocidad → descarrila
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1.1 Two adjacent curves at speed 10 → derails on the second curve")
    void twoAdjacentCurves_atHighSpeed_derails() {
        LayoutCurves l = buildAdjacentCurves();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 10);

        runTicks(16);

        // Descarrila al entrar en la 2ª curva (tick 10); no debe seguir circulando.
        assertTrue(crashes.get() >= 1, "train must derail when curves are too close at speed 10");
        assertTrue(loco.isDestroying(), "locomotive must be destroying after derailment");
        assertTrue(t.isStalled(), "train must be stalled after derailment");
    }

    @ParameterizedTest(name = "1.2 gap={0} cell(s), speed={1} → derails")
    @CsvSource({"1,10", "2,10"})
    @DisplayName("1.2 Curves separated by a short straight at speed 10 → derails")
    void curvesWithGap_atHighSpeed_derails(int gapCells, int speed) {
        LayoutCurves l = buildCurvesWithGap(gapCells);
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, speed);

        runTicks(30);

        assertTrue(crashes.get() >= 1,
                "train must derail when curves are separated by " + gapCells
                        + " straight cell(s) at speed " + speed);
        assertTrue(loco.isDestroying(), "locomotive must be destroying after derailment");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. La misma geometría a baja velocidad → no descarrila
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2.1 Two adjacent curves at speed 3 → no derail (interval >= minCurveInterval)")
    void twoAdjacentCurves_atLowSpeed_noDerail() {
        LayoutCurves l = buildAdjacentCurves();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 3);

        // A speed 3 el tren tarda 16 ticks por casilla: entre las dos curvas pasan 16 ticks.
        // La 2ª curva se entra en el tick 32 y el primer recto de salida en el 48.
        runTicks(56);

        assertEquals(0, crashes.get(), "slow train must not derail between adjacent curves");
        assertFalse(loco.isDestroying(), "locomotive must remain intact");
        // La cabeza debe haber atravesado la 2ª curva y seguir en la vía.
        assertNotSame(l.curve1, t.getPhysicalFront().getTrack(),
                "head must have passed the first curve");
        assertNotSame(l.curve2, t.getPhysicalFront().getTrack(),
                "head must have passed the second curve");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Encadenar curvas por debajo de minSpeed → nunca descarrila
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3.1 Chained adjacent curves below minSpeed (speed 2) → never derails")
    void chainedCurves_belowMinSpeed_neverDerails() {
        LayoutCurves l = buildAdjacentCurves();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 2);

        runTicks(85);

        assertEquals(0, crashes.get(), "speed below minSpeed must never derail");
        assertFalse(loco.isDestroying(), "locomotive must remain intact");
        assertNotSame(l.curve2, t.getPhysicalFront().getTrack(),
                "head must have passed both curves");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Desvíos: por encima de forkMaxSpeed → descarrila siempre
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4.1 Crossing a fork straight at speed 4 (> forkMaxSpeed 3) → derails")
    void forkAtSpeedAboveLimit_derails() {
        LayoutFork l = buildStraightFork();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 4);

        runTicks(20);

        assertTrue(crashes.get() >= 1, "fork over-speed must always derail (hard limit)");
        assertTrue(loco.isDestroying(), "locomotive must be destroying after derailment");
    }

    @Test
    @DisplayName("4.2 Crossing a fork straight at speed 3 (<= forkMaxSpeed 3) → no derail")
    void forkAtSpeedAtOrBelowLimit_noDerail() {
        LayoutFork l = buildStraightFork();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 3);

        runTicks(50);

        assertEquals(0, crashes.get(), "fork at or below its speed limit must not derail");
        assertFalse(loco.isDestroying(), "locomotive must remain intact");
        assertNotSame(l.fork, t.getPhysicalFront().getTrack(),
                "head must have passed through the fork");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Pararse / invertir resetea el historial de curva
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5.1 Stopping between two curves resets the interval → no derail after restart")
    void stopping_resetsHistory_noDerail() {
        LayoutCurves l = buildAdjacentCurves();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 10);

        // Entra en la 1ª curva (tick 5) y se detiene antes de llegar a la 2ª.
        runTicks(6);
        t.emergencyStop();
        assertEquals(0, t.getSpeed(), "train must be fully stopped");

        // Re-arranca a tope: sin el reset al parar, descarrilaría en la 2ª curva porque
        // habrían pasado menos de 12 ticks desde la 1ª.
        setConstantSpeed(loco, 10);
        runTicks(12);

        assertEquals(0, crashes.get(), "stop must reset the last-curve history");
        assertFalse(loco.isDestroying(), "locomotive must remain intact");
        assertNotSame(l.curve2, t.getPhysicalFront().getTrack(),
                "head must have passed the second curve after the restart");
    }

    @Test
    @DisplayName("5.2 Reversing resets the history → crossing the curve backwards does not derail")
    void reversing_resetsHistory_noDerail() {
        LayoutCurves l = buildCurvesWithReverseTail();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 10);

        // Atraviesa la 1ª curva (tick 5), llega al recto intermedio (tick 10) y frena.
        runTicks(10);
        t.emergencyStop();
        assertEquals(0, t.getSpeed(), "train must be fully stopped");

        // Invierte la marcha y vuelve a cruzar la curva en sentido contrario poco después:
        // sin el reset, habrían pasado menos de 12 ticks desde la 1ª curva y descarrilaría.
        loco.toggleReversed();
        assertTrue(loco.isReversed(), "locomotive must be reversed");
        setConstantSpeed(loco, 10);
        runTicks(8);

        assertEquals(0, crashes.get(), "reverse must reset the last-curve history");
        assertFalse(loco.isDestroying(), "locomotive must remain intact");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. Frenada sin detenerse + re-aceleración → descarrila (tiempo efectivo)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("6.1 Braking without stopping then re-accelerating within interval → derails")
    void brakeWithoutStopping_reaccelerate_derails() {
        LayoutCurves l = buildAdjacentCurves();
        Train t = placeTrain(l.lead, Dir.W);
        Locomotive loco = director(t);
        AtomicInteger crashes = crashCounter(t);
        setConstantSpeed(loco, 10);

        // Cruza la 1ª curva (tick 5) y frena a velocidad 1 (sin detenerse del todo).
        runTicks(6);
        setConstantSpeed(loco, 1);
        runTicks(5);

        // Re-acelera a tope: entran menos de 12 ticks desde la 1ª curva en total,
        // por lo que descarrila en la 2ª curva.
        setConstantSpeed(loco, 10);
        runTicks(8);

        assertTrue(crashes.get() >= 1,
                "re-accelerating within the interval (without stopping) must derail");
        assertTrue(loco.isDestroying(), "locomotive must be destroying after derailment");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Geometría con dos curvas adyacentes: recto → curva(giro S) → curva(giro E) → rectos. */
    private LayoutCurves buildAdjacentCurves() {
        LayoutCurves l = new LayoutCurves();
        l.lead = track(0, 0, Dir.E, Dir.W);
        l.curve1 = track(1, 0, Dir.W, Dir.S); // entra rumbo E, sale rumbo S
        l.curve2 = track(1, 1, Dir.N, Dir.E); // entra rumbo S, sale rumbo E
        l.tail1 = track(2, 1, Dir.W, Dir.E);
        l.tail2 = track(3, 1, Dir.W, Dir.E);
        l.tail3 = track(4, 1, Dir.W, Dir.E);
        connect(l.lead, Dir.E, l.curve1, Dir.W);
        connect(l.curve1, Dir.S, l.curve2, Dir.N);
        connect(l.curve2, Dir.E, l.tail1, Dir.W);
        connect(l.tail1, Dir.E, l.tail2, Dir.W);
        connect(l.tail2, Dir.E, l.tail3, Dir.W);
        return l;
    }

    /** Geometría: recto de entrada → curva → gap rectos (N→S) → curva → rectos. */
    private LayoutCurves buildCurvesWithGap(int gapCells) {
        LayoutCurves l = new LayoutCurves();
        l.lead = track(0, 0, Dir.E, Dir.W);
        l.curve1 = track(1, 0, Dir.W, Dir.S);
        connect(l.lead, Dir.E, l.curve1, Dir.W);
        RailTrack prev = l.curve1;
        int y = 1;
        for (int i = 0; i < gapCells; i++, y++) {
            RailTrack straight = track(1, y, Dir.N, Dir.S);
            connect(prev, Dir.S, straight, Dir.N);
            prev = straight;
        }
        l.curve2 = track(1, y, Dir.N, Dir.E);
        connect(prev, Dir.S, l.curve2, Dir.N);
        l.tail1 = track(2, y, Dir.W, Dir.E);
        l.tail2 = track(3, y, Dir.W, Dir.E);
        connect(l.curve2, Dir.E, l.tail1, Dir.W);
        connect(l.tail1, Dir.E, l.tail2, Dir.W);
        return l;
    }

    /** Curvas con un recto intermedio y cola recta hacia el oeste para poder invertir la marcha. */
    private LayoutCurves buildCurvesWithReverseTail() {
        LayoutCurves l = new LayoutCurves();
        l.west2 = track(-2, 0, Dir.W, Dir.E);
        l.west1 = track(-1, 0, Dir.W, Dir.E);
        l.lead = track(0, 0, Dir.E, Dir.W);
        l.curve1 = track(1, 0, Dir.W, Dir.S);
        l.mid = track(1, 1, Dir.N, Dir.S); // recto intermedio para frenar/invertir
        l.curve2 = track(1, 2, Dir.N, Dir.E);
        l.tail1 = track(2, 2, Dir.W, Dir.E);
        connect(l.west2, Dir.E, l.west1, Dir.W);
        connect(l.west1, Dir.E, l.lead, Dir.W);
        connect(l.lead, Dir.E, l.curve1, Dir.W);
        connect(l.curve1, Dir.S, l.mid, Dir.N);
        connect(l.mid, Dir.S, l.curve2, Dir.N);
        connect(l.curve2, Dir.E, l.tail1, Dir.W);
        return l;
    }

    /** Línea recta con un desvío en medio (recorrido recto por el desvío). */
    private LayoutFork buildStraightFork() {
        LayoutFork l = new LayoutFork();
        l.lead = track(0, 0, Dir.E, Dir.W);
        l.fork = fork(1, 0);
        l.fork.addRoute(Dir.W, Dir.E);
        l.fork.addRoute(Dir.E, Dir.W); // straight
        l.fork.setNormalRoute();
        l.tail1 = track(2, 0, Dir.W, Dir.E);
        l.tail2 = track(3, 0, Dir.W, Dir.E);
        l.tail3 = track(4, 0, Dir.W, Dir.E);
        connect(l.lead, Dir.E, l.fork, Dir.W);
        connect(l.fork, Dir.E, l.tail1, Dir.W);
        connect(l.tail1, Dir.E, l.tail2, Dir.W);
        connect(l.tail2, Dir.E, l.tail3, Dir.W);
        return l;
    }

    private RailTrack track(int x, int y, Dir from, Dir to) {
        RailTrack t = new RailTrack();
        t.setPosition(new Point(x, y));
        t.addRoute(from, to);
        t.addRoute(to, from);
        model.getRailMap().addTrack(new Point(x, y), t);
        return t;
    }

    private ForkRailTrack fork(int x, int y) {
        ForkRailTrack f = new ForkRailTrack(model.nextForkId());
        f.setPosition(new Point(x, y));
        model.getRailMap().addTrack(new Point(x, y), f);
        model.addFork(f);
        return f;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    /**
     * Coloca una locomotora en {@code startTrack} entrando por el puerto {@code entryFrom}; el tren
     * avanza en la dirección opuesta (p. ej. entrada por Dir.W → marcha hacia el E).
     */
    private Train placeTrain(RailTrack startTrack, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        startTrack.enterLinkerFromDir(entryFrom, loco);
        return train;
    }

    private Locomotive director(Train t) {
        return (Locomotive) t.getDirectorLinker();
    }

    /** Velocidad física constante (sin rampa de inercia): current == target == speed. */
    private void setConstantSpeed(Locomotive loco, int speed) {
        loco.setCurrentSpeed(speed);
        loco.setTargetSpeed(speed);
    }

    private AtomicInteger crashCounter(Train t) {
        AtomicInteger crashes = new AtomicInteger();
        t.addCoreTrainEventListener(new CoreTrainEventListener() {
            @Override
            public void onCrash(Train train, letrain.map.Point pos, int speed) {
                crashes.incrementAndGet();
            }
        });
        return crashes;
    }

    private void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            model.getScheduler().tick();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
        }
        model.removeDestroyedTrains();
    }

    /** Contenedor de las piezas de una geometría de dos curvas. */
    private static final class LayoutCurves {
        RailTrack west2;
        RailTrack west1;
        RailTrack lead;
        RailTrack curve1;
        RailTrack mid;
        RailTrack curve2;
        RailTrack tail1;
        RailTrack tail2;
        RailTrack tail3;
    }

    /** Contenedor de las piezas de una línea recta con desvío. */
    private static final class LayoutFork {
        RailTrack lead;
        ForkRailTrack fork;
        RailTrack tail1;
        RailTrack tail2;
        RailTrack tail3;
    }
}
