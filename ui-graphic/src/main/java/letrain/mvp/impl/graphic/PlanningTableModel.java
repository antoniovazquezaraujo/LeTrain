package letrain.mvp.impl.graphic;

import java.util.List;
import java.util.Locale;
import letrain.construction.ConstructionCompany;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.ConstructionPlanList;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;

/**
 * Pure formatting and selection rules for the 3D Planning window.
 *
 * <p>
 * The 2D terminal already formats its planned-works table in {@code ConstructionPlansDialog}; this
 * class reproduces the same column layout for the Scene2D window without depending on a GL context,
 * so the alignment and the disabled-action rules can be pinned with focused unit tests.
 *
 * <p>
 * Headers are centred, text columns are left-aligned and numeric columns are right-aligned, with a
 * vertical {@code |} separator between every column. Costs and times use the same game currency and
 * 20-ticks-per-minute conversion as the terminal.
 */
public final class PlanningTableModel {

    /** Table column widths, kept in sync with the 2D dialog. */
    static final int ID_WIDTH = 2;
    static final int STATE_WIDTH = 9;
    static final int FROM_WIDTH = 7;
    static final int TO_WIDTH = 8;
    static final int POLICY_WIDTH = 11;
    static final int COMPANY_WIDTH = 10;
    static final int QUALITY_WIDTH = 7;
    static final int CELLS_WIDTH = 5;
    static final int COST_WIDTH = 11;
    static final int TIME_WIDTH = 9;
    private static final char COLUMN_SEPARATOR = '|';

    /** Game ticks that make one game minute (20 TPS, one game minute per real second). */
    static final long TICKS_PER_GAME_MINUTE = 20;

    static final int QUOTE_COMPANY_WIDTH = 10;
    static final int QUOTE_COST_WIDTH = 11;
    static final int QUOTE_TIME_WIDTH = 8;
    static final int QUOTE_QUALITY_WIDTH = 8;
    static final int QUOTE_CAPABILITIES_WIDTH = 13;

    /** One display row; every field is already presentation text. */
    public record Row(int id, PlannedWork.State state, String from, String to, String policy,
            String company, String quality, int cells, String cost, String time) {
    }

    /** Header of the works table. */
    static final String HEADER = buildHeader();

    /** Header of the company quote chooser. */
    static final String QUOTE_HEADER = buildQuoteHeader();

    private PlanningTableModel() {}

    private static String buildHeader() {
        StringBuilder line = new StringBuilder();
        appendCentered(line, "#", ID_WIDTH);
        appendSeparator(line);
        appendCentered(line, "State", STATE_WIDTH);
        appendSeparator(line);
        appendCentered(line, "From", FROM_WIDTH);
        appendSeparator(line);
        appendCentered(line, "To", TO_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Policy", POLICY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Company", COMPANY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Quality", QUALITY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Cells", CELLS_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Cost", COST_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Time", TIME_WIDTH);
        return line.toString();
    }

