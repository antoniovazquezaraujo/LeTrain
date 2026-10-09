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
import letrain.time.TemporalTrigger;
import letrain.time.TemporalTriggerService;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.TrainCouplingManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandManager extends ScriptLogicParserBaseVisitor<Object> {
    static Logger log = LoggerFactory.getLogger(CommandManager.class);
    Model model;

    /**
     * Stores itineraries created during parsing, keyed by name. A full script parse starts with a
     * fresh registry ({@link #visitScriptStart}); the console keeps one manager per world (#632),
     * so the definitions survive from one typed statement to the next.
     */
    private final Map<String, Itinerary> itineraries = new HashMap<>();

    /** Current itinerary being constructed. */
    private ItineraryImpl currentItinerary;

    /**
     * Problems found while building the itinerary being parsed (unknown waypoint destination,
     * unknown mission target…). A non-empty list rejects the itinerary (D1: a plan must not run
     * with a stop missing).
     */
    private final List<String> itineraryProblems = new ArrayList<>();

    /**
     * Sink for user-facing problem notices (rejection, unreachable, lost route, stall). The console
     * sets it (typed orders warn on screen); scripts and the program replay leave it null, so their
     * messages go to the log only. Mission success never uses it (issue #619).
     */
    private java.util.function.BiConsumer<String, String> warningSink;

    /**
     * Sink for notices produced by deferred command blocks (a trigger firing) and by the autopilot
     * mission notifier. Those are asynchronous events: the console routes them to the panel
     * ({@code Model.reportUserMessage}); programs do the same. Without one, the block warnings stay
     * in the log and a caller-provided mission notifier is left untouched (headless contract).
     */
    private java.util.function.BiConsumer<String, String> deferredWarningSink;

    public CommandManager(Model model) {
        this.model = model;
    }

    public void setWarningSink(java.util.function.BiConsumer<String, String> sink) {
        this.warningSink = sink;
    }

    public void setDeferredWarningSink(java.util.function.BiConsumer<String, String> sink) {
        this.deferredWarningSink = sink;
    }

    interface ExecutableCommand {
        void execute(Train contextTrain);
    }

    // ── Statement dispatch ─────────────────────────────────────────

    @Override
    public Object visitScriptStart(ScriptLogicParser.ScriptStartContext ctx) {
        itineraries.clear();
        currentItinerary = null;
        itineraryProblems.clear();
        return super.visitScriptStart(ctx);
    }

    @Override
    public Object visitStatement(ScriptLogicParser.StatementContext ctx) {
        if (ctx.trigger() != null) {
            // Existing: event-driven
            List<ExecutableCommand> commands = (List<ExecutableCommand>) visit(ctx.commandBlock());
            setupTrigger(ctx.trigger(), commands);
        } else if (ctx.temporalTrigger() != null) {
            // ADR-022 phase 3 (F3a): time-driven. The block is visited (and validated) now; F3b
            // will run its commands when the timer fires.
            visit(ctx.commandBlock());
            setupTemporalTrigger(ctx.temporalTrigger());
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
            Sensor sensor = resolveTriggerSensor(ctx.sensorSelector());
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
            } else {
                warnUser("Trigger", "Sensor " + selectorRef(ctx.sensorSelector())
                        + " not found; trigger ignored");
            }
        } else if (ctx.stationSelector() != null) {
            letrain.track.Station station = resolveTriggerStation(ctx.stationSelector());
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
            } else {
                warnUser("Trigger", "Station " + selectorRef(ctx.stationSelector())
                        + " not found; trigger ignored");
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
            } else {
                warnUser("Trigger", "Fork " + id + " not found; trigger ignored");
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
            } else {
                warnUser("Trigger", "Semaphore " + id + " not found; trigger ignored");
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

    /**
     * ADR-022 phase 3 (contract D1-D7): registers an {@code at}/{@code every} trigger in the world
     * registry, which arms its next fire on the scheduler. An invalid hour/period, an exact
     * duplicate and the active limit are visible warnings; the trigger is then ignored. The block
     * actions are parsed and validated now but not executed yet (F3b).
     */
    private void setupTemporalTrigger(ScriptLogicParser.TemporalTriggerContext ctx) {
        TemporalTrigger trigger = buildTemporalTrigger(ctx);
        if (trigger == null) {
            return; // invalid: the specific warning was already reported
        }
        TemporalTriggerService service = model.getTemporalTriggerService();
        TemporalTriggerService.Registration result = service.register(trigger);
        switch (result) {
            case DUPLICATE -> warnUser("Trigger",
                    "Duplicate temporal trigger '" + trigger.describe() + "'; ignored");
            case LIMIT_REACHED -> warnUser("Trigger",
                    "Too many temporal triggers (limit " + TemporalTriggerService.MAX_TRIGGERS
                            + "); '" + trigger.describe() + "' ignored");
            case REGISTERED -> log.info("[DSL] temporal trigger {} registered", trigger.describe());
        }
    }

    private TemporalTrigger buildTemporalTrigger(ScriptLogicParser.TemporalTriggerContext ctx) {
        if (ctx.atTrigger() != null) {
            LocalTime at = parseTriggerTime(ctx.atTrigger().triggerTime(), "at");
            return at == null ? null : TemporalTrigger.at(at);
        }
        ScriptLogicParser.EveryTriggerContext every = ctx.everyTrigger();
        Integer period = parsePeriod(every.period());
        if (period == null) {
            return null;
        }
        LocalTime from = null;
        if (every.triggerTime() != null) {
            from = parseTriggerTime(every.triggerTime(), "from");
            if (from == null) {
                return null;
            }
        }
        return TemporalTrigger.every(period, from);
    }

    /**
     * U8-style parsing for trigger times: {@code at 9} is 09:00 and {@code at 9:20} the full form;
     * TIME is already limited to 00:00..23:59, so only a bare number out of 0..23 is rejected, with
     * a visible warning.
     */
    private LocalTime parseTriggerTime(ScriptLogicParser.TriggerTimeContext ctx, String keyword) {
        String text = ctx.getText();
        String[] parts = text.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            warnUser("Trigger", "'" + keyword + " " + text
                    + "' is out of range (expected 0..23 hours); trigger ignored");
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    /**
     * ADR-022 phase 3 (D3): period units {@code m} (game minutes), {@code h} and {@code d}. The
     * period may arrive glued into one token ({@code 30m} is an ID) or spaced ({@code 30 m}), so
     * the text is split here. {@code 0m}, negative amounts, unknown units and oversized periods are
     * rejected with a visible warning; the trigger is then ignored.
     */
    private Integer parsePeriod(ScriptLogicParser.PeriodContext ctx) {
        String text = ctx.getText();
        int split = 0;
        if (split < text.length() && (text.charAt(split) == '-' || text.charAt(split) == '+')) {
            split++;
        }
        int digitsStart = split;
        while (split < text.length() && Character.isDigit(text.charAt(split))) {
            split++;
        }
        if (split == digitsStart) {
            warnUser("Trigger",
                    "Invalid period '" + text + "'; expected <amount>m|h|d; trigger ignored");
            return null;
        }
        long amount;
        try {
            amount = Long.parseLong(text.substring(0, split));
        } catch (NumberFormatException e) {
            warnUser("Trigger", "Period '" + text + "' is too large; trigger ignored");
            return null;
        }
        if (amount <= 0) {
            warnUser("Trigger", "Invalid period '" + text
                    + "': the amount must be greater than 0; trigger ignored");
            return null;
        }
        String unit = text.substring(split);
        int factor;
        switch (unit) {
            case "m" -> factor = 1;
            case "h" -> factor = 60;
            case "d" -> factor = 1440;
            default -> {
                warnUser("Trigger", "Unknown period unit '" + unit + "' in '" + text
                        + "' (expected m, h or d); trigger ignored");
                return null;
            }
        }
        try {
            return Math.multiplyExact(Math.toIntExact(amount), factor);
        } catch (ArithmeticException e) {
            warnUser("Trigger", "Period '" + text + "' is too large; trigger ignored");
            return null;
        }
    }

    @Override
    public Object visitCommandItem(ScriptLogicParser.CommandItemContext ctx) {
        if (ctx.semaphoreAction() != null) {
            int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
            ScriptLogicParser.SemaphoreActionContext act = ctx.semaphoreAction();
            if (act.INVERT() != null) {
                return (ExecutableCommand) (contextTrain) -> {
                    RailSemaphore s = model.getSemaphore(id);
                    if (s != null) {
                        s.setCreationDir(s.getCreationDir().inverse());
                    } else {
                        warnDeferred("Semaphore", "Semaphore " + id + " not found; action ignored");
                    }
                };
            }
            // Issue #736: `toggle` flips the light state and leaves the direction alone (that is
            // what `invert` does).
            if (act.TOGGLE() != null) {
                return (ExecutableCommand) (contextTrain) -> {
                    RailSemaphore s = model.getSemaphore(id);
                    if (s != null) {
                        s.setOpen(!s.isOpen());
                    } else {
                        warnDeferred("Semaphore", "Semaphore " + id + " not found; action ignored");
                    }
                };
            }
            // `semaphoreStatus()` is null for the bare OPEN/CLOSE/CLOSED forms: reading it blindly
            // used to NPE here (review finding 4).
            boolean open;
            if (act.OPEN() != null) {
                open = true;
            } else if (act.CLOSE() != null || act.CLOSED() != null) {
                open = false;
            } else {
                open = act.semaphoreStatus() != null
                        && "open".equals(act.semaphoreStatus().getText());
            }
            return (ExecutableCommand) (contextTrain) -> {
                RailSemaphore s = model.getSemaphore(id);
                if (s != null) {
                    s.setOpen(open);
                } else {
                    warnDeferred("Semaphore", "Semaphore " + id + " not found; action ignored");
                }
            };
        } else if (ctx.forkAction() != null) {
            int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            String dirText = ctx.forkAction().forkDirection() != null
                    ? ctx.forkAction().forkDirection().getText().toLowerCase(java.util.Locale.ROOT)
                    : "flip";
            return (ExecutableCommand) (contextTrain) -> {
                ForkRailTrack f = model.getFork(id);
                if (f == null) {
                    warnDeferred("Fork", "Fork " + id + " not found; action ignored");
                } else if (!applyForkDirection(f, dirText)) {
                    // D3: the same mapping as the console; the old implicit flip on an unknown
                    // direction is gone, and it is never a silent no-op.
                    warnDeferred("Fork",
                            "Fork " + id + " has no route towards " + dirText + "; unchanged");
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
                        } else {
                            warnDeferred("Train", "Train " + id + " not found; action ignored");
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
                    PlaceTrain found = findTrainAtPlace(pCtx);
                    if (found.train() != null) {
                        baseAction.execute(found.train());
                    } else if (found.placeKnown()) {
                        // `getText()` glues the tokens ("station1"): describe the selector by hand
                        // so the notice reads like the order the user wrote (O1). An unknown place
                        // already warned inside findTrainAtPlace, with its own notice.
                        warnDeferred("Trigger",
                                "No train at " + describePlace(pCtx) + "; action ignored");
                    }
                };
            }
        }
        return (ExecutableCommand) (ct) -> {
        };
    }

    private ExecutableCommand buildTrainAction(ScriptLogicParser.TrainActionContext ctx) {
        if (ctx.stopOrder() != null) {
            return buildStopOrder(ctx.stopOrder());
        } else if (ctx.trainSpeed() != null) {
            // U1: only `set speed N`; the old `set N` shortcut is gone.
            int speed = Integer.parseInt(ctx.trainSpeed().getText());
            int clampedSpeed = clampSpeed(speed);
            if (clampedSpeed != speed) {
                warnUser("Speed",
                        "Speed " + speed + " is out of range 0-10; using " + clampedSpeed);
            }
            return (t) -> {
                t.setSpeed(clampedSpeed);
            };
        } else if (ctx.ACCELERATE() != null) {
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) {
                    tractor.incSpeed();
                }
            };
        } else if (ctx.DECELERATE() != null) {
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
        } else if (ctx.INVERT() != null) {
            // U2: `invert` and `reverse` are the same token; both flip the travel sense.
            return (t) -> {
                Tractor tractor = t.getDirectorLinker();
                if (tractor != null) {
                    tractor.toggleReversed();
                }
            };
        } else if (ctx.PARK() != null) {
            // U3: same semantics as the waypoint action — brake, engine off, autopilot kept.
            return (t) -> parkTrain(t);
        } else if (ctx.STOP() != null) {
            // Waypoint STOP semantics as a direct order: brake and leave the autopilot off.
            return (t) -> {
                t.getMovementManager().initiateBraking();
                t.setPendingManualMode(true);
                if (t.getAutopilot() != null) {
                    t.getAutopilot().deactivate();
                }
            };
        } else if (ctx.coupleAction() != null) {
            ScriptLogicParser.CoupleActionContext lCtx = ctx.coupleAction();
            boolean forward = lCtx.sense().getText().startsWith("f");
            Integer count = resolveVehicleCount(lCtx.vehicleCount(), TrainCouplingManager.ALL,
                    couplingText("couple", forward, lCtx.vehicleCount()));
            if (count == null) {
                return (t) -> {
                };
            }
            return (t) -> {
                t.getTrainCouplingManager().prepareLink(t, forward, count);
                t.getTrainCouplingManager().joinLinkers(t);
            };
        } else if (ctx.uncoupleAction() != null) {
            ScriptLogicParser.UncoupleActionContext uCtx = ctx.uncoupleAction();
            boolean forward = uCtx.sense().getText().startsWith("f");
            // U6: no count means every vehicle on that side, exactly like `couple`.
            Integer count = resolveVehicleCount(uCtx.vehicleCount(), TrainCouplingManager.ALL,
                    couplingText("uncouple", forward, uCtx.vehicleCount()));
            if (count == null) {
                return (t) -> {
                };
            }
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
        } else if (ctx.UNLOAD() != null) {

            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null) {
                    t.getLogisticsManager().startUnloadProcess(s);
                }
            };
        } else if (ctx.LOAD() != null) {
            return (t) -> {
                letrain.track.Station s = t.getLogisticsManager().getStationAtTrain();
                if (s != null) {
                    t.getLogisticsManager().startLoadProcess(s);
                }
            };
        } else if (ctx.NAME() != null && ctx.STRING() != null) {
            // `train N set name "X"` (direct order and inside a block) used to be a silent no-op.
            String newName = stripQuotes(ctx.STRING().getText());
            return (t) -> {
                t.setName(newName);
                log.info("[DSL] Train {} named '{}'", t.getId(), newName);
            };
        } else {
            return (t) -> {
            };
        }
    }

    /**
     * `train N park;` (U3): brake, switch the engine off and keep the autopilot. A moving train
     * brakes by inertia; the engine is switched off as soon as it halts (bounded scheduler
     * re-check, mirroring the waypoint action's deferred {@code PARK}).
     */
    private void parkTrain(Train t) {
        Tractor director = t.getDirectorLinker();
        if (director != null && director.getSpeed() > 0) {
            t.getMovementManager().initiateBraking();
            scheduleEngineOffWhenStopped(t, PARK_RECHECKS);
            return;
        }
        turnEnginesOff(t);
    }

    /** Re-checks every few ticks while the parked train brakes; never polls forever. */
    private static final int PARK_RECHECKS = 100;

    private void scheduleEngineOffWhenStopped(Train t, int remainingChecks) {
        Tractor director = t.getDirectorLinker();
        if (director == null || remainingChecks <= 0 || director.getSpeed() == 0
                || model.getScheduler() == null) {
            turnEnginesOff(t);
            return;
        }
        model.getScheduler().schedule(2,
                () -> scheduleEngineOffWhenStopped(t, remainingChecks - 1));
    }

    /** Explicit engine off for the whole consist; the autopilot is left untouched. */
    private void turnEnginesOff(Train t) {
        t.getLocomotives().forEach(l -> l.setEngineOn(false));
        log.info("[DSL] Train {} parked: engine off, autopilot kept", t.getId());
    }

    /**
     * Couple/uncouple count (U6): a number {@code >= 1}, or {@code all} (every vehicle on that
     * side). No count uses {@code defaultValue} (all, for both orders); {@code 0} and negative
     * counts are invalid: they warn and the order does nothing (never a silent no-op). Returns
     * {@code null} when the count is invalid.
     */
    private Integer resolveVehicleCount(ScriptLogicParser.VehicleCountContext ctx, int defaultValue,
            String orderText) {
        if (ctx == null) {
            return defaultValue;
        }
        if (ctx.ALL() != null) {
            return letrain.vehicle.rail.TrainCouplingManager.ALL;
        }
        int count = Integer.parseInt(ctx.NUMBER().getText());
        if (count < 1) {
            warnUser("Coupling", "Invalid vehicle count " + count + " in '" + orderText
                    + "'; use a number >= 1 or 'all'");
            return null;
        }
        return count;
    }

    /** Readable form of a couple/uncouple action for notices ("uncouple backward 2"). */
    private static String couplingText(String verb, boolean forward,
            ScriptLogicParser.VehicleCountContext count) {
        return verb + (forward ? " forward" : " backward")
                + (count == null ? "" : " " + count.getText());
    }

    /**
     * Destination and speed of a {@code stopOrder}, shared by loose orders and waypoint actions.
     */
    private record MissionSpec(TrainMission.Kind kind, int targetId, int speed) {
    }

    /**
     * Resolves a {@code stopOrder} into a mission spec, or null (after warning) when the target is
     * unknown. The waypoint actions (ADR-022 phase 2f) and the loose orders share this.
     */
    private MissionSpec resolveMissionSpec(ScriptLogicParser.StopOrderContext ctx) {
        int speed = ctx.missionSpeed() != null
                ? Integer.parseInt(ctx.missionSpeed().trainSpeed().getText())
                : 0;
        int clamped = clampSpeed(speed);
        if (clamped != speed) {
            warnUser("Speed", "Mission speed " + speed + " is out of range 0-10; using " + clamped);
        }
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
            installMissionNotifier(t);
            t.getAutopilot().startMission(mission);
        };
    }

    /**
     * Wires the autopilot mission notifier to the asynchronous (deferred) user channel (D1:
     * itinerary maneuver problems must reach the panel, never the command line). While the typed
     * command that started the mission is still running, the console's deferred sink folds those
     * notices into the command feedback; later failures go to the panel. Without a deferred sink
     * the notifier is left untouched, so a caller-provided one (headless tests) survives.
     */
    private void installMissionNotifier(Train train) {
        if (train.getAutopilot() == null || deferredWarningSink == null) {
            return;
        }
        java.util.function.BiConsumer<String, String> sink = deferredWarningSink;
        train.getAutopilot().setMissionNotifier(text -> sink.accept("Autopilot", text));
    }

    /** Clamps a requested speed to the engine range 0..MAX_SPEED (0 disables speed actions). */
    private static int clampSpeed(int speed) {
        return Math.max(0, Math.min(letrain.vehicle.rail.impl.Locomotive.MAX_SPEED, speed));
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

    /**
     * Deferred user notice: a trigger block or a mission problem that fires after the statement
     * that armed it. It travels on the asynchronous channel (panel); while a typed command is still
     * running, the console folds it into that command's feedback instead. Without a deferred sink
     * it stays in the log (headless callers keep their own notifier).
     */
    private void warnDeferred(String title, String text) {
        log.warn("[DSL] {}", text);
        if (deferredWarningSink != null) {
            deferredWarningSink.accept(title, text);
        }
    }

    // ── Direct command visitors ──────────────────────────────────────

    @Override
    public Object visitCreateItinerary(ScriptLogicParser.CreateItineraryContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        currentItinerary = new ItineraryImpl();
        itineraryProblems.clear();
        for (ScriptLogicParser.WaypointContext wp : ctx.waypoint()) {
            visit(wp);
        }
        if (!itineraryProblems.isEmpty()) {
            // D1: a waypoint with an unknown destination used to be dropped in silence and the
            // plan ran with a missing stop. Reject the whole itinerary instead. A rejected
            // (re)definition also retires any previous definition of that name, so a later
            // `assign` warns "not found" instead of silently assigning a stale plan.
            itineraries.remove(name);
            warnUser("Itinerary", "Itinerary '" + name + "' not created: "
                    + String.join("; ", itineraryProblems));
        } else if (currentItinerary.isValid()) {
            itineraries.put(name, currentItinerary);
            log.info("[DSL] Created itinerary '{}' with {} waypoints", name,
                    currentItinerary.waypoints().size());
        } else {
            // A definition with fewer than two waypoints is rejected too: it retires the previous
            // plan under that name (same rule as the problem rejection above).
            itineraries.remove(name);
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
                itineraryProblems.add("station " + ctx.stationRef().getText() + " not found");
                return null;
            }
            wp = new WaypointImpl(Waypoint.Type.STATION, st.getId(),
                    Optional.ofNullable(resolveDir(ctx)), resolveCommands(ctx), resolveArrival(ctx),
                    resolveDeparture(ctx));
        } else if (ctx.sensorRef() != null) {
            Sensor se = resolveSensor(ctx.sensorRef());
            if (se == null) {
                itineraryProblems.add("sensor " + ctx.sensorRef().getText() + " not found");
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
        if (plan == null || plan.arrivalAttr() == null) {
            return Optional.empty();
        }
        return parseTime(plan.arrivalAttr().waypointTime(), "arrival");
    }

    private Optional<LocalTime> resolveDeparture(ScriptLogicParser.WaypointContext ctx) {
        ScriptLogicParser.WaypointPlanContext plan = ctx.waypointPlan();
        if (plan == null || plan.departureAttr() == null) {
            return Optional.empty();
        }
        return parseTime(plan.departureAttr().waypointTime(), "departure");
    }

    /**
     * Parses a grammar-validated time into a time of day. U8: `9` means 09:00 and `9:20` the full
     * form; TIME already restricts `HH:MM` to 00:00..23:59, so the range check here only rejects a
     * bare hour out of 0..23 (`arrival 25`). A bad time makes the waypoint unknown and the whole
     * itinerary is rejected with the notice (D1: no plan runs with a broken stop).
     */
    private Optional<LocalTime> parseTime(ScriptLogicParser.WaypointTimeContext ctx, String attr) {
        String text = ctx.getText();
        String[] parts = text.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            itineraryProblems.add(attr + " " + text
                    + " is out of range (expected 0..23 hours and 0..59 minutes)");
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
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
            warnUser("Train",
                    "Train " + ctx.trainRef().getText() + " not found; itinerary not assigned");
            return null;
        }
        // Autopilot is always instantiated. Set pathfinder and assign itinerary
        if (model.getRailwayGraph() != null) {
            train.getAutopilot().setPathfinder(new letrain.itinerary.AStarPathfinder(
                    model.getRailwayGraph(), model.getBlockManager(), train));
        }
        train.getAutopilot().setItinerary(it);
        // D1: waypoint maneuver problems must reach the user too, not only loose orders.
        installMissionNotifier(train);
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
        } else {
            warnUser("Train",
                    "Train " + ctx.trainRef().getText() + " not found; autopilot unchanged");
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
            warnUser("Train", "Train " + ctx.trainRef().getText() + " not found; order ignored");
        }
        return null;
    }

    @Override
    public Object visitSetNameCommand(ScriptLogicParser.SetNameCommandContext ctx) {
        String name = stripQuotes(ctx.STRING().getText());
        int id = Integer.parseInt(ctx.NUMBER().getText());
        // Compare by token type, not by text: `st`/`sn` are the same tokens as `station`/`sensor`
        // and the old getText() comparison made `st 1 set name "X"` a silent no-op.
        if (ctx.STATION() != null) {
            Station s = model.getStation(id);
            if (s != null) {
                s.setName(name);
                log.info("[DSL] Station {} named '{}'", id, name);
            } else {
                warnUser("Station", "Station " + id + " not found; name unchanged");
            }
        } else if (ctx.SENSOR() != null) {
            Sensor s = model.getSensor(id);
            if (s != null) {
                s.setName(name);
                log.info("[DSL] Sensor {} named '{}'", id, name);
            } else {
                warnUser("Sensor", "Sensor " + id + " not found; name unchanged");
            }
        } else if (ctx.TRAIN() != null) {
            Train t = model.getTrainFromLocomotiveId(id);
            if (t != null) {
                t.setName(name);
                log.info("[DSL] Train {} named '{}'", id, name);
            } else {
                warnUser("Train", "Train " + id + " not found; name unchanged");
            }
        }
        return null;
    }



    @Override
    public Object visitDirectForkCommand(ScriptLogicParser.DirectForkCommandContext ctx) {
        int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
        letrain.track.rail.ForkRailTrack fork = model.getFork(id);
        if (fork == null) {
            warnUser("Fork", "Fork " + id + " not found; order ignored");
            return null;
        }
        String dir = ctx.forkAction().forkDirection() != null
                ? ctx.forkAction().forkDirection().getText().toLowerCase(java.util.Locale.ROOT)
                : "flip";
        if (!applyForkDirection(fork, dir)) {
            warnUser("Fork", "Fork " + id + " has no route towards " + dir + "; unchanged");
        } else {
            log.info("[DSL] Direct fork {} set towards {}", id, dir);
        }
        return null;
    }

    /**
     * Maps `fork set <direction>` to the physical route, with the same behaviour in console,
     * trigger and waypoint orders (D3): `straight` / `curved` force those routes, `flip` toggles,
     * and a compass direction selects the route that leaves towards it. Returns false when the fork
     * has no such route (the caller warns); the dead `left`/`right` spellings are gone.
     */
    private boolean applyForkDirection(ForkRailTrack fork, String direction) {
        if ("straight".equals(direction)) {
            fork.setStraightRoute();
            return true;
        }
        if ("curved".equals(direction)) {
            fork.setCurvedRoute();
            return true;
        }
        if ("flip".equals(direction)) {
            fork.flipRoute();
            return true;
        }
        try {
            letrain.map.Dir dir =
                    letrain.map.Dir.valueOf(direction.toUpperCase(java.util.Locale.ROOT));
            letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> originalRoute =
                    fork.getOriginalRoute();
            letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> alternativeRoute =
                    fork.getAlternativeRoute();
            if (originalRoute != null && originalRoute.getValue() == dir) {
                fork.setNormalRoute();
                return true;
            }
            if (alternativeRoute != null && alternativeRoute.getValue() == dir) {
                fork.setAlternativeRoute();
                return true;
            }
        } catch (IllegalArgumentException e) {
            // Not a compass direction: falls through to "no route".
        }
        return false;
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
            } else if (act.TOGGLE() != null) {
                sem.setOpen(!sem.isOpen());
                log.info("[DSL] Direct semaphore {} toggled to {}", id,
                        sem.isOpen() ? "open" : "closed");
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
        } else {
            warnUser("Semaphore", "Semaphore " + id + " not found; order ignored");
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
        } else {
            warnUser("Signal", "Signal " + id + " not found; order ignored");
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
            warnUser("Station", "Station " + id + " not found; order ignored");
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
            warnUser("Sensor", "Plain sensor " + id + " not found; order ignored");
        }
        return null;
    }

    private Station resolveStation(ScriptLogicParser.StationRefContext ctx) {
        if (ctx.STRING() != null)
            return model.findStationByName(stripQuotes(ctx.STRING().getText()));
        return model.getStation(Integer.parseInt(ctx.NUMBER().getText()));
    }

    private Sensor resolveSensor(ScriptLogicParser.SensorRefContext ctx) {
        Sensor resolved;
        if (ctx.STRING() != null) {
            resolved = model.findSensorByName(stripQuotes(ctx.STRING().getText()));
        } else {
            resolved = model.getSensor(Integer.parseInt(ctx.NUMBER().getText()));
        }
        if (resolved != null && resolved.getClass() != letrain.track.Sensor.class) {
            // Ids collide with stations and speed signals; those never match a "plain sensor"
            // mission, which then never completes (review item 18). Reject it loudly.
            warnUser("Sensor", "Sensor " + ctx.getText()
                    + " is not a plain sensor (station or speed signal); order ignored");
            return null;
        }
        return resolved;
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
            Integer count = resolveVehicleCount(couple.vehicleCount(), TrainCouplingManager.ALL,
                    couplingText("couple", forward, couple.vehicleCount()));
            if (count == null) {
                itineraryProblems.add("invalid vehicle count in '"
                        + couplingText("couple", forward, couple.vehicleCount())
                        + "' (use a number >= 1 or 'all')");
                return List.of();
            }
            return List.of(WaypointCommand.couple(forward, count));
        }
        if (ctx.uncoupleAction() != null) {
            ScriptLogicParser.UncoupleActionContext uncouple = ctx.uncoupleAction();
            boolean forward = uncouple.sense().getText().startsWith("f");
            // U6: no count means every vehicle on that side, exactly like `couple`.
            Integer count = resolveVehicleCount(uncouple.vehicleCount(), TrainCouplingManager.ALL,
                    couplingText("uncouple", forward, uncouple.vehicleCount()));
            if (count == null) {
                itineraryProblems.add("invalid vehicle count in '"
                        + couplingText("uncouple", forward, uncouple.vehicleCount())
                        + "' (use a number >= 1 or 'all')");
                return List.of();
            }
            return List.of(WaypointCommand.uncouple(forward, count));
        }
        if (ctx.stopOrder() != null) {
            MissionSpec spec = resolveMissionSpec(ctx.stopOrder());
            if (spec == null) {
                itineraryProblems.add("unknown destination in '" + ctx.stopOrder().getText() + "'");
                return List.of();
            }
            return List.of(WaypointCommand.mission(spec.kind(), spec.targetId(), spec.speed()));
        }
        if (ctx.forkSelector() != null) {
            int forkId = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            String direction = ctx.forkAction().forkDirection() != null
                    ? ctx.forkAction().forkDirection().getText().toLowerCase(java.util.Locale.ROOT)
                    : "flip";
            letrain.track.rail.ForkRailTrack fork = model.getFork(forkId);
            if (fork != null && !forkHasRoute(fork, direction)) {
                // D3: same mapping and same warning as the console and the trigger; the action is
                // kept so the itinerary still parses, but the author sees the problem now.
                warnUser("Fork", "Fork " + forkId + " has no route towards " + direction
                        + "; action will do nothing");
            }
            if ("flip".equals(direction)) {
                return List.of(WaypointCommand.forkFlip(forkId));
            }
            return List.of(WaypointCommand.forkSetDirection(forkId, direction));
        }
        String text = ctx.getText().toLowerCase(java.util.Locale.ROOT);
        return switch (text) {
            case "load" -> List.of(WaypointCommand.LOAD);
            case "unload" -> List.of(WaypointCommand.UNLOAD);
            // U2: `reverse` and `invert` are synonyms; both are the same waypoint action.
            case "reverse", "invert" -> List.of(WaypointCommand.REVERSE);
            case "stop" -> List.of(WaypointCommand.STOP);
            case "park" -> List.of(WaypointCommand.PARK);
            default -> {
                if (text.startsWith("wait")) {
                    int seconds = Integer.parseInt(ctx.NUMBER().getText());
                    yield List.of(WaypointCommand.waitSeconds(seconds));
                } else if (text.startsWith("speed")) {
                    int speed = Integer.parseInt(ctx.NUMBER().getText());
                    int clamped = clampSpeed(speed);
                    if (clamped != speed) {
                        warnUser("Speed", "Waypoint speed " + speed
                                + " is out of range 0-10; using " + clamped);
                    }
                    yield List.of(WaypointCommand.speed(clamped));
                }
                yield List.of();
            }
        };
    }

    /**
     * True when `fork set <direction>` can select a route: `straight`/`curved`/`flip` always map
     * (with geometric fallbacks) and a compass direction needs a route leaving towards it.
     */
    private boolean forkHasRoute(ForkRailTrack fork, String direction) {
        if ("straight".equals(direction) || "curved".equals(direction)
                || "flip".equals(direction)) {
            return true;
        }
        try {
            letrain.map.Dir dir =
                    letrain.map.Dir.valueOf(direction.toUpperCase(java.util.Locale.ROOT));
            letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> originalRoute =
                    fork.getOriginalRoute();
            letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> alternativeRoute =
                    fork.getAlternativeRoute();
            return (originalRoute != null && originalRoute.getValue() == dir)
                    || (alternativeRoute != null && alternativeRoute.getValue() == dir);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            return s.substring(1, s.length() - 1);
        return s;
    }

    /**
     * Outcome of {@code train at <place>}: the train resting there (or null) and whether the place
     * itself exists. An unknown place is warned here and reported with {@code placeKnown=false} so
     * the caller does not add a second, misleading "no train" notice on top.
     */
    private record PlaceTrain(Train train, boolean placeKnown) {
    }

    private PlaceTrain findTrainAtPlace(ScriptLogicParser.PlaceSelectorContext ctx) {
        if (ctx.stationSelector() != null) {
            Station s = resolveTriggerStation(ctx.stationSelector());
            if (s == null) {
                warnDeferred("Station", "Station " + selectorRef(ctx.stationSelector())
                        + " not found; action ignored");
                return new PlaceTrain(null, false);
            }
            return new PlaceTrain(trainAtComponent(s), true);
        }
        if (ctx.sensorSelector() != null) {
            Sensor s = resolveTriggerSensor(ctx.sensorSelector());
            if (s == null) {
                warnDeferred("Sensor", "Sensor " + selectorRef(ctx.sensorSelector())
                        + " not found; action ignored");
                return new PlaceTrain(null, false);
            }
            return new PlaceTrain(trainAtComponent(s), true);
        }
        if (ctx.forkSelector() != null) {
            int id = Integer.parseInt(ctx.forkSelector().NUMBER().getText());
            if (model.getFork(id) == null) {
                warnDeferred("Fork", "Fork " + id + " not found; action ignored");
                return new PlaceTrain(null, false);
            }
            return new PlaceTrain(null, true); // no train lookup at forks (unchanged)
        }
        if (ctx.semaphoreSelector() != null) {
            int id = Integer.parseInt(ctx.semaphoreSelector().NUMBER().getText());
            if (model.getSemaphore(id) == null) {
                warnDeferred("Semaphore", "Semaphore " + id + " not found; action ignored");
                return new PlaceTrain(null, false);
            }
            return new PlaceTrain(null, true);
        }
        return new PlaceTrain(null, true);
    }

    /** The train whose head locomotive rests on the track component, or null. */
    private Train trainAtComponent(Sensor component) {
        for (Locomotive l : model.getLocomotives()) {
            if (l.getTrack() != null && l.getTrack().getComponent() == component) {
                return l.getTrain();
            }
        }
        return null;
    }

    /**
     * U4: trigger and place selectors resolve a station reference (number or exact quoted name).
     * The name is looked up only when the reference is a name; case matters (strict policy).
     */
    private Station resolveTriggerStation(ScriptLogicParser.StationSelectorContext ctx) {
        if (ctx.STRING() != null) {
            return model.findStationByName(stripQuotes(ctx.STRING().getText()));
        }
        return model.getStation(Integer.parseInt(ctx.NUMBER().getText()));
    }

    /**
     * U4: sensor reference (number or exact quoted name), mirroring {@link #resolveTriggerStation}.
     */
    private Sensor resolveTriggerSensor(ScriptLogicParser.SensorSelectorContext ctx) {
        if (ctx.STRING() != null) {
            return model.findSensorByName(stripQuotes(ctx.STRING().getText()));
        }
        return model.getSensor(Integer.parseInt(ctx.NUMBER().getText()));
    }

    /** Readable trigger selector reference ("1" or "\"Norte\"") for problem notices. */
    private static String selectorRef(ScriptLogicParser.StationSelectorContext ctx) {
        if (ctx.STRING() != null) {
            return "\"" + stripQuotes(ctx.STRING().getText()) + "\"";
        }
        return ctx.NUMBER().getText();
    }

    private static String selectorRef(ScriptLogicParser.SensorSelectorContext ctx) {
        if (ctx.STRING() != null) {
            return "\"" + stripQuotes(ctx.STRING().getText()) + "\"";
        }
        return ctx.NUMBER().getText();
    }

    /**
     * Human-readable place selector ("station 1", "sensor \"Norte\"") for notices. ANTLR's
     * {@code getText()} glues the tokens without spaces ("station1"), which made the
     * {@code train at} warning unreadable (O1).
     */
    private static String describePlace(ScriptLogicParser.PlaceSelectorContext ctx) {
        if (ctx.stationSelector() != null) {
            return "station " + selectorRef(ctx.stationSelector());
        }
        if (ctx.sensorSelector() != null) {
            return "sensor " + selectorRef(ctx.sensorSelector());
        }
        if (ctx.forkSelector() != null) {
            return "fork " + ctx.forkSelector().NUMBER().getText();
        }
        if (ctx.semaphoreSelector() != null) {
            return "semaphore " + ctx.semaphoreSelector().NUMBER().getText();
        }
        return ctx.getText();
    }
}
