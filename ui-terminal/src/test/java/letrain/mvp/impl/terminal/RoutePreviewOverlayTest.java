package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.mvp.impl.terminal.RoutePreviewOverlay.Style;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D route preview: overlay cell mapping")
class RoutePreviewOverlayTest {

    private static RouteStep step(int x, int y, TrackType type) {
        return new RouteStep(new Point(x, y), type);
    }

    @Test
    @DisplayName("every route kind uses the same circle; only the style differs")
    void mapsTrackTypesToTheSameGlyphAndDifferentStyles() {
        RoutePlan plan = RoutePlan.feasible(List.of(step(0, 0, TrackType.NORMAL_TRACK),
                step(1, 0, TrackType.BRIDGE_TRACK), step(2, 0, TrackType.TUNNEL_TRACK)), 0, 0);

        List<RoutePreviewOverlay.Cell> cells = RoutePreviewOverlay.cells(plan, null, null);

        assertEquals(3, cells.size());
        assertEquals("⬤", cells.get(0).glyph());
        assertSame(Style.NORMAL, cells.get(0).style());
        assertEquals("⬤", cells.get(1).glyph());
        assertSame(Style.SPECIAL, cells.get(1).style());
        assertEquals("⬤", cells.get(2).glyph());
        assertSame(Style.SPECIAL, cells.get(2).style());
    }

    @Test
    @DisplayName("the route glyph is the documented large black circle")
    void routeGlyphIsTheLargeBlackCircle() {
        assertEquals("\u2B24", RoutePreviewOverlay.ROUTE_GLYPH);
        assertEquals("⬤", RoutePreviewOverlay.ROUTE_GLYPH);
    }

    @Test
    @DisplayName("the endpoint marker wins over the route glyph and stays normal-styled")
    void endpointsWinOverRouteGlyphs() {
        RoutePlan plan = RoutePlan.feasible(
                List.of(step(0, 0, TrackType.TUNNEL_TRACK), step(1, 0, TrackType.NORMAL_TRACK)), 0,
                0);

        List<RoutePreviewOverlay.Cell> cells =
                RoutePreviewOverlay.cells(plan, new Point(0, 0), new Point(0, 0));

        assertEquals(2, cells.size());
        assertEquals("B", cells.get(0).glyph());
        assertTrue(cells.get(0).endpoint());
        assertSame(Style.NORMAL, cells.get(0).style());
        assertEquals("⬤", cells.get(1).glyph());
        assertFalse(cells.get(1).endpoint());
    }

    @Test
    @DisplayName("an infeasible plan contributes no route glyphs but keeps the endpoints")
    void infeasiblePlanKeepsOnlyEndpoints() {
        List<RoutePreviewOverlay.Cell> cells = RoutePreviewOverlay
                .cells(RoutePlan.infeasible("no route"), new Point(4, 5), new Point(8, 9));

        assertEquals(2, cells.size());
        assertEquals("A", cells.get(0).glyph());
        assertEquals(4, cells.get(0).x());
        assertEquals(5, cells.get(0).y());
        assertEquals("B", cells.get(1).glyph());
        assertSame(Style.NORMAL, cells.get(1).style());
    }

    @Test
    @DisplayName("gate variants keep the circle and the special style")
    void mapsGateVariants() {
        assertEquals("⬤", RoutePreviewOverlay.glyphFor(TrackType.BRIDGE_GATE_TRACK));
        assertEquals("⬤", RoutePreviewOverlay.glyphFor(TrackType.TUNNEL_GATE_TRACK));
        assertSame(Style.SPECIAL, RoutePreviewOverlay.styleFor(TrackType.BRIDGE_GATE_TRACK));
        assertSame(Style.SPECIAL, RoutePreviewOverlay.styleFor(TrackType.TUNNEL_GATE_TRACK));
        assertSame(Style.NORMAL, RoutePreviewOverlay.styleFor(TrackType.NORMAL_TRACK));
    }
}
