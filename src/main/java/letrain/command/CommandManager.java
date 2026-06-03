package letrain.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import letrain.itinerary.Itinerary;
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

public class CommandManager extends LeTrainProgramBaseVisitor<Object> {
    static Logger log = LoggerFactory.getLogger(CommandManager.class);
    Model model;

    /** Stores itineraries created during parsing, keyed by name. */
    private final Map<String, Itinerary> itineraries = new HashMap<>();
    /** Current itinerary being constructed. */
    private ItineraryImpl currentItinerary;

    public CommandManager(Model model) {
        this.model = model;
    }

    interface ExecutableCommand {
        void execute(Train contextTrain);
    }

    // ── Statement dispatch ─────────────────────────────────────────

    @Override
    public Object visitStart(LeTrainProgramParser.StartContext ctx) {
        itineraries.clear();
        currentItinerary = null;
        return super.visitStart(ctx);
    }

    @Override
    public Object visitStatement(LeTrainProgramParser.StatementContext ctx) {
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
    public Object visitDirectCommand(LeTrainProgramParser.DirectCommandContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitCommandBlock(LeTrainProgramParser.CommandBlockContext ctx) {
        List<ExecutableCommand> commands = new ArrayList<>();
        for (LeTrainProgramParser.CommandItemContext itemCtx : ctx.commandItem()) {
            commands.add((ExecutableCommand) visit(itemCtx));
        }
        return commands;
    }

    private void setupTrigger(LeTrainProgramParser.TriggerContext ctx, List<ExecutableCommand> commands) {
        if (ctx.sensorSelector() != null) {
            int id = Integer.parseInt(ctx.sensorSelector().NUMBER().getText());
            Sensor sensor = model.getSensor(id);
            if (sensor != null) {
                Integer filterTrainId = (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                        ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                        : null;
                String event = ctx.trainEvent().getChild(0).getText();
                String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText() : null;
                sensor.addSensorEventListener(new SensorEventListener() {
                    @Override
                    public void onEnterTrain(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                || (sense.equals("backward") && !isForward);
                        if ("enter".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onExitTrain(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                || (sense.equals("backward") && !isForward);
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
                    Integer filterTrainId = (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                            ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                            : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText() : null;
                    station.addStationEventListener(new StationEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
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
                    Integer filterTrainId = (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                            ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                            : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText() : null;
                    fork.addForkEventListener(new ForkEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
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
                    Integer filterTrainId = (ctx.trainSelector() != null && ctx.trainSelector().NUMBER() != null)
                            ? Integer.parseInt(ctx.trainSelector().NUMBER().getText())
                            : null;
                    String event = ctx.trainEvent().getChild(0).getText();
                    String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText() : null;
                    semaphore.addSemaphoreEventListener(new letrain.track.SemaphoreEventListener() {
                        @Override
                        public void onEnterTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
                            if ("enter".equals(event) && senseMatch
                                    && (filterTrainId == null || filterTrainId == train.getId())) {
                                commands.forEach(c -> c.execute(train));
                            }
                        }

                        @Override
                        public void onExitTrain(Train train, boolean isForward) {
                            boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                    || (sense.equals("backward") && !isForward);
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
                String sense = ctx.trainEvent().sense() != null ? ctx.trainEvent().sense().getText() : null;
                model.addScriptTrainEventListener(new ScriptTrainEventListener() {
                    @Override
                    public void onSensorEnter(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                || (sense.equals("backward") && !isForward);
                        if ("enter".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onSensorExit(Train train, boolean isForward) {
                        boolean senseMatch = (sense == null) || (sense.equals("forward") && isForward)
                                || (sense.equals("backward") && !isForward);
                        if ("exit".equals(event) && senseMatch
                                && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onLink(Train train) {
                        if ("link".equals(event) && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onUnlink(Train train) {
                        if ("unlink".equals(event) && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }
                });
            } else if (ctx.getChildCount() >= 3) {
                String event = ctx.getChild(2).getText();
                model.addScriptTrainEventListener(new ScriptTrainEventListener() {
                    @Override
                    public void onCrash(Train train, letrain.map.Point pos, int speed) {
                        if ("crash".equals(event) && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }

                    @Override
                    public void onContact(Train train, letrain.map.Point pos, int speed) {
                        if ("contact".equals(event) && (filterTrainId == null || filterTrainId == train.getId())) {
                            commands.forEach(c -> c.execute(train));
                        }
                    }
                });
            }
        }
    }

    @Override
    public Object visitCommandItem(LeTrainProgramParser.CommandItemContext ctx) {
        if (ctx.semaphoreAction() != null) {
            int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
            String status = ctx.semaphoreAction().semaphoreStatus().getText();
            return (ExecutableCommand) (contextTrain) -> {
                RailSemaphore s = model.getSemaphore(id);
                if (s != null)
                    s.setOpen("open".equals(status));
            };
        } else if (ctx.forkAction() != null) {
            int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            String dirText = ctx.forkAction().forkDirection() != null ? ctx.forkAction().forkDirection().getText()
                    : null;
            return (ExecutableCommand) (contextTrain) -> {
                ForkRailTrack f = model.getFork(id);
                if (f != null) {
                    if ("straight".equals(dirText)) {
                        if (f.getOriginalRoute() != null
                                && f.getOriginalRoute().getFirst().isStraight(f.getOriginalRoute().getSecond())) {
                            f.setNormalRoute();
                        } else if (f.getAlternativeRoute() != null
                                && f.getAlternativeRoute().getFirst().isStraight(f.getAlternativeRoute().getSecond())) {
                            f.setAlternativeRoute();
                        } else {
                            f.setNormalRoute();
                        }
                    } else if ("curved".equals(dirText)) {
                        boolean originalIsStraight = f.getOriginalRoute() != null
                                && f.getOriginalRoute().getFirst().isStraight(f.getOriginalRoute().getSecond());
                        boolean alternativeIsStraight = f.getAlternativeRoute() != null
                                && f.getAlternativeRoute().getFirst().isStraight(f.getAlternativeRoute().getSecond());

                        if (f.getOriginalRoute() != null && !originalIsStraight) {
                            f.setNormalRoute();
                        } else if (f.getAlternativeRoute() != null && !alternativeIsStraight) {
                            f.setAlternativeRoute();
                        } else {
                            f.setAlternativeRoute();
                        }
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
                        if (target != null)
                            baseAction.execute(target);
                    };
                } else {
                    return (ExecutableCommand) (contextTrain) -> {
                        if (contextTrain != null)
                            baseAction.execute(contextTrain);
                    };
                }
            } else if (ctx.trainExtractor() != null) {
                LeTrainProgramParser.PlaceSelectorContext pCtx = ctx.trainExtractor().placeSelector();
                return (ExecutableCommand) (contextTrain) -> {
                    Train target = findTrainAtPlace(pCtx);
                    if (target != null)
                        baseAction.execute(target);
                };
            }
        }
        return (ExecutableCommand) (ct) -> {
        };
    }

    private ExecutableCommand buildTrainAction(LeTrainProgramParser.TrainActionContext ctx) {
        String actionText = ctx.getText();
        if (ctx.trainSpeed() != null) {
            int speed = Integer.parseInt(ctx.trainSpeed().getText());
            int clampedSpeed = Math.max(0, Math.min(10, speed));
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) tractor.setSpeed(clampedSpeed);
            };
        } else if (actionText.contains("accelerate")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) tractor.incSpeed();
            };
        } else if (actionText.contains("decelerate")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) tractor.decSpeed();
            };
        } else if (ctx.trainSense() != null) {
            boolean forward = "forward".equals(ctx.trainSense().getText());
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null && tractor.isReversed() == forward)
                    tractor.toggleReversed();
            };
        } else if (actionText.contains("stop")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) tractor.setSpeed(0);
            };
        } else if (actionText.contains("invert")) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) tractor.toggleReversed();
            };
        } else if (ctx.linkAction() != null) {
            LeTrainProgramParser.LinkActionContext lCtx = ctx.linkAction();
            boolean forward = "forward".equals(lCtx.sense().getText());
            int count = lCtx.NUMBER() != null ? Integer.parseInt(lCtx.NUMBER().getText()) : 0;
            return (t) -> {
                t.trainCouplingManager.prepareLink(t, forward, count);
                t.trainCouplingManager.joinLinkers(t);
            };
        } else if (ctx.unlinkAction() != null) {
            LeTrainProgramParser.UnlinkActionContext uCtx = ctx.unlinkAction();
            boolean forward = "forward".equals(uCtx.sense().getText());
            int count = uCtx.NUMBER() != null ? Integer.parseInt(uCtx.NUMBER().getText()) : 1;
            return (t) -> {
                t.trainCouplingManager.prepareUnlink(t, forward, count);
                t.trainCouplingManager.divideTrain(t, () -> model.nextTrainId());
            };
        } else if (actionText.contains("unload")) {
            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null)
                    t.getLogisticsManager().startUnloadProcess(s);
            };
        } else if (actionText.contains("load")) {
            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null)
                    t.getLogisticsManager().startLoadProcess(s);
            };
        } else {
            return (t) -> {
            };
        }
    }

    // ── Direct command visitors ──────────────────────────────────────

    @Override
    public Object visitCreateItinerary(LeTrainProgramParser.CreateItineraryContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        currentItinerary = new ItineraryImpl();
        for (LeTrainProgramParser.WaypointContext wp : ctx.waypoint()) {
            visit(wp);
        }
        if (currentItinerary.isValid()) {
            itineraries.put(name, currentItinerary);
            log.info("[DSL] Created itinerary '{}' with {} waypoints", name, currentItinerary.waypoints().size());
        } else {
            log.warn("[DSL] Itinerary '{}' is invalid (<2 waypoints)", name);
        }
        currentItinerary = null;
        return null;
    }

    @Override
    public Object visitWaypoint(LeTrainProgramParser.WaypointContext ctx) {
        if (currentItinerary == null) return null;

        Waypoint wp;
        if (ctx.stationRef() != null) {
            Station st = resolveStation(ctx.stationRef());
            if (st == null) return null;
            wp = new WaypointImpl(Waypoint.Type.STATION, st.getId(), resolveDir(ctx), resolveCommands(ctx));
        } else if (ctx.sensorRef() != null) {
            Sensor se = resolveSensor(ctx.sensorRef());
            if (se == null) return null;
            wp = new WaypointImpl(Waypoint.Type.SENSOR, se.getId(), resolveDir(ctx), resolveCommands(ctx));
        } else {
            return null;
        }
        currentItinerary.addWaypoint(wp);
        return null;
    }

    private Dir resolveDir(LeTrainProgramParser.WaypointContext ctx) {
        if (ctx.direction() != null && ctx.direction().dir() != null) {
            return Dir.valueOf(ctx.direction().dir().getText());
        }
        return null; // default
    }

    private List<WaypointCommand> resolveCommands(LeTrainProgramParser.WaypointContext ctx) {
        List<WaypointCommand> all = new ArrayList<>();
        for (var act : ctx.action()) {
            all.addAll(toCommands(act));
        }
        return all;
    }

    @Override
    public Object visitAssignItinerary(LeTrainProgramParser.AssignItineraryContext ctx) {
        String itName = stripQuotes(ctx.STRING().getText());
        Itinerary it = itineraries.get(itName);
        if (it == null) {
            log.warn("[DSL] Itinerary '{}' not found", itName);
            return null;
        }
        Train train = resolveTrain(ctx.trainRef());
        if (train == null) {
            log.warn("[DSL] Train not found for '{}'", ctx.trainRef().getText());
            return null;
        }
        // Autopilot is always instantiated. Set pathfinder and assign itinerary
        if (model.getRailwayGraph() != null) {
            train.getAutopilot().setPathfinder(
                new letrain.itinerary.AStarPathfinder(model.getRailwayGraph()));
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
    public Object visitSetAutopilot(LeTrainProgramParser.SetAutopilotContext ctx) {
        Train train = resolveTrain(ctx.trainRef());
        if (train != null) {
            boolean on = "true".equals(ctx.bool().getText());
            if (on != train.isAutoMode()) train.toggleAutoMode();
            log.info("[DSL] Train {} autopilot = {}", train.getId(), on);
        }
        return null;
    }

    @Override
    public Object visitDirectTrainCommand(LeTrainProgramParser.DirectTrainCommandContext ctx) {
        Train train = resolveTrain(ctx.trainRef());
        if (train != null) {
            ExecutableCommand action = buildTrainAction(ctx.trainAction());
            action.execute(train);
            log.info("[DSL] Direct command executed on Train {}", train.getId());
        } else {
            log.warn("[DSL] Direct command failed: Train not found for '{}'", ctx.trainRef().getText());
        }
        return null;
    }

    @Override
    public Object visitSetNameCommand(LeTrainProgramParser.SetNameCommandContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        int id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.getChild(0).getText().equals("station")) {
            Station s = model.getStation(id);
            if (s != null) { s.setName(name); log.info("[DSL] Station {} named '{}'", id, name); }
        } else if (ctx.getChild(0).getText().equals("sensor")) {
            Sensor s = model.getSensor(id);
            if (s != null) { s.setName(name); log.info("[DSL] Sensor {} named '{}'", id, name); }
        } else if (ctx.getChild(0).getText().equals("train")) {
            Train t = model.getTrainFromLocomotiveId(id);
            if (t != null) { t.setName(name); log.info("[DSL] Train {} named '{}'", id, name); }
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Station resolveStation(LeTrainProgramParser.StationRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findStationByName(stripQuotes(ctx.STRING().getText()));
        return model.getStation(Integer.parseInt(ctx.NUMBER().getText()));
    }

    private Sensor resolveSensor(LeTrainProgramParser.SensorRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findSensorByName(stripQuotes(ctx.STRING().getText()));
        return model.getSensor(Integer.parseInt(ctx.NUMBER().getText()));
    }

    private Train resolveTrain(LeTrainProgramParser.TrainRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findTrainByName(stripQuotes(ctx.STRING().getText()));
        int id = Integer.parseInt(ctx.NUMBER().getText());
        return model.getTrainFromLocomotiveId(id);
    }

    private List<WaypointCommand> toCommands(LeTrainProgramParser.ActionContext ctx) {
        String text = ctx.getText().toUpperCase();
        return switch (text) {
            case "LOAD"     -> List.of(WaypointCommand.LOAD);
            case "UNLOAD"   -> List.of(WaypointCommand.UNLOAD);
            case "REVERSE"  -> List.of(WaypointCommand.REVERSE);
            case "STOP"     -> List.of(WaypointCommand.speed(0));
            default -> {
                if (text.startsWith("WAIT")) {
                    int seconds = Integer.parseInt(ctx.NUMBER().getText());
                    yield List.of(WaypointCommand.waitSeconds(seconds));
                } else if (text.startsWith("SPEED")) {
                    int speed = Integer.parseInt(ctx.NUMBER().getText());
                    yield List.of(WaypointCommand.speed(speed));
                }
                yield List.of();
            }
        };
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            return s.substring(1, s.length() - 1);
        return s;
    }

    private Train findTrainAtPlace(LeTrainProgramParser.PlaceSelectorContext ctx) {
        if (ctx.stationSelector() != null) {
            int id = Integer.parseInt(ctx.stationSelector().NUMBER().getText());
            letrain.track.Station s = model.getStation(id);
            if (s != null) {
                for (Locomotive l : model.getLocomotives()) {
                    if (l.getTrack() != null && l.getTrack().getSensor() == s)
                        return l.getTrain();
                }
            }
        } else if (ctx.sensorSelector() != null) {
            int id = Integer.parseInt(ctx.sensorSelector().NUMBER().getText());
            Sensor s = model.getSensor(id);
            if (s != null) {
                for (Locomotive l : model.getLocomotives()) {
                    if (l.getTrack() != null && l.getTrack().getSensor() == s)
                        return l.getTrain();
                }
            }
        }
        return null;
    }
}