package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import letrain.itinerary.AutoPilot;
import letrain.mvp.Model.GameMode;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.Segment;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;

/**
 * Generates text reports and menu definitions for the game model.
 */
@JsonIgnoreType
public final class ModelReportService {

    private ModelReportService() {}

    public static List<GameModeMenuOption> createMenuModel(Model model) {
        return Arrays.asList(new GameModeMenuOption("&Rails",
                "[⏴⏵⏶⏷/hjkl]: Move [Shift]: Add rail [Ctrl]: Remove rail [Ins]: Add sensor [Home]: Add sem [Del]: Add speed [End]: Add station [#]: Steps [Space]: Reset steps",
                () -> true, () -> (model.getEffectiveMode() == GameMode.RAILS),
                () -> (GameMode.RAILS)),
                new GameModeMenuOption("&Add",
                        "[n]: Station [e]: Sensor [s]: Semaphore [g]: Speed signal", () -> true,
                        () -> model.getEffectiveMode() == GameMode.ADD, () -> GameMode.ADD),
                new GameModeMenuOption("&Drive",
                        "[⏴⏵/hl]: Select [o]: Locate [m]: Motor [⏶/k]: Accel [⏷/j]: Decel [Space]: Rev [Enter]: Load [#]: ID",
                        () -> !model.getLocomotives().isEmpty(),
                        () -> model.getEffectiveMode() == GameMode.DRIVE, () -> GameMode.DRIVE),
                new GameModeMenuOption("&Forks",
                        "[⏴⏵/hl]: Select [o]: Locate [Space]: Toggle [#]: ID",
                        () -> !model.getForks().isEmpty(),
                        () -> model.getEffectiveMode() == GameMode.FORKS, () -> GameMode.FORKS),
                new GameModeMenuOption("&Semaphores",
                        "[⏴⏵/hl]: Select [o]: Locate [Space]: Toggle [#]: ID",
                        () -> !model.getSemaphores().isEmpty(),
                        () -> model.getEffectiveMode() == GameMode.SEMAPHORES,
                        () -> GameMode.SEMAPHORES),
                new GameModeMenuOption("S&ensors",
                        "[⏴⏵/hl]: Select [o]: Locate [Space]: Invert [#]: ID",
                        () -> model.getSensors().stream()
                                .anyMatch(s -> s.getClass() == letrain.track.Sensor.class),
                        () -> model.getEffectiveMode() == GameMode.SENSORS, () -> GameMode.SENSORS),
                new GameModeMenuOption("Si&gnals",
                        "[⏴⏵/hl]: Select [m]: Max/Min [⏶⏷/kj]: Limit [Space]: Invert",
                        () -> !model.getSpeedSignals().isEmpty(),
                        () -> model.getEffectiveMode() == GameMode.SPEED_SIGNALS,
                        () -> GameMode.SPEED_SIGNALS),
                new GameModeMenuOption("&Trains",
                        "[A-Z]: Locomotive | [a-z]: Wagon | [Enter]: Finish",
                        () -> model.getCursorRailTrack() != null,
                        () -> model.getEffectiveMode() == GameMode.TRAINS, () -> GameMode.TRAINS),
                new GameModeMenuOption("&Couple",
                        "[⏶⏷/kj]: Front/Back [⏴⏵/hl]: Sel wagons [o]: Locate [Space]: Couple",
                        () -> model.canEnterLinkMode(),
                        () -> model.getEffectiveMode() == GameMode.LINK, () -> GameMode.LINK),
                new GameModeMenuOption("&Uncouple",
                        "[⏶⏷/kj]: Front/Back [⏴⏵/hl]: Sel wagons [o]: Locate [Space]: Uncouple",
                        () -> model.canEnterUnlinkMode(),
                        () -> model.getEffectiveMode() == GameMode.UNLINK, () -> GameMode.UNLINK),
                new GameModeMenuOption("&Program",
                        "Scenario editor (Save/Load/Export/Import/Close)", () -> true,
                        () -> model.getEffectiveMode() == GameMode.PROGRAM, () -> GameMode.PROGRAM),
                new GameModeMenuOption("Statio&ns", "[⏴⏵/hl]: Select [o]: Locate [#]: ID",
                        () -> !model.getStations().isEmpty(),
                        () -> model.getEffectiveMode() == GameMode.STATIONS,
                        () -> GameMode.STATIONS));
    }

