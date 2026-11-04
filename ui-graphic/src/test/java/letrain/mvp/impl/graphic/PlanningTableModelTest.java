package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure formatting and action rules of the 3D Planning window. */
@DisplayName("3D planning table")
class PlanningTableModelTest {

    private static RoutePlan plan(double cost, long ticks) {
        return RoutePlan.feasible(List.of(new RouteStep(new Point(1, 2), TrackType.NORMAL_TRACK),
                new RouteStep(new Point(4, 5), TrackType.NORMAL_TRACK)), cost, ticks);
    }

    private static PlannedWork draft() {
        return PlannedWork.draft(1, new Point(1, 2), new Point(4, 5), RoutePolicy.DIRECT,
                plan(1000, 1200), 1200);
    }

    @Test
    @DisplayName("the header centres every column over the row layout")
    void should_CentreHeaderColumns() {
        String header = PlanningTableModel.header();
        PlanningTableModel.Row row = new PlanningTableModel.Row(1, PlannedWork.State.DRAFT, "1,2",
                "4,5", "DIRECT", "CHAPUCERT", "Low", 3, "$1.000", "1 h");

        assertTrue(header.startsWith("#  |   State   |"), header);
        assertTrue(header.contains("| Cells |"), header);
        assertTrue(header.endsWith("|   Time"), header);
        assertEquals(PlanningTableModel.HEADER.length(),
                PlanningTableModel.formatRow(row).length());

        String line = PlanningTableModel.formatRow(row);
        for (int index = 0; index < header.length(); index++) {
            if (header.charAt(index) == '|') {
                assertEquals('|', line.charAt(index), "column separator at " + index);
            }
        }
    }

    @Test
    @DisplayName("a row left-aligns text, right-aligns numbers and separates columns")
    void should_FormatRowLikeThe2dTable() {
        PlanningTableModel.Row row = new PlanningTableModel.Row(1, PlannedWork.State.DRAFT, "1,2",
                "4,5", "DIRECT", "CHAPUCERT", "Low", 3, "$1.000", "1 h");

        String line = PlanningTableModel.formatRow(row);

        assertEquals(" 1 | Draft     |     1,2 |      4,5 | DIRECT      | CHAPUCERT  | Low     |"
                + "     3 |      $1.000 |       1 h", line);
        assertEquals(9, line.chars().filter(c -> c == '|').count());
    }

    @Test
    @DisplayName("the cancelled row shows the real spent cost instead of the quote")
    void should_ShowSpentCostForCancelledWorks() {
        PlannedWork cancelled = draft()
                .withQuote(WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, plan(1000, 1200),
                        WorkQuote.DEFAULT_BASE_DELAY_TICKS_PER_CELL))
                .withState(PlannedWork.State.CANCELLED).withSpentCost(250);

        PlanningTableModel.Row row = PlanningTableModel.row(cancelled);

