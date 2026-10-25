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
 * coordinates, one-character glyphs and a {@link Style} hint, so the mapping can be tested without
 * a terminal. The presenter/view layer decides which cells are visible and how to colour them.
 *
 * <p>
 * A and B are drawn last, so an endpoint always wins over the route glyph on the same cell. Only
 * one plan is represented at a time; an infeasible plan contributes no route glyphs but its
 * endpoints, when known, are still returned so the author can see what was picked.
 */
public final class RoutePreviewOverlay {

    /**
     * One glyph for every route cell: a large filled circle. A route is just a sequence of cells,
     * so the overlay does not suggest a direction. The circle also avoids the Unicode
     * double-diagonal ambiguity of box-drawing glyphs and reads as continuous regardless of the
     * direction the route takes.
     */
    public static final String ROUTE_GLYPH = "⬤";
    public static final String START_GLYPH = "A";
    public static final String END_GLYPH = "B";

    /**
     * How the view should paint a route cell.
     *
     * <ul>
     * <li>{@link #NORMAL}: white foreground, terrain background preserved.</li>
     * <li>{@link #SPECIAL}: tunnel/bridge, red foreground, terrain background preserved.</li>
     * </ul>
     */
    public enum Style {
        NORMAL, SPECIAL
    }

    private RoutePreviewOverlay() {}

    /**
     * One map cell to paint: coordinates, glyph, whether it is an endpoint marker and its style.
     * Endpoints always carry {@link Style#NORMAL}; the view paints endpoint markers on top.
     */
    public record Cell(int x, int y, String glyph, boolean endpoint, Style style) {
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
                byPosition.put(key(point), new Cell(point.getX(), point.getY(),
                        glyphFor(step.trackType()), false, styleFor(step.trackType())));
            }
        }
        if (start != null) {
            byPosition.put(key(start),
                    new Cell(start.getX(), start.getY(), START_GLYPH, true, Style.NORMAL));
        }
        if (end != null) {
            byPosition.put(key(end),
                    new Cell(end.getX(), end.getY(), END_GLYPH, true, Style.NORMAL));
        }
        return List.copyOf(byPosition.values());
    }

    /** Every route cell uses the same circle; only the style (colour) depends on the track kind. */
    public static String glyphFor(TrackType type) {
        return ROUTE_GLYPH;
    }

    /** Special style for tunnels and bridges, normal for plain track. */
    public static Style styleFor(TrackType type) {
        return isTunnel(type) || isBridge(type) ? Style.SPECIAL : Style.NORMAL;
    }

    private static boolean isTunnel(TrackType type) {
        return type == TrackType.TUNNEL_TRACK || type == TrackType.TUNNEL_GATE_TRACK;
    }

    private static boolean isBridge(TrackType type) {
        return type == TrackType.BRIDGE_TRACK || type == TrackType.BRIDGE_GATE_TRACK;
    }

    private static long key(Point point) {
        return (((long) point.getX()) << 32) ^ (point.getY() & 0xFFFFFFFFL);
    }
}
