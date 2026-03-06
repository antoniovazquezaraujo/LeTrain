package letrain.command;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import letrain.mvp.Model;
import letrain.track.ForkEventListener;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.StationEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandManager extends LeTrainProgramBaseVisitor<Object> implements Serializable {
    private static final long serialVersionUID = 1L;
    static Logger log = LoggerFactory.getLogger(CommandManager.class);
    Model model;

    public CommandManager(Model model) {
        this.model = model;
    }

    interface ExecutableCommand extends Serializable {
        void execute(Train contextTrain);
    }

    @Override
    public Object visitStatement(LeTrainProgramParser.StatementContext ctx) {
        List<ExecutableCommand> commands = (List<ExecutableCommand>) visit(ctx.commandBlock());
        setupTrigger(ctx.trigger(), commands);
        return null;
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
                model.addTrainEventListener(new letrain.vehicle.impl.rail.TrainEventListener() {
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
                model.addTrainEventListener(new letrain.vehicle.impl.rail.TrainEventListener() {
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
            String actionText = ctx.trainAction().getText();
            ExecutableCommand baseAction;

            if (ctx.trainAction().trainSpeed() != null) {
                int speed = Integer.parseInt(ctx.trainAction().trainSpeed().getText());
                int clampedSpeed = Math.max(1, Math.min(10, speed));
                baseAction = (t) -> ((Locomotive) t.getDirectorLinker()).setSpeed(clampedSpeed);
            } else if (actionText.contains("accelerate")) {
                baseAction = (t) -> ((Locomotive) t.getDirectorLinker()).incSpeed();
            } else if (actionText.contains("decelerate")) {
                baseAction = (t) -> ((Locomotive) t.getDirectorLinker()).decSpeed();
            } else if (ctx.trainAction().trainSense() != null) {
                boolean forward = "forward".equals(ctx.trainAction().trainSense().getText());
                baseAction = (t) -> {
                    Locomotive l = (Locomotive) t.getDirectorLinker();
                    if (l.isReversed() == forward)
                        l.toggleReversed();
                };
            } else if (actionText.contains("stop")) {
                baseAction = (t) -> ((Locomotive) t.getDirectorLinker()).setSpeed(0);
            } else if (actionText.contains("invert")) {
                baseAction = (t) -> ((Locomotive) t.getDirectorLinker()).toggleReversed();
            } else if (ctx.trainAction().linkAction() != null) {
                LeTrainProgramParser.LinkActionContext lCtx = ctx.trainAction().linkAction();
                boolean forward = "forward".equals(lCtx.sense().getText());
                int count = lCtx.NUMBER() != null ? Integer.parseInt(lCtx.NUMBER().getText()) : 0;
                baseAction = (t) -> {
                    t.prepareLink(forward, count);
                    t.joinLinkers();
                };
            } else if (ctx.trainAction().unlinkAction() != null) {
                LeTrainProgramParser.UnlinkActionContext uCtx = ctx.trainAction().unlinkAction();
                boolean forward = "forward".equals(uCtx.sense().getText());
                int count = uCtx.NUMBER() != null ? Integer.parseInt(uCtx.NUMBER().getText()) : 1;
                baseAction = (t) -> {
                    t.prepareUnlink(forward, count);
                    t.divideTrain(() -> model.nextTrainId());
                };
            } else if (actionText.contains("unload")) {
                baseAction = (t) -> {
                    letrain.track.Station s = t.getStationAtTrain();
                    if (s != null)
                        t.startUnloadProcess(s);
                };
            } else if (actionText.contains("load")) {
                baseAction = (t) -> {
                    letrain.track.Station s = t.getStationAtTrain();
                    if (s != null)
                        t.startLoadProcess(s);
                };
            } else {
                baseAction = (t) -> {
                };
            }

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