    public static String generateGameObjectsReport(Model model) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- TRAINS ---\n");
        Set<Train> processedTrains = new HashSet<>();
        for (Locomotive loco : model.getLocomotives()) {
            Train train = loco.getTrain();
            if (train != null && !processedTrains.contains(train)) {
                processedTrains.add(train);
                sb.append("Train ID: ").append(train.getId()).append("\n");
                sb.append("  Segments Owned: ");
                List<Segment> owned = model.getBlockManager().getOwnedSegments(train);
                for (Segment s : owned) {
                    sb.append(s.getId()).append(" ");
                }
                sb.append("\n");

                sb.append("  Current Segment: ")
                        .append(train.getSafetyManager().getCurrentSegment() != null
                                ? train.getSafetyManager().getCurrentSegment().getId()
                                : "None")
                        .append("\n");
                sb.append("  Next Segment: ")
                        .append(train.getSafetyManager().getNextSegment() != null
                                ? train.getSafetyManager().getNextSegment().getId()
                                : "None")
                        .append("\n");
                if (!train.getSafetyManager().hasPermissionToMove()
                        && train.getSafetyManager().getNextSegment() != null) {
                    List<Train> blockers = model.getBlockManager()
                            .getOwners(train.getSafetyManager().getNextSegment());
                    sb.append("  Permission: WAITING (Blocked by: ");
                    if (blockers.isEmpty()) {
                        sb.append("Logic/Retry Timer");
                    } else {
                        for (Train b : blockers) {
                            sb.append("Train ").append(b.getId()).append(" ");
                        }
                    }
                    sb.append(")\n");
                } else {
                    sb.append("  Permission: ").append(
                            train.getSafetyManager().hasPermissionToMove() ? "GRANTED" : "WAITING")
                            .append("\n");
                }

                for (String line : train.describeComposition().split("\n")) {
                    sb.append("  ").append(line).append("\n");
                }
                if (train.getDirectorLinker() != null) {
                    if (train.getDirectorLinker() instanceof Linker) {
                        sb.append("  Pos: ")
                                .append(((Linker) train.getDirectorLinker()).getPosition())
                                .append("\n");
                    }
                    sb.append("  Speed: ").append(train.getDirectorLinker().getSpeed())
                            .append("\n");
                }
                sb.append("  State: ").append(describeTrainState(train)).append("\n");
                for (Linker linker : train.getLinkers()) {
                    if (linker instanceof Wagon) {
                        Wagon w = (Wagon) linker;
                        if (w.getCargoAmount() > 0) {
                            sb.append("    Wagon: ").append(w.getCargoType()).append(" (")
                                    .append(w.getCargoAmount()).append("/")
                                    .append(w.getMaxCapacity()).append(")\n");
                        }
                    }
                }
            }
        }
        sb.append("\n--- STATIONS ---\n");
        for (Station s : model.getStations()) {
            sb.append("Station ").append(s.getId()).append(": ").append(s.getRole()).append(" ")
                    .append(s.getCargoType()).append(" (").append(s.getStorage()).append("/")
                    .append(s.getMaxStorage()).append(") @ ").append(s.getPosition()).append("\n");
        }
        sb.append("\n--- SENSORS ---\n");
        for (Sensor s : model.getSensors()) {
            if (!(s instanceof Station)) {
                sb.append("Sensor ").append(s.getId()).append(" @ ").append(s.getPosition())
                        .append("\n");
            }
        }
        sb.append("\n--- FORKS ---\n");
        for (ForkRailTrack f : model.getForks()) {
            sb.append("Fork ").append(f.getId()).append(" @ ").append(f.getPosition()).append(" (")
                    .append(f.isUsingAlternativeRoute() ? "Alternative" : "Normal").append(")\n");
        }
        sb.append("\n--- SEMAPHORES ---\n");
        for (RailSemaphore s : model.getSemaphores()) {
            sb.append("Semaphore ").append(s.getId()).append(" @ ").append(s.getPosition())
                    .append(" (").append(s.isOpen() ? "OPEN" : "CLOSED").append(")\n");
        }
        return sb.toString();
    }

    /**
     * Operational state of a train for the {@code info} report, derived from the real runtime flags
     * (issue #701). Fixed priority:
     *
     * <ol>
     * <li>{@code LOADING at Station N} — loading/unloading
     * <li>{@code STALLED} — collision/dead-end stall
     * <li>{@code WAITING FOR BLOCK} — the safety layer is holding the train for its next canton
     * <li>{@code HOLDING FOR DEPARTURE} — autopilot waiting for a scheduled departure
     * <li>{@code PARKED} (engine off with autopilot armed) / {@code ENGINE OFF} (engine off,
     * manual)
     * <li>{@code BRAKING} — rolling with a full-stop order (target speed 0)
     * <li>autopilot modes: {@code CRUISING} (following and moving), {@code IDLE},
     * {@code REVERSING}, {@code ERROR}
     * <li>{@code STOPPED (MANUAL)} — fallback
     * </ol>
     *
     * <p>
     * Package-private so the mapping can be unit-tested without going through the whole report.
     */
    static String describeTrainState(Train train) {
        if (train.getLogisticsManager() != null && train.getLogisticsManager().isLoading()) {
            Station station = train.getLogisticsManager().getStationAtTrain();
            return station != null ? "LOADING at Station " + station.getId() : "LOADING";
        }
        if (train.isStalled()) {
            return "STALLED";
        }
        if (train.getSafetyManager() != null && train.getSafetyManager().isWaitingForBlock()) {
            return "WAITING FOR BLOCK";
        }
        if (train.isHeldBySchedule()) {
            return "HOLDING FOR DEPARTURE";
        }

        Locomotive locomotive = train.getDirectorLinker() instanceof Locomotive
                ? (Locomotive) train.getDirectorLinker()
                : null;
        // An engine-off train is parked (autopilot armed) or simply switched off (manual). The
        // check runs before the autopilot modes: a train told to stop the engine must never be
        // reported as following/cruising even if the speed counter has not settled to zero yet.
        if (locomotive != null && !locomotive.isEngineOn()) {
            return train.isAutoMode() ? "PARKED" : "ENGINE OFF";
        }
        if (locomotive != null && locomotive.isBraking() && locomotive.getTargetSpeed() == 0) {
            return "BRAKING";
        }

        AutoPilot autopilot = train.getAutopilot();
        AutoPilot.Mode mode = autopilot != null ? autopilot.mode() : AutoPilot.Mode.IDLE;
        boolean stopped = train.isStopped();
        switch (mode) {
            case FOLLOWING:
                return stopped ? "IDLE" : "CRUISING";
            case WAITING:
                return "HOLDING FOR DEPARTURE";
            case REVERSING:
                return "REVERSING";
            case ERROR:
                return "ERROR";
            case IDLE:
            default:
                return stopped ? "STOPPED (MANUAL)" : "CRUISING";
        }
    }

    public static String generateRailwayGraphReport(Model model) {
        StringBuilder sb = new StringBuilder();
        sb.append(model.getRailwayGraph().toString());

        sb.append("\n\n--- SEGMENT OWNERSHIP ---\n");
        BlockManager bm = model.getBlockManager();
        Set<Segment> segments = bm.getAllLockedSegments();

        if (segments.isEmpty()) {
            sb.append("No active segment locks.\n");
        } else {
            for (Segment s : segments) {
                List<Train> owners = bm.getOwners(s);
                if (!owners.isEmpty()) {
                    sb.append("Segment ").append(s.getId()).append(" owned by: ");
                    for (Train train : owners) {
                        sb.append("Train ").append(train.getId()).append(" ");
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("\n").append(generateGameObjectsReport(model));
        return sb.toString();
    }
}