    private static String buildQuoteHeader() {
        StringBuilder line = new StringBuilder();
        appendCentered(line, "Company", QUOTE_COMPANY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Cost", QUOTE_COST_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Time", QUOTE_TIME_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Quality", QUOTE_QUALITY_WIDTH);
        appendSeparator(line);
        appendCentered(line, "Capabilities", QUOTE_CAPABILITIES_WIDTH);
        return line.toString();
    }

    /** Header without the trailing padding introduced by the last centred column. */
    public static String header() {
        return HEADER.stripTrailing();
    }

    public static String quoteHeader() {
        return QUOTE_HEADER.stripTrailing();
    }

    /** Builds the display row for a planned work. */
    public static Row row(PlannedWork work) {
        String company = work.quote().map(quote -> quote.company().name()).orElse("-");
        String quality = work.quote().map(PlanningTableModel::qualityLabel).orElse("-");
        String cost;
        if (work.state() == PlannedWork.State.CANCELLED && work.spentCost().isPresent()) {
            cost = formatCost(work.spentCost().getAsDouble());
        } else {
            cost = work.quote().map(quote -> formatCost(quote.totalCost())).orElse("-");
        }
        String time = work.quote().map(quote -> formatTicks(quote.totalTicks())).orElse("-");
        return new Row(work.id(), work.state(),
                formatPoint(work.start().getX(), work.start().getY()),
                formatPoint(work.end().getX(), work.end().getY()), policyLabel(work.policy()),
                company, quality, work.cells(), cost, time);
    }

    /**
     * One fixed-width table line. Values that overflow their column are truncated so the table
     * never wraps or pushes the following columns.
     */
    public static String formatRow(Row row) {
        StringBuilder line = new StringBuilder();
        appendField(line, Integer.toString(row.id()), ID_WIDTH, true);
        appendSeparator(line);
        appendField(line, stateLabel(row.state()), STATE_WIDTH, false);
        appendSeparator(line);
        appendField(line, row.from(), FROM_WIDTH, true);
        appendSeparator(line);
        appendField(line, row.to(), TO_WIDTH, true);
        appendSeparator(line);
        appendField(line, row.policy(), POLICY_WIDTH, false);
        appendSeparator(line);
        appendField(line, row.company(), COMPANY_WIDTH, false);
        appendSeparator(line);
        appendField(line, row.quality(), QUALITY_WIDTH, false);
        appendSeparator(line);
        appendField(line, Integer.toString(row.cells()), CELLS_WIDTH, true);
        appendSeparator(line);
        appendField(line, row.cost(), COST_WIDTH, true);
        appendSeparator(line);
        appendField(line, row.time(), TIME_WIDTH, true);
        return line.toString();
    }

    /** One fixed-width line of the company quote chooser. */
    public static String formatQuoteRow(WorkQuote quote) {
        String company = fitLeft(quote.company().name(), QUOTE_COMPANY_WIDTH);
        String cost = quote.feasible() ? fitRight(formatCost(quote.totalCost()), QUOTE_COST_WIDTH)
                : fitRight("-", QUOTE_COST_WIDTH);
        String time = quote.feasible() ? fitRight(formatTicks(quote.totalTicks()), QUOTE_TIME_WIDTH)
                : fitRight("-", QUOTE_TIME_WIDTH);
        String quality = fitLeft(qualityLabel(quote), QUOTE_QUALITY_WIDTH);
        String capabilities = fitLeft(capabilityLabel(quote), QUOTE_CAPABILITIES_WIDTH);
        StringBuilder line = new StringBuilder(company);
        appendSeparator(line);
        line.append(cost);
        appendSeparator(line);
        line.append(time);
        appendSeparator(line);
        line.append(quality);
        appendSeparator(line);
        line.append(capabilities);
        return quote.feasible() ? line.toString() : line + " !";
    }

    /** Display label of a work state. */
    public static String stateLabel(PlannedWork.State state) {
        return switch (state) {
            case DRAFT -> "Draft";
            case WORKING -> "Working";
            case PAUSED -> "Paused";
            case DONE -> "Done";
            case CANCELLED -> "Cancelled";
        };
    }

    /** Display label of a construction quality. */
    public static String qualityLabel(WorkQuote quote) {
        return quote.quality() == ConstructionCompany.Quality.HIGH ? "High" : "Low";
    }

    /** Short policy label that fits the table column. */
    public static String policyLabel(letrain.planner.RoutePolicy policy) {
        return switch (policy) {
            case CHEAPEST -> "CHEAPEST";
            case DIRECT -> "DIRECT";
            case AVOID_MOUNTAINS -> "AVOID_MTN";
            case AVOID_WATER -> "AVOID_WATER";
            case AVOID_ALL -> "AVOID_ALL";
        };
    }

    /** Company capabilities required by the quote's route. */
    static String capabilityLabel(WorkQuote quote) {
        boolean bridge = quote.requiredCapabilities().contains(Capability.BRIDGE);
        boolean tunnel = quote.requiredCapabilities().contains(Capability.TUNNEL);
        if (bridge && tunnel) {
            return "Bridge+Tunnel";
        }
        if (bridge) {
            return "Bridge";
        }
        if (tunnel) {
            return "Tunnel";
        }
        return "-";
    }

    /** Game currency with dot grouping, matching the 2D terminal ({@code $1.234.500}). */
    public static String formatCost(double cost) {
        long rounded = Math.round(cost);
        String grouped = String.format(Locale.US, "%,d", Math.abs(rounded)).replace(',', '.');
        return (rounded < 0 ? "-$" : "$") + grouped;
    }

    /**
     * Construction time from game ticks, rounded up so a non-zero build never reads as {@code 0
     * min}.
     */
    public static String formatTicks(long ticks) {
        if (ticks <= 0) {
            return "0 min";
        }
        long totalMinutes = (ticks + TICKS_PER_GAME_MINUTE - 1) / TICKS_PER_GAME_MINUTE;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    /** Total line for the works list, from the exact list figures. */
    public static String totalLine(ConstructionPlanList list) {
        if (list == null || list.isEmpty()) {
            return totalLine(0, "-", "-");
        }
        if (list.quotedCount() == 0) {
            return totalLine(list.size(), "-", "-");
        }
        return totalLine(list.size(), formatCost(list.totalListedCost()),
                formatTicks(list.totalQuotedTicks()));
    }

    /** Total line from already-formatted figures: {@code Total: 3 works · $385.800 · 17 min}. */
    public static String totalLine(long workCount, String cost, String time) {
        return "Total: " + workCount + (workCount == 1 ? " work" : " works") + " · " + cost + " · "
                + time;
    }

    /** Clamps the remembered selection to an existing row; {@code -1} when there are no rows. */
    public static int clampIndex(int index, int rowCount) {
        if (rowCount <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(index, rowCount - 1));
    }

    /** Moves a selection with wrapping, used by j/k and the arrow keys. */
    public static int moveIndex(int index, int rowCount, int delta) {
        if (rowCount <= 0) {
            return -1;
        }
        if (index < 0) {
            return delta > 0 ? 0 : rowCount - 1;
        }
        return Math.floorMod(index + delta, rowCount);
    }

    /** A draft with a chosen quote can be run. */
    public static boolean canRun(PlannedWork work) {
        return work != null && work.state() == PlannedWork.State.DRAFT;
    }

    /** Pause is offered for the active automatic work. */
    public static boolean canPause(PlannedWork work) {
        return work != null && work.state() == PlannedWork.State.WORKING;
    }

    /** Resume is offered for a paused work. */
    public static boolean canResume(PlannedWork work) {
        return work != null && work.state() == PlannedWork.State.PAUSED;
    }

    /** Cancel is offered for a working or paused work. */
    public static boolean canCancel(PlannedWork work) {
        return work != null && (work.state() == PlannedWork.State.WORKING
                || work.state() == PlannedWork.State.PAUSED);
    }

    /** Working and paused works must be cancelled before they can be deleted. */
    public static boolean canDelete(PlannedWork work) {
        return work != null && work.state() != PlannedWork.State.WORKING
                && work.state() != PlannedWork.State.PAUSED;
    }

    /** Quotes are chosen for drafts only. */
    public static boolean canChooseQuote(PlannedWork work) {
        return work != null && work.state() == PlannedWork.State.DRAFT;
    }

    /**
     * True when a working or paused work locks the other list actions. The 2D dialog disables
     * Add/Edit/Quotes/Delete while any work is running or paused; the 3D window keeps the same rule
     * for the actions it offers.
     */
    public static boolean hasBlockingWork(List<PlannedWork> works) {
        return works != null
                && works.stream().anyMatch(work -> work.state() == PlannedWork.State.WORKING
                        || work.state() == PlannedWork.State.PAUSED);
    }

    private static String formatPoint(int x, int y) {
        return x + "," + y;
    }

    private static void appendSeparator(StringBuilder line) {
        line.append(' ').append(COLUMN_SEPARATOR).append(' ');
    }

    private static void appendCentered(StringBuilder line, String text, int width) {
        String value = truncate(text, width);
        int left = (width - value.length()) / 2;
        int right = width - value.length() - left;
        line.append(" ".repeat(left)).append(value).append(" ".repeat(right));
    }

    private static void appendField(StringBuilder line, String text, int width, boolean right) {
        String value = truncate(text, width);
        if (right) {
            line.append(" ".repeat(width - value.length())).append(value);
        } else {
            line.append(value).append(" ".repeat(width - value.length()));
        }
    }

    private static String fitLeft(String text, int width) {
        String value = truncate(text, width);
        return value + " ".repeat(width - value.length());
    }

    private static String fitRight(String text, int width) {
        String value = truncate(text, width);
        return " ".repeat(width - value.length()) + value;
    }

    private static String truncate(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() > width ? value.substring(0, width) : value;
    }
}
