package letrain;

import java.util.function.Consumer;

import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;
import letrain.vehicle.impl.rail.Train;

class LeTrainSensorProgramVisitor extends LeTrainProgramBaseVisitor<Void> {
    private String sensorId;
    private String trainId;
    private String trainAction;
    private String trainDir;
    private Model model;

    public LeTrainSensorProgramVisitor(Model model) {
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
        String semaphoreId = null;
        String semaphoreAction = null;
        final String forkId;
        final String forkDirection;
        final Consumer<Pair<ForkRailTrack, Dir>> forkConsumer;
        String speedLimitType = null;
        String speedLimit = null;
        if (ctx.semaphoreAction() != null) {
            forkConsumer = null;
            forkId = null;
            forkDirection = null;
            semaphoreId = ctx.NUMBER().getText();
            semaphoreAction = ctx.semaphoreAction().getText();
        } else if (ctx.dir() != null) {
            forkId = ctx.NUMBER().getText();
            forkDirection = ctx.dir().getText();
            forkConsumer = (Pair<ForkRailTrack, Dir> input) -> {
                ForkRailTrack fork = input.getFirst();
                if (fork.getAlternativeRoute().getSecond().equals(input.getSecond())) {
                    fork.setAlternativeRoute();
                } else {
                    fork.setNormalRoute();
                }
            };
        } else if (ctx.speedLimit() != null) {
            forkConsumer = null;
            forkId = null;
            forkDirection = null;
            speedLimitType = ctx.speedLimit().getText();
            speedLimit = ctx.NUMBER().getText();
        } else {
            forkConsumer = null;
            forkId = null;
            forkDirection = null;
        }

        Sensor sensor = model.getSensor(Integer.parseInt(sensorId));
        if (sensor == null) {
            return null;

        }
        if ("enter".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    doForkAction(forkId, forkDirection, forkConsumer, train);
                }
            });
        } else if ("exit".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    doForkAction(forkId, forkDirection, forkConsumer, train);
                }
            });
        } else {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    doForkAction(forkId, forkDirection, forkConsumer, train);
                }
            });
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    doForkAction(forkId, forkDirection, forkConsumer, train);
                }
            });
        }
        return super.visitCommandItem(ctx);
    }

    void doForkAction(final String forkId, final String forkDirection,
            final Consumer<Pair<ForkRailTrack, Dir>> forkConsumer, Train train) {
        if (trainId != null) {
            if (Integer.parseInt(trainId) != train.getId()) {
                return;
            }
        }
        if (forkConsumer != null) {
            ForkRailTrack fork = model.getFork(Integer.parseInt(forkId));
            forkConsumer.accept(new Pair<ForkRailTrack, Dir>(fork, Dir.valueOf(forkDirection)));
        }
    }

}