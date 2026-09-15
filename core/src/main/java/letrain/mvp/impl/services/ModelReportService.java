package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                "[⏴⏵⏶⏷/hjkl]:Move [Shift]:Add rail [Ctrl]:Remove rail [Ins]:Add sensor [Home]:Add sem [Del]:Add speed [End]:Add station [#]:Steps [Space]:Reset steps",
                () -> true, () -> (model.getMode() == GameMode.RAILS), () -> (GameMode.RAILS)),
                new GameModeMenuOption("&Add",
                        "[n]:Station [e]:Sensor [s]:Semaphore [g]:Speed Signal", () -> true,
                        () -> model.getMode() == GameMode.ADD, () -> GameMode.ADD),
                new GameModeMenuOption("&Drive",
                        "[⏴⏵/hl]:Select [o]:Locate [m]:Motor [⏶/k]:Accel [⏷/j]:Decel [Space]:Rev [Enter]:Load [#]:ID",
                        () -> !model.getLocomotives().isEmpty(),
                        () -> model.getMode() == GameMode.DRIVE, () -> GameMode.DRIVE),
                new GameModeMenuOption("&Forks", "[⏴⏵/hl]:Select [o]:Locate [Space]:Toggle [#]:ID",
                        () -> !model.getForks().isEmpty(), () -> model.getMode() == GameMode.FORKS,
                        () -> GameMode.FORKS),
                new GameModeMenuOption("&Semaphores",
                        "[⏴⏵/hl]:Select [o]:Locate [Space]:Toggle [#]:ID",
                        () -> !model.getSemaphores().isEmpty(),
                        () -> model.getMode() == GameMode.SEMAPHORES, () -> GameMode.SEMAPHORES),
                new GameModeMenuOption("S&ensors",
                        "[⏴⏵/hl]:Select [o]:Locate [Space]:Invert [#]:ID",
                        () -> model.getSensors().stream()
                                .anyMatch(s -> s.getClass() == letrain.track.Sensor.class),
                        () -> model.getMode() == GameMode.SENSORS, () -> GameMode.SENSORS),
                new GameModeMenuOption("Si&gnals",
                        "[⏴⏵/hl]:Select [m]:Max/Min [⏶⏷/kj]:Limit [Space]:Invert",
                        () -> !model.getSpeedSignals().isEmpty(),
                        () -> model.getMode() == GameMode.SPEED_SIGNALS,
                        () -> GameMode.SPEED_SIGNALS),
                new GameModeMenuOption("&Trains",
                        "[A-Z]: LOCOMOTIVE | [a-z]: WAGON | [ENTER]: FINISH",
                        () -> model.getCursorRailTrack() != null,
                        () -> model.getMode() == GameMode.TRAINS, () -> GameMode.TRAINS),
                new GameModeMenuOption("&Couple",
                        "[⏶⏷/kj]:Front/Back [⏴⏵/hl]:Sel wagons [o]:Locate [Space]:Couple",
                        () -> model.canEnterLinkMode(), () -> model.getMode() == GameMode.LINK,
                        () -> GameMode.LINK),
                new GameModeMenuOption("&Uncouple",
                        "[⏶⏷/kj]:Front/Back [⏴⏵/hl]:Sel wagons [o]:Locate [Space]:Uncouple",
                        () -> model.canEnterUnlinkMode(), () -> model.getMode() == GameMode.UNLINK,
                        () -> GameMode.UNLINK),
                new GameModeMenuOption("&Program",
                        "Scenario editor (Save/Load/Export/Import/Close)", () -> true,
                        () -> model.getMode() == GameMode.PROGRAM, () -> GameMode.PROGRAM),
                new GameModeMenuOption("Statio&ns", "[⏴⏵/hl]:Select [o]:Locate [#]:ID",
                        () -> !model.getStations().isEmpty(),
                        () -> model.getMode() == GameMode.STATIONS, () -> GameMode.STATIONS));
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
                if (train.getLogisticsManager().isLoading()) {
                    sb.append("  State: LOADING at Station ")
                            .append(train.getLogisticsManager().getStationAtTrain().getId())
                            .append("\n");
                } else if (train.isStalled()) {
                    sb.append("  State: STALLED\n");
                } else {
                    sb.append("  State: CRUIZING\n");
                }
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
