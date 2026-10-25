package letrain.mvp.impl.terminal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Pure mapping between a {@link RoutePlan} and the glyphs painted on the 2D map (ADR-035).
 *
 * <p>
 * This class deliberately knows nothing about Lanterna, the viewport or colours: it returns map
 * coordinates and one-character glyphs, so the mapping can be tested without a terminal. The
 * presenter/view layer decides which cells are visible and how to colour them.
 *
 * <p>
 * A and B are drawn last, so an endpoint always wins over the route glyph on the same cell. Only
 * one plan is represented at a time; an infeasible plan contributes no route glyphs but its
 * endpoints, when known, are still returned so the author can see what was picked.
 */
public final class RoutePreviewOverlay {

    public static final String NORMAL_GLYPH = "═";
    public static final String BRIDGE_GLYPH = "≈";
    public static final String TUNNEL_GLYPH = "▒";
    public static final String START_GLYPH = "A";
    public static final String END_GLYPH = "B";

    private RoutePreviewOverlay() {}

    /** One map cell to paint: coordinates, glyph and whether it is an endpoint marker. */
    public record Cell(int x, int y, String glyph, boolean endpoint) {
    }

    /**
     * Returns the overlay cells for {@code plan} plus the picked endpoints.
     *
     * @param plan current plan; {@code null} or infeasible contributes no route glyphs
     * @param start first endpoint, if already picked
     * @param end second endpoint, if already picked
     */
    public static List<Cell> cells(RoutePlan plan, Point start, Point end) {
        Map<Long, Cell> byPosition = new LinkedHashMap<>();
        if (plan != null && plan.feasible()) {
            for (RouteStep step : plan.steps()) {
                Point point = step.point();
                byPosition.put(key(point),
                        new Cell(point.getX(), point.getY(), glyphFor(step.trackType()), false));
            }
        }
        if (start != null) {
            byPosition.put(key(start), new Cell(start.getX(), start.getY(), START_GLYPH, true));
        }
        if (end != null) {
            byPosition.put(key(end), new Cell(end.getX(), end.getY(), END_GLYPH, true));
        }
        return List.copyOf(byPosition.values());
    }

    /** Glyph for a planned track kind: normal, bridge or tunnel. */
    public static String glyphFor(TrackType type) {
        if (type == TrackType.TUNNEL_TRACK || type == TrackType.TUNNEL_GATE_TRACK) {
            return TUNNEL_GLYPH;
        }
        if (type == TrackType.BRIDGE_TRACK || type == TrackType.BRIDGE_GATE_TRACK) {
            return BRIDGE_GLYPH;
        }
        return NORMAL_GLYPH;
    }

    private static long key(Point point) {
        return (((long) point.getX()) << 32) ^ (point.getY() & 0xFFFFFFFFL);
    }
}
