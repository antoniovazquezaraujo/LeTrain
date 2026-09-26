package letrain.command;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import letrain.itinerary.Itinerary;
import letrain.itinerary.TrainMission;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.track.ForkEventListener;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.Station;
import letrain.track.StationEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandManager extends ScriptLogicParserBaseVisitor<Object> {
    static Logger log = LoggerFactory.getLogger(CommandManager.class);
    Model model;

    /** Stores itineraries created during parsing, keyed by name. */
    private final Map<String, Itinerary> itineraries = new HashMap<>();

    /** Current itinerary being constructed. */
    private ItineraryImpl currentItinerary;

    /**
     * Sink for user-facing problem notices (rejection, unreachable, lost route, stall). The console
     * sets it (typed orders warn on screen); scripts and the program replay leave it null, so their
     * messages go to the log only. Mission success never uses it (issue #619).
     */
    private java.util.function.BiConsumer<String, String> warningSink;

    public CommandManager(Model model) {
        this.model = model;
    }

    public void setWarningSink(java.util.function.BiConsumer<String, String> sink) {
        this.warningSink = sink;
    }

    interface ExecutableCommand {
        void execute(Train contextTrain);
    }

    // ── Statement dispatch ─────────────────────────────────────────

    @Override
    public Object visitScriptStart(ScriptLogicParser.ScriptStartContext ctx) {
        itineraries.clear();
        currentItinerary = null;
        return super.visitScriptStart(ctx);
    }

    @Override
    public Object visitStatement(ScriptLogicParser.StatementContext ctx) {
        if (ctx.trigger() != null) {
            // Existing: event-driven
            List<ExecutableCommand> commands = (List<ExecutableCommand>) visit(ctx.commandBlock());
            setupTrigger(ctx.trigger(), commands);
        } else if (ctx.createItinerary() != null) {
            // create itinerary block — } is the terminator
            visit(ctx.createItinerary());
        } else if (ctx.directCommand() != null) {
            // Other direct commands (assign, autopilot, name)
            visit(ctx.directCommand());
        }
        return null;
    }

    // Direct commands dispatch (assign, autopilot, name)
    @Override
    public Object visitDirectCommand(ScriptLogicParser.DirectCommandContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitCommandBlock(ScriptLogicParser.CommandBlockContext ctx) {
        List<ExecutableCommand> commands = new ArrayList<>();
        for (ScriptLogicParser.CommandItemContext itemCtx : ctx.commandItem()) {
            commands.add((ExecutableCommand) visit(itemCtx));
        }
        return commands;
    }

    private void setupTrigger(ScriptLogicParser.TriggerContext ctx,
            List<ExecutableCommand> commands) {
        if (ctx.sensorSelector() != null) {
            int id = Integer.parseInt(ctx.sensorSelector().NUMBER().getText());
            Sensor sensor = model.getSensor(id);
            if (sensor != null) {
                Integer filterTrainId =
                        (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                                ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                                : null;
                String event = ctx.trainEvent().getChild(0).getText();
                String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText()
                        : null;
                sensor.addSensorEventListener(new SensorEventListener() {
                    @Override
                    public void onEnterTrain(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.startsWith("f") && isForward)
                                || (sense.startsWith("b") && !isForward);
                        if ("enter".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onExitTrain(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.startsWith("f") && isForward)
                                || (sense.startsWith("b") && !isForward);
                        if ("exit".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }
                });
            }
        } else if (ctx.stationSelector() != null) {
            int id = Integer.parseInt(ctx.stationSelector().NUMBER().getText());
            letrain.track.Station station = model.getStation(id);
            if (station != null) {
                if (ctx.trainEvent() != null) {
                    Integer filterTrainId =
                            (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                                    ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                                    : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense =
                            ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText()
                                    : null;
                    station.addStationEventListener(new StationEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("exit".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }
                    });
                }
            }
        } else if (ctx.forkSelector() != null) {
            int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            ForkRailTrack fork = model.getFork(id);
            if (fork != null) {
                if (ctx.trainEvent() != null) {
                    Integer filterTrainId =
                            (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                                    ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                                    : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense =
                            ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText()
                                    : null;
                    fork.addForkEventListener(new ForkEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("exit".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }
                    });
                }
            }
        } else if (ctx.semaphoreSelector() != null) {
            int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
            RailSemaphore semaphore = model.getSemaphore(id);
            if (semaphore != null) {
                if (ctx.trainEvent() != null) {
                    Integer filterTrainId =
                            (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                                    ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                                    : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense =
                            ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText()
                                    : null;
                    semaphore.addSemaphoreEventListener(new letrain.track.SemaphoreEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch =
                                    (sense == null) || (sense.startsWith("f") && isForward)
                                            || (sense.startsWith("b") && !isForward);
                            if ("exit".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }
                    });
                }
            }
        } else if (ctx.trainSelector() != null) {
            Integer filterTrainId = (ctx.trainSelector().NUMBER() != null)
                    ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                    : null;

            if (ctx.trainEvent() != null) {
                String event = ctx.trainEvent().getChild(0).getText();
                String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText()
                        : null;
                model.addScriptTrainEventListener(new ScriptTrainEventListener() {
                    @Override
                    public void onSensorEnter(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.startsWith("f") && isForward)
                                || (sense.startsWith("b") && !isForward);
                        if ("enter".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onSensorExit(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.startsWith("f") && isForward)
                                || (sense.startsWith("b") && !isForward);
                        if ("exit".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onLink(Train train) {
                        if ("link".equals(event)
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onUnlink(Train train) {
                        if ("unlink".equals(event)
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }
                });
            } else if (ctx.getChildCount() >= 3) {
                String event = ctx.getChild(2).getText();
                model.addScriptTrainEventListener(new ScriptTrainEventListener() {
                    @Override
                    public void onCrash(Train train, letrain.map.Point pos, int speed) {
                        if ("crash".equals(event)
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onContact(Train train, letrain.map.Point pos, int speed) {
                        if ("contact".equals(event)
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }
                });
            }
        }
    }

    @Override
    public Object visitCommandItem(ScriptLogicParser.CommandItemContext ctx) {
        if (ctx.semaphoreAction() != null) {
            int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
            String status = ctx.semaphoreAction().semaphoreStatus().getText();
            return (ExecutableCommand) (contextTrain) -> {
                RailSemaphore s = model.getSemaphore(id);
                if (s != null) {
                    s.setOpen("open".equals(status));
                }
            };
        } else if (ctx.forkAction() != null) {
            int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            String dirText = ctx.forkAction().forkDirection() != null
                    ? ctx.forkAction().forkDirection().getText()
                    : null;
            return (ExecutableCommand) (contextTrain) -> {
                ForkRailTrack f = model.getFork(id);
                if (f != null) {
                    if ("straight".equals(dirText)) {
                        f.setStraightRoute();
                    } else if ("curved".equals(dirText)) {
                        f.setCurvedRoute();
                    } else {
                        f.flipRoute();
                    }
                }
            };
        } else if (ctx.trainAction() != null) {
            ExecutableCommand baseAction = buildTrainAction(ctx.trainAction());

            if (ctx.trainSelector() != null) {
                if (ctx.trainSelector().NUMBER() != null) {
                    int id = Integer.parseInt(ctx.trainSelector().NUMBER().getText());
                    return (ExecutableCommand) (contextTrain) -> {
                        Train target = model.getTrainFromLocomotiveId(id);
                        if (target != null) {
                            baseAction.execute(target);
                        }
                    };
                } else {
                    return (ExecutableCommand) (contextTrain) -> {
                        if (contextTrain != null) {
                            baseAction.execute(contextTrain);
                        }
                    };
                }
            } else if (ctx.trainExtractor() != null) {
                ScriptLogicParser.PlaceSelectorContext pCtx = ctx.trainExtractor().placeSelector();
                return (ExecutableCommand) (contextTrain) -> {
                    Train target = findTrainAtPlace(pCtx);
                    if (target != null) {
                        baseAction.execute(target);
                    }
                };
            }
        }
        return (ExecutableCommand) (ct) -> {
        };
    }

    private ExecutableCommand buildTrainAction(ScriptLogicParser.TrainActionContext ctx) {
        String actionText = ctx.getText();
        if (ctx.stopOrder() != null) {
            return buildStopOrder(ctx.stopOrder());
        } else if (ctx.trainSpeed() != null) {
            int speed = Integer.parseInt(ctx.trainSpeed().getText());
            int clampedSpeed = Math.max(0, Math.min(10, speed));
            return (t) -> {
                t.setSpeed(clampedSpeed);
            };
        } else if (actionText.contains("accelerate")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) {
                    tractor.incSpeed();
                }
            };
        } else if (actionText.contains("decelerate")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) {
                    tractor.decSpeed();
                }
            };
        } else if (ctx.trainSense() != null) {
            boolean forward = ctx.trainSense().getText().startsWith("f");
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null && tractor.isReversed() == forward) {
                    tractor.toggleReversed();
                }
            };
        } else if (actionText.contains("invert")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) {
                    tractor.toggleReversed();
                }
            };
        } else if (ctx.coupleAction() != null) {
            ScriptLogicParser.CoupleActionContext lCtx = ctx.coupleAction();
            boolean forward = lCtx.sense().getText().startsWith("f");
            int count = resolveVehicleCount(lCtx.vehicleCount(), 0);
            return (t) -> {
                t.getTrainCouplingManager().prepareLink(t, forward, count);
                t.getTrainCouplingManager().joinLinkers(t);
            };
        } else if (ctx.uncoupleAction() != null) {
            ScriptLogicParser.UncoupleActionContext uCtx = ctx.uncoupleAction();
            boolean forward = uCtx.sense().getText().startsWith("f");
            int count = resolveVehicleCount(uCtx.vehicleCount(), 1);
            return (t) -> {
                t.getTrainCouplingManager().prepareUnlink(t, forward, count);
                t.getTrainCouplingManager().divideTrain(t, () -> model.nextTrainId());
            };

        } else if (ctx.engineAction() != null) {
            boolean turnOn = ctx.engineAction().ON() != null;
            return (t) -> {
                t.getLinkers().forEach(l -> {
                    if (l instanceof letrain.vehicle.rail.impl.Locomotive) {
                        ((letrain.vehicle.rail.impl.Locomotive) l).setEngineOn(turnOn);
                    }
                });
            };
        } else if (actionText.contains("unload")) {

            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null) {
                    t.getLogisticsManager().startUnloadProcess(s);
                }
            };
        } else if (actionText.contains("load")) {
            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null) {
                    t.getLogisticsManager().startLoadProcess(s);
                }
            };
        } else {
            return (t) -> {
            };
        }
    }

    /** Couple/uncouple count: a number, or {@code all} (every vehicle on that side). */
    private int resolveVehicleCount(ScriptLogicParser.VehicleCountContext ctx, int defaultValue) {
        if (ctx == null) {
            return defaultValue;
        }
        if (ctx.ALL() != null) {
            return letrain.vehicle.rail.TrainCouplingManager.ALL;
        }
        return Integer.parseInt(ctx.NUMBER().getText());
    }

    /**
     * Destination and speed of a {@code stopOrder}, shared by loose orders and waypoint actions.
     */
    private record MissionSpec(TrainMission.Kind kind, int targetId, int speed) {}

    /**
     * Resolves a {@code stopOrder} into a mission spec, or null (after warning) when the target is
     * unknown. The waypoint actions (ADR-022 phase 2f) and the loose orders share this.
     */
    private MissionSpec resolveMissionSpec(ScriptLogicParser.StopOrderContext ctx) {
        int speed = ctx.missionSpeed() != null
                ? Integer.parseInt(ctx.missionSpeed().trainSpeed().getText())
                : 0;
        int clamped = Math.max(0, Math.min(10, speed));
        if (ctx.stopTarget().ON() != null) {
            return new MissionSpec(TrainMission.Kind.ON_CONTACT, -1, clamped);
        }
        if (ctx.stopTarget().WHEN() != null) {
            return new MissionSpec(TrainMission.Kind.WHEN_BLOCKED, -1, clamped);
        }
        if (ctx.stopTarget().END() != null) {
            return new MissionSpec(TrainMission.Kind.END_OF_TRACK, -1, clamped);
        }
        if (ctx.stopTarget().stationRef() != null) {
            Station st = resolveStation(ctx.stopTarget().stationRef());
            if (st == null) {
                warnUser("Station not found in 'stop at' order");
                return null;
            }
            return new MissionSpec(TrainMission.Kind.STATION, st.getId(), clamped);
        }
        Sensor se = resolveSensor(ctx.stopTarget().sensorRef());
        if (se == null) {
            warnUser("Sensor not found in 'stop at' order");
            return null;
        }
        return new MissionSpec(TrainMission.Kind.SENSOR, se.getId(), clamped);
    }

    /**
     * Builds a loose one-shot mission order (issue #619): {@code stop at sensor 5 speed 2},
     * {@code stop at station "A"}, {@code stop at end}, {@code stop when blocked} and (issue #645)
     * {@code stop on contact}. Speed 0 (or absent) means "keep the train's current speed".
     */
    private ExecutableCommand buildStopOrder(ScriptLogicParser.StopOrderContext ctx) {
        MissionSpec spec = resolveMissionSpec(ctx);
        if (spec == null) {
            return (t) -> {
            };
        }
        TrainMission mission = switch (spec.kind()) {
            case ON_CONTACT -> TrainMission.stopOnContact(spec.speed());
            case WHEN_BLOCKED -> TrainMission.stopWhenBlocked(spec.speed());
            case END_OF_TRACK -> TrainMission.stopAtEndOfTrack(spec.speed());
            case STATION -> TrainMission.stopAtStation(spec.targetId(), spec.speed());
            case SENSOR -> TrainMission.stopAtSensor(spec.targetId(), spec.speed());
        };
        return (t) -> {
            if (t.getAutopilot() == null) {
                return;
            }
            java.util.function.BiConsumer<String, String> sink = this.warningSink;
            t.getAutopilot().setMissionNotifier(
                    sink != null ? text -> sink.accept("Autopilot", text) : null);
            t.getAutopilot().startMission(mission);
        };
    }

    /** Immediate user notice: console when there is a sink, log otherwise (issue #619). */
    private void warnUser(String text) {
        warnUser("Autopilot", text);
    }

    /** Same with an explicit console title (e.g. "Itinerary"). */
    private void warnUser(String title, String text) {
        log.warn("[DSL] {}", text);
        if (warningSink != null) {
            warningSink.accept(title, text);
        }
    }

    // ── Direct command visitors ──────────────────────────────────────

    @Override
    public Object visitCreateItinerary(ScriptLogicParser.CreateItineraryContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        currentItinerary = new ItineraryImpl();
        for (ScriptLogicParser.WaypointContext wp : ctx.waypoint()) {
            visit(wp);
        }
        if (currentItinerary.isValid()) {
            itineraries.put(name, currentItinerary);
            log.info("[DSL] Created itinerary '{}' with {} waypoints", name,
                    currentItinerary.waypoints().size());
        } else {
            warnUser("Itinerary",
                    "Itinerary '" + name + "' is invalid: an itinerary needs at least 2 waypoints");
        }
        currentItinerary = null;
        return null;
    }

    @Override
    public Object visitWaypoint(ScriptLogicParser.WaypointContext ctx) {
        if (currentItinerary == null) {
            return null;
        }

        Waypoint wp;
        if (ctx.stationRef() != null) {
            Station st = resolveStation(ctx.stationRef());
            if (st == null) {
                return null;
            }
            wp = new WaypointImpl(Waypoint.Type.STATION, st.getId(),
                    Optional.ofNullable(resolveDir(ctx)), resolveCommands(ctx), resolveArrival(ctx),
                    resolveDeparture(ctx));
        } else if (ctx.sensorRef() != null) {
            Sensor se = resolveSensor(ctx.sensorRef());
            if (se == null) {
                return null;
            }
            wp = new WaypointImpl(Waypoint.Type.SENSOR, se.getId(),
                    Optional.ofNullable(resolveDir(ctx)), resolveCommands(ctx), resolveArrival(ctx),
                    resolveDeparture(ctx));
        } else {
            return null;
        }
        currentItinerary.addWaypoint(wp);
        return null;
    }

    private Dir resolveDir(ScriptLogicParser.WaypointContext ctx) {
        if (ctx.direction() != null && ctx.direction().dir() != null) {
            return Dir.valueOf(ctx.direction().dir().getText().toUpperCase());
        }
        return null; // default
    }

    private Optional<LocalTime> resolveArrival(ScriptLogicParser.WaypointContext ctx) {
        ScriptLogicParser.WaypointPlanContext plan = ctx.waypointPlan();
        if (plan == null || plan.arrivalAttr() == null || plan.arrivalAttr().TIME() == null) {
            // TIME() is null only after parser error recovery; the diagnostic is already reported.
            return Optional.empty();
        }
        return Optional.of(parseTime(plan.arrivalAttr().TIME().getText()));
    }

    private Optional<LocalTime> resolveDeparture(ScriptLogicParser.WaypointContext ctx) {
        ScriptLogicParser.WaypointPlanContext plan = ctx.waypointPlan();
        if (plan == null || plan.departureAttr() == null || plan.departureAttr().TIME() == null) {
            // TIME() is null only after parser error recovery; the diagnostic is already reported.
            return Optional.empty();
        }
        return Optional.of(parseTime(plan.departureAttr().TIME().getText()));
    }

    /** Parses a grammar-validated {@code H:MM} / {@code HH:MM} literal into a time of day. */
    private static LocalTime parseTime(String text) {
        String[] parts = text.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private List<WaypointCommand> resolveCommands(ScriptLogicParser.WaypointContext ctx) {
        List<WaypointCommand> all = new ArrayList<>();
        if (ctx.waypointPlan() == null) {
            return all;
        }
        for (var act : ctx.waypointPlan().action()) {
            all.addAll(toCommands(act));
        }
        return all;
    }

    @Override
    public Object visitAssignItinerary(ScriptLogicParser.AssignItineraryContext ctx) {
        String itName = stripQuotes(ctx.STRING().getText());
        Itinerary it = itineraries.get(itName);
        if (it == null) {
            warnUser("Itinerary", "Itinerary '" + itName + "' not found");
            return null;
        }
        Train train = resolveTrain(ctx.trainRef());
        if (train == null) {
            log.warn("[DSL] Train not found for '{}'", ctx.trainRef().getText());
            return null;
        }
        // Autopilot is always instantiated. Set pathfinder and assign itinerary
        if (model.getRailwayGraph() != null) {
            train.getAutopilot().setPathfinder(new letrain.itinerary.AStarPathfinder(
                    model.getRailwayGraph(), model.getBlockManager(), train));
        }
        train.getAutopilot().setItinerary(it);
        // Re-activate if autopilot was on (itinerary change resets to IDLE)
        if (train.isAutoMode()) {
            train.getAutopilot().activate();
        }
        log.info("[DSL] Itinerary '{}' assigned to Train {}", itName, train.getId());
        return null;
    }

    @Override
    public Object visitSetAutopilot(ScriptLogicParser.SetAutopilotContext ctx) {
        Train train = resolveTrain(ctx.trainRef());
        if (train != null) {
            boolean on = "true".equals(ctx.bool().getText());
            if (on != train.isAutoMode()) {
                train.toggleAutoMode();
            }
            if (on && !train.isAutoMode()) {
                // The most common cause of "the train does not go": no assigned/valid itinerary.
                warnUser("Itinerary", "Train " + train.getId()
                        + " has no itinerary assigned (or it is invalid); the autopilot stays off");
            }
            log.info("[DSL] Train {} autopilot = {}", train.getId(), on);
        }
        return null;
    }

    @Override
    public Object visitDirectTrainCommand(ScriptLogicParser.DirectTrainCommandContext ctx) {
        Train train = resolveTrain(ctx.trainRef());
        if (train != null) {
            ExecutableCommand action = buildTrainAction(ctx.trainAction());
            action.execute(train);
            log.info("[DSL] Direct command executed on Train {}", train.getId());
        } else {
            log.warn("[DSL] Direct command failed: Train not found for '{}'",
                    ctx.trainRef().getText());
        }
        return null;
    }

    @Override
    public Object visitSetNameCommand(ScriptLogicParser.SetNameCommandContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        int id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.getChild(0).getText().equals("station")) {
            Station s = model.getStation(id);
            if (s != null) {
                s.setName(name);
                log.info("[DSL] Station {} named '{}'", id, name);
            }
        } else if (ctx.getChild(0).getText().equals("sensor")) {
            Sensor s = model.getSensor(id);
            if (s != null) {
                s.setName(name);
                log.info("[DSL] Sensor {} named '{}'", id, name);
            }
        } else if (ctx.getChild(0).getText().equals("train")) {
            Train t = model.getTrainFromLocomotiveId(id);
            if (t != null) {
                t.setName(name);
                log.info("[DSL] Train {} named '{}'", id, name);
            }
        }
        return null;
    }



    @Override
    public Object visitDirectForkCommand(ScriptLogicParser.DirectForkCommandContext ctx) {
        int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
        letrain.track.rail.ForkRailTrack fork = model.getFork(id);
        if (fork != null) {
            if (ctx.forkAction().FLIP() != null) {
                fork.flipRoute();
                log.info("[DSL] Direct fork toggle {}", id);
                return null;
            }

            String dir = ctx.forkAction().forkDirection().getText().toLowerCase();
            if ("straight".equals(dir)) {
                fork.setNormalRoute();
            } else if ("curved".equals(dir)) {
                fork.setAlternativeRoute();
            } else if ("left".equals(dir)) {
                if (fork.getCreationDir() == letrain.map.Dir.N) {
                    // example logic for left/right mapping
                    fork.setAlternativeRoute(); // Just an example, assuming alternate is curve
                } else {
                    fork.setAlternativeRoute();
                }
            } else if ("right".equals(dir)) {
                fork.setAlternativeRoute();
            } else {
                letrain.map.Dir direction = letrain.map.Dir.valueOf(dir.toUpperCase());
                if (fork.getOriginalRoute().getValue() == direction) {
                    fork.setNormalRoute();
                } else if (fork.getAlternativeRoute().getValue() == direction) {
                    fork.setAlternativeRoute();
                }
            }
            log.info("[DSL] Direct fork {} set towards {}", id, dir);
        }
        return null;
    }


    @Override
    public Object visitDirectSemaphoreCommand(ScriptLogicParser.DirectSemaphoreCommandContext ctx) {
        int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
        letrain.track.RailSemaphore sem = model.getSemaphore(id);
        if (sem != null) {
            ScriptLogicParser.SemaphoreActionContext act = ctx.semaphoreAction();
            if (act.INVERT() != null) {
                sem.setCreationDir(sem.getCreationDir().inverse());
                log.info("[DSL] Direct semaphore {} inverted", id);
            } else if (act.OPEN() != null || (act.semaphoreStatus() != null
                    && "open".equalsIgnoreCase(act.semaphoreStatus().getText()))) {
                sem.setOpen(true);
                log.info("[DSL] Direct semaphore {} set to open", id);
            } else if (act.CLOSE() != null || act.CLOSED() != null || (act.semaphoreStatus() != null
                    && ("close".equalsIgnoreCase(act.semaphoreStatus().getText())
                            || "closed".equalsIgnoreCase(act.semaphoreStatus().getText())))) {
                sem.setOpen(false);
                log.info("[DSL] Direct semaphore {} set to closed", id);
            }
        }
        return null;
    }

    @Override
    public Object visitDirectSignalCommand(ScriptLogicParser.DirectSignalCommandContext ctx) {
        int id = Integer.parseInt(ctx.signalSelector().NUMBER().getText());
        // Plain sensors and speed signals have separate id counters, so getSensor(id) can return a
        // plain sensor sharing the numeric id. Resolve the signal among the speed signals only;
        // otherwise the command is silently dropped (e.g. an imported scenario kept limit 3).
        letrain.track.SpeedSignal signal = null;
        for (letrain.track.SpeedSignal s : model.getSpeedSignals()) {
            if (s.getId() == id) {
                signal = s;
                break;
            }
        }
        if (signal != null) {
            ScriptLogicParser.SignalActionContext act = ctx.signalAction();
            if (act.INVERT() != null) {
                signal.setCreationDir(signal.getCreationDir().inverse());
                log.info("[DSL] Direct signal {} inverted", id);
            } else if (act.LIMIT() != null && act.NUMBER() != null) {
                signal.setLimit(Integer.parseInt(act.NUMBER().getText()));
                log.info("[DSL] Direct signal {} limit set to {}", id, signal.getLimit());
            } else if (act.MODE() != null) {
                boolean isMax = act.MAX() != null;
                signal.setMax(isMax);
                log.info("[DSL] Direct signal {} mode set to {}", id, isMax ? "MAX" : "MIN");
            }
        }
        return null;
    }


    @Override
    public Object visitDirectStationCommand(ScriptLogicParser.DirectStationCommandContext ctx) {
        int id = Integer.parseInt(ctx.NUMBER().getText());
        letrain.track.Station station = model.getStation(id);
        if (station != null) {
            station.flipOrientation();
            log.info("[DSL] Direct station {} inverted", id);
        } else {
            log.warn("[DSL] Direct station invert: station {} not found", id);
        }
        return null;
    }

    @Override
    public Object visitDirectSensorCommand(ScriptLogicParser.DirectSensorCommandContext ctx) {
        int id = Integer.parseInt(ctx.NUMBER().getText());
        letrain.track.Sensor sensor = null;
        for (letrain.track.Sensor s : model.getSensors()) {
            if (s.getId() == id && s.getClass() == letrain.track.Sensor.class) {
                sensor = s;
                break;
            }
        }
        if (sensor != null) {
            sensor.setCreationDir(sensor.getCreationDir().inverse());
            log.info("[DSL] Direct sensor {} inverted", id);
        } else {
            log.warn("[DSL] Direct sensor invert: plain sensor {} not found", id);
        }
        return null;
    }

    private Station resolveStation(ScriptLogicParser.StationRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findStationByName(stripQuotes(ctx.STRING().getText()));
        return model.getStation(Integer.parseInt(ctx.NUMBER().getText()));
    }

    private Sensor resolveSensor(ScriptLogicParser.SensorRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findSensorByName(stripQuotes(ctx.STRING().getText()));
        return model.getSensor(Integer.parseInt(ctx.NUMBER().getText()));
    }

    private Train resolveTrain(ScriptLogicParser.TrainRefContext ctx) {
        if (ctx.STRING() != null) {
            return model.findTrainByName(stripQuotes(ctx.STRING().getText()));
        }
        int id = Integer.parseInt(ctx.NUMBER().getText());
        return model.getTrainFromLocomotiveId(id);
    }

    private List<WaypointCommand> toCommands(ScriptLogicParser.ActionContext ctx) {
        // ADR-022 phase 2f: train orders and fork actions are waypoint actions too.
        if (ctx.coupleAction() != null) {
            ScriptLogicParser.CoupleActionContext couple = ctx.coupleAction();
            boolean forward = couple.sense().getText().startsWith("f");
            int count = resolveVehicleCount(couple.vehicleCount(), 0);
            return List.of(WaypointCommand.couple(forward, count));
        }
        if (ctx.uncoupleAction() != null) {
            ScriptLogicParser.UncoupleActionContext uncouple = ctx.uncoupleAction();
            boolean forward = uncouple.sense().getText().startsWith("f");
            int count = resolveVehicleCount(uncouple.vehicleCount(), 1);
            return List.of(WaypointCommand.uncouple(forward, count));
        }
        if (ctx.stopOrder() != null) {
            MissionSpec spec = resolveMissionSpec(ctx.stopOrder());
            if (spec == null) {
                return List.of();
            }
            return List.of(WaypointCommand.mission(spec.kind(), spec.targetId(), spec.speed()));
        }
        if (ctx.forkSelector() != null) {
            int forkId = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            String direction = ctx.forkAction().forkDirection() != null
                    ? ctx.forkAction().forkDirection().getText().toLowerCase()
                    : "flip";
            if ("flip".equals(direction)) {
                return List.of(WaypointCommand.forkFlip(forkId));
            }
            return List.of(WaypointCommand.forkSetDirection(forkId, direction));
        }
        String text = ctx.getText().toLowerCase();
        return switch (text) {
            case "load" -> List.of(WaypointCommand.LOAD);
            case "unload" -> List.of(WaypointCommand.UNLOAD);
            case "reverse" -> List.of(WaypointCommand.REVERSE);
            case "stop" -> List.of(WaypointCommand.STOP);
            case "park" -> List.of(WaypointCommand.PARK);
            default -> {
                if (text.startsWith("wait")) {
                    int seconds = Integer.parseInt(ctx.NUMBER().getText());
                    yield List.of(WaypointCommand.waitSeconds(seconds));
                } else if (text.startsWith("speed")) {
                    int speed = Integer.parseInt(ctx.NUMBER().getText());
                    yield List.of(WaypointCommand.speed(speed));
                }
                yield List.of();
            }
        };
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            return s.substring(1, s.length() - 1);
        return s;
    }

    private Train findTrainAtPlace(ScriptLogicParser.PlaceSelectorContext ctx) {
        if (ctx.stationSelector() != null) {
            int id = Integer.parseInt(ctx.stationSelector().NUMBER().getText());
            letrain.track.Station s = model.getStation(id);
            if (s != null) {
                for (Locomotive l : model.getLocomotives()) {
                    if (l.getTrack() != null && l.getTrack().getComponent() == s) {
                        return l.getTrain();
                    }
                }
            }
        } else if (ctx.sensorSelector() != null) {
            int id = Integer.parseInt(ctx.sensorSelector().NUMBER().getText());
            Sensor s = model.getSensor(id);
            if (s != null) {
                for (Locomotive l : model.getLocomotives()) {
                    if (l.getTrack() != null && l.getTrack().getComponent() == s) {
                        return l.getTrain();
                    }
                }
            }
        }
        return null;
    }
}
