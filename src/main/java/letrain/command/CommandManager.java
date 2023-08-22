package letrain.command;

import java.io.Serializable;
import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Locomotive.SpeedLimitType;
import letrain.vehicle.impl.rail.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandManager extends LeTrainProgramBaseVisitor<Void> implements Serializable {
    private static final long serialVersionUID = 1L;
    static Logger log = LoggerFactory.getLogger(CommandManager.class);
    String sensorId;
    String trainId;
    String trainAction;
    String trainDir;
    Model model;

    public CommandManager(Model model) {
        this.model = model;
    }

    @Override
    public Void visitCommand(LeTrainProgramParser.CommandContext ctx) {
        sensorId = ctx.NUMBER(0).getText();
        trainId = ctx.NUMBER().size() > 1 ? ctx.NUMBER(1).getText() : "";
        trainAction = ctx.trainAction() != null ? ctx.trainAction().getText() : "";
        trainDir = ctx.dir() != null ? ctx.dir().getText() : "";
        return super.visitCommand(ctx);
    }

    @Override
    public Void visitCommandItem(LeTrainProgramParser.CommandItemContext ctx) {
        final String semaphoreId;
        final String semaphoreActionText;
        final SemaphoreAction semaphoreAction;

        final String forkId;
        final String forkDirectionText;
        final ForkDirAction forkDirAction;

        final String speedLimitType;
        final String speedLimitText;
        final TrainSpeedAction trainSpeedAction;

        if (ctx.semaphoreAction() != null) {
            semaphoreId = ctx.NUMBER().getText();
            semaphoreActionText = ctx.semaphoreAction().getText();
            semaphoreAction = new SemaphoreAction();

            forkDirAction = null;
            forkId = null;
            forkDirectionText = null;

            trainSpeedAction = null;
            speedLimitType = null;
            speedLimitText = null;
        } else if (ctx.dir() != null) {
            semaphoreId = null;
            semaphoreActionText = null;
            semaphoreAction = null;

            forkId = ctx.NUMBER().getText();
            forkDirectionText = ctx.dir().getText();
            forkDirAction = new ForkDirAction();

            trainSpeedAction = null;
            speedLimitType = null;
            speedLimitText = null;
        } else if (ctx.speedLimit() != null) {
            semaphoreId = null;
            semaphoreActionText = null;
            semaphoreAction = null;

            forkDirAction = null;
            forkId = null;
            forkDirectionText = null;

            trainSpeedAction = new TrainSpeedAction();
            speedLimitType = ctx.speedLimit().getText();
            speedLimitText = ctx.NUMBER().getText();
        } else {
            semaphoreAction = null;
            semaphoreId = null;
            semaphoreActionText = null;

            forkDirAction = null;
            forkId = null;
            forkDirectionText = null;

            trainSpeedAction = null;
            speedLimitType = null;
            speedLimitText = null;
        }

        Sensor sensor = model.getSensor(Integer.parseInt(sensorId));
        if (sensor == null) {
            return null;
        }
        if ("enter".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    if (forkDirAction != null) {
                        doForkAction(forkId, forkDirectionText, forkDirAction, train);
                    }
                    if (semaphoreAction != null) {
                        doSemaphoreAction(semaphoreId, semaphoreActionText, semaphoreAction);
                    }
                    if (trainSpeedAction != null) {
                        doTrainSpeedAction(speedLimitType, speedLimitText, trainSpeedAction, train);
                    }
                }
            });
        } else if ("exit".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    if (forkDirAction != null) {
                        doForkAction(forkId, forkDirectionText, forkDirAction, train);
                    }
                    if (semaphoreAction != null) {
                        doSemaphoreAction(semaphoreId, semaphoreActionText, semaphoreAction);
                    }
                }
            });
        } else {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    if (forkDirAction != null) {
                        doForkAction(forkId, forkDirectionText, forkDirAction, train);
                    }
                    if (semaphoreAction != null) {
                        doSemaphoreAction(semaphoreId, semaphoreActionText, semaphoreAction);
                    }
                }
            });
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    if (forkDirAction != null) {
                        doForkAction(forkId, forkDirectionText, forkDirAction, train);
                    }
                    if (semaphoreAction != null) {
                        doSemaphoreAction(semaphoreId, semaphoreActionText, semaphoreAction);
                    }
                }
            });
        }
        return super.visitCommandItem(ctx);
    }

    void doForkAction(final String forkId, final String forkDirection,
            final ForkDirAction forkDirConsumer, Train train) {
        if (trainId != null && !trainId.isEmpty()) {
            if (Integer.parseInt(trainId) != train.getId()) {
                return;
            }
        }
        ForkRailTrack fork = model.getFork(Integer.parseInt(forkId));
        forkDirConsumer.accept(new Pair<ForkRailTrack, Dir>(fork, Dir.valueOf(forkDirection)));
    }

    void doSemaphoreAction(final String semaphoreId, final String semaphoreState,
            final SemaphoreAction semaphoreStateConsumer) {
        RailSemaphore semaphore = model.getSemaphore(Integer.parseInt(semaphoreId));
        Boolean state = semaphoreState.toUpperCase().equals("OPEN") ? true : false;
        semaphoreStateConsumer.accept(new Pair<RailSemaphore, Boolean>(semaphore,
                state));
    }

    void doTrainSpeedAction(final String speedLimitType, final String speedLimit,
            final TrainSpeedAction trainSpeedConsumer, Train train) {
        if (trainId != null && !trainId.isEmpty()) {
            if (Integer.parseInt(trainId) != train.getId()) {
                return;
            }
        }
        SpeedLimitType type;
        if (speedLimitType.toUpperCase().equals("MAX")) {
            type = SpeedLimitType.MAX_SPEED;
        } else {
            type = SpeedLimitType.MIN_SPEED;
        }
        trainSpeedConsumer.accept(new Pair<Train, Pair<SpeedLimitType, Integer>>(
                train,
                new Pair<SpeedLimitType, Integer>(type, Integer.parseInt(speedLimit))));
    }

    class SemaphoreAction implements Serializable, Consumer<Pair<RailSemaphore, Boolean>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void accept(Pair<RailSemaphore, Boolean> input) {
            input.getFirst().setOpen(input.getSecond());
        }
    }

    class ForkDirAction implements Serializable, Consumer<Pair<ForkRailTrack, Dir>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void accept(Pair<ForkRailTrack, Dir> input) {
            if (input.getFirst().getAlternativeRoute().getSecond().equals(input.getSecond())) {
                input.getFirst().setAlternativeRoute();
            } else {
                input.getFirst().setNormalRoute();
            }
        }
    }

    class TrainSpeedAction implements Serializable, Consumer<Pair<Train, Pair<SpeedLimitType, Integer>>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void accept(Pair<Train, Pair<SpeedLimitType, Integer>> input) {
            Locomotive locomotive = (Locomotive) (input.getFirst().getDirectorLinker());
            Integer speed = input.getSecond().getSecond();
            if (input.getSecond().getFirst() == SpeedLimitType.MAX_SPEED) {
                locomotive.setMaxSpeed(speed);
            } else {
                locomotive.setMinSpeed(speed);
            }
        }
    }

}