        assertEquals("$250", row.cost());
        assertEquals("Cancelled", PlanningTableModel.stateLabel(row.state()));
    }

    @Test
    @DisplayName("cost and time use the 2D game formatting rules")
    void should_FormatCostAndTicks() {
        assertEquals("$1.234.500", PlanningTableModel.formatCost(1234500));
        assertEquals("$0", PlanningTableModel.formatCost(0));
        assertEquals("40 min", PlanningTableModel.formatTicks(800));
        assertEquals("1 h", PlanningTableModel.formatTicks(1200));
        assertEquals("1 h 30 min", PlanningTableModel.formatTicks(1800));
        assertEquals("0 min", PlanningTableModel.formatTicks(0));
    }

    @Test
    @DisplayName("the total line uses singular and plural like the 2D dialog")
    void should_FormatTotalLine() {
        assertEquals("Total: 0 works · - · -", PlanningTableModel.totalLine(0, "-", "-"));
        assertEquals("Total: 1 work · $700 · 1 h", PlanningTableModel.totalLine(1, "$700", "1 h"));
        assertEquals("Total: 2 works · $700 · 1 h", PlanningTableModel.totalLine(2, "$700", "1 h"));
    }

    @Test
    @DisplayName("selection wraps with j/k and clamps to the available rows")
    void should_MoveAndClampSelection() {
        assertEquals(0, PlanningTableModel.clampIndex(-4, 3));
        assertEquals(2, PlanningTableModel.clampIndex(9, 3));
        assertEquals(-1, PlanningTableModel.clampIndex(0, 0));
        assertEquals(2, PlanningTableModel.moveIndex(0, 3, -1));
        assertEquals(0, PlanningTableModel.moveIndex(2, 3, 1));
        assertEquals(1, PlanningTableModel.moveIndex(0, 3, 1));
        assertEquals(0, PlanningTableModel.moveIndex(-1, 3, 1));
        assertEquals(2, PlanningTableModel.moveIndex(-1, 3, -1));
    }

    @Test
    @DisplayName("actions follow the work state, never allowing delete of working/paused")
    void should_EnableActionsByState() {
        PlannedWork draft = draft();
        PlannedWork working = draft.withState(PlannedWork.State.WORKING);
        PlannedWork paused = draft.withState(PlannedWork.State.PAUSED);
        PlannedWork done = draft.withState(PlannedWork.State.DONE);

        assertTrue(PlanningTableModel.canRun(draft));
        assertFalse(PlanningTableModel.canRun(working));
        assertTrue(PlanningTableModel.canPause(working));
        assertTrue(PlanningTableModel.canResume(paused));
        assertTrue(PlanningTableModel.canCancel(working));
        assertTrue(PlanningTableModel.canCancel(paused));
        assertFalse(PlanningTableModel.canCancel(done));
        assertTrue(PlanningTableModel.canDelete(draft));
        assertTrue(PlanningTableModel.canDelete(done));
        assertFalse(PlanningTableModel.canDelete(working));
        assertFalse(PlanningTableModel.canDelete(paused));
        assertTrue(PlanningTableModel.canChooseQuote(draft));
        assertFalse(PlanningTableModel.canChooseQuote(paused));
    }

    @Test
    @DisplayName("working and paused works block edits to the rest of the list")
    void should_BlockListActionsWhileWorkIsActiveOrPaused() {
        PlannedWork draft = draft();
        PlannedWork working = draft.withState(PlannedWork.State.WORKING);
        PlannedWork paused = draft.withState(PlannedWork.State.PAUSED);
        PlannedWork done = draft.withState(PlannedWork.State.DONE);

        assertFalse(PlanningTableModel.hasBlockingWork(List.of(draft, done)));
        assertTrue(PlanningTableModel.hasBlockingWork(List.of(draft, working)));
        assertTrue(PlanningTableModel.hasBlockingWork(List.of(draft, paused)));
        assertFalse(PlanningTableModel.hasBlockingWork(List.of()));
        assertFalse(PlanningTableModel.hasBlockingWork(null));
    }

    @Test
    @DisplayName("a feasible quote row shows prices and a disabled one is marked unavailable")
    void should_FormatQuoteRows() {
        RoutePlan normal = plan(1000, 1200);
        WorkQuote feasible = WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, normal,
                WorkQuote.DEFAULT_BASE_DELAY_TICKS_PER_CELL);

        String row = PlanningTableModel.formatQuoteRow(feasible);
        assertEquals(PlanningTableModel.QUOTE_HEADER.length(), row.length());
        assertTrue(row.startsWith("CHAPUCERT "), row);
        assertTrue(row.contains("$700"), row);

        RoutePlan bridge = RoutePlan
                .feasible(List.of(new RouteStep(new Point(0, 0), TrackType.BRIDGE_TRACK)), 100, 10);
        WorkQuote unavailable = WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, bridge);
        String unavailableRow = PlanningTableModel.formatQuoteRow(unavailable);

        assertFalse(unavailable.feasible());
        assertTrue(unavailableRow.endsWith(" !"), unavailableRow);

        WorkQuote imperial = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, normal,
                WorkQuote.DEFAULT_BASE_DELAY_TICKS_PER_CELL);
        assertTrue(PlanningTableModel.formatQuoteRow(imperial).contains("Bridge+Tunnel"));
        assertFalse(PlanningTableModel.formatQuoteRow(feasible).contains("Bridge+Tunnel"));
    }
}
