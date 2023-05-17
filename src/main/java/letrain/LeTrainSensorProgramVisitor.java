package letrain;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.utils.Pair;
import letrain.vehicle.impl.rail.Train;

public class LeTrainSensorProgramVisitor extends LeTrainProgramBaseVisitor<Void> implements Serializable {
    private static final long serialVersionUID = 1L;
    static Logger log = LoggerFactory.getLogger(LeTrainSensorProgramVisitor.class);

    String sensorId;
    String trainId;
    String trainAction;
    String trainDir;
    Model model;

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
        final String semaphoreId;
        final String semaphoreAction;
        final SemaphoreStateConsumer semaphoreStateConsumer;
        final String forkId;
        final String forkDirection;
        final ForkDirConsumer forkDirConsumer;
        String speedLimitType = null;
        String speedLimit = null;
        if (ctx.semaphoreAction() != null) {
            forkDirConsumer = null;
            forkId = null;
            forkDirection = null;
            semaphoreId = ctx.NUMBER().getText();
            semaphoreAction = ctx.semaphoreAction().getText();
            semaphoreStateConsumer = new SemaphoreStateConsumer();
        } else if (ctx.dir() != null) {
            forkId = ctx.NUMBER().getText();
            forkDirection = ctx.dir().getText();
            forkDirConsumer = new ForkDirConsumer();
            semaphoreId = null;
            semaphoreAction = null;
            semaphoreStateConsumer = null;
        } else if (ctx.speedLimit() != null) {
            forkDirConsumer = null;
            forkId = null;
            forkDirection = null;
            speedLimitType = ctx.speedLimit().getText();
            speedLimit = ctx.NUMBER().getText();
            semaphoreId = null;
            semaphoreAction = null;
            semaphoreStateConsumer = null;
        } else {
            forkDirConsumer = null;
            forkId = null;
            forkDirection = null;
            semaphoreId = null;
            semaphoreAction = null;
            semaphoreStateConsumer = null;
        }

        Sensor sensor = model.getSensor(Integer.parseInt(sensorId));
        if (sensor == null) {
            return null;
        }
        if ("enter".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    if (forkDirConsumer != null) {
                        doForkAction(forkId, forkDirection, forkDirConsumer, train);
                    }
                    if (semaphoreStateConsumer != null) {
                        doSemaphoreAction(semaphoreId, semaphoreAction, semaphoreStateConsumer);
                    }
                }
            });
        } else if ("exit".equals(trainAction)) {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    if (forkDirConsumer != null) {
                        doForkAction(forkId, forkDirection, forkDirConsumer, train);
                    }
                    if (semaphoreStateConsumer != null) {
                        doSemaphoreAction(semaphoreId, semaphoreAction, semaphoreStateConsumer);
                    }
                }
            });
        } else {
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onEnterTrain(Train train) {
                    if (forkDirConsumer != null) {
                        doForkAction(forkId, forkDirection, forkDirConsumer, train);
                    }
                    if (semaphoreStateConsumer != null) {
                        doSemaphoreAction(semaphoreId, semaphoreAction, semaphoreStateConsumer);
                    }
                }
            });
            sensor.addSensorEventListener(new SensorEventListener() {
                @Override
                public void onExitTrain(Train train) {
                    if (forkDirConsumer != null) {
                        doForkAction(forkId, forkDirection, forkDirConsumer, train);
                    }
                    if (semaphoreStateConsumer != null) {
                        doSemaphoreAction(semaphoreId, semaphoreAction, semaphoreStateConsumer);
                    }
                }
            });
        }
        return super.visitCommandItem(ctx);
    }

    void doForkAction(final String forkId, final String forkDirection,
            final ForkDirConsumer forkDirConsumer, Train train) {
        if (trainId != null && !trainId.isEmpty()) {
            if (Integer.parseInt(trainId) != train.getId()) {
                return;
            }
        }
        ForkRailTrack fork = model.getFork(Integer.parseInt(forkId));
        forkDirConsumer.accept(new Pair<ForkRailTrack, Dir>(fork, Dir.valueOf(forkDirection)));
    }

    void doSemaphoreAction(final String semaphoreId, final String semaphoreState,
            final SemaphoreStateConsumer semaphoreStateConsumer) {
        RailSemaphore semaphore = model.getSemaphore(Integer.parseInt(semaphoreId));
        Boolean state = semaphoreState.toUpperCase().equals("OPEN") ? true : false;
        semaphoreStateConsumer.accept(new Pair<RailSemaphore, Boolean>(semaphore,
                state));
    }

    public static void saveModel(Model model, String file) {
        try (FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(model);
        } catch (IOException ex) {
            LeTrainSensorProgramVisitor.log.error("Error saving model", ex);
        }

    }

    public static Model loadModel(String file) {
        try (FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            Model model = (Model) ois.readObject();
            return model;
        } catch (IOException | ClassNotFoundException ex) {
            log.error("Error loading model", ex);
            return null;
        }
    }

    public static void readProgram(letrain.mvp.Model model) {
        for (Sensor sensor : model.getSensors()) {
            sensor.removeAllSensorEventListeners();
        }

        CharStream input = null;
        try {
            input = CharStreams.fromFileName("commands.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
        LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

        // Obtener el árbol de análisis sintáctico
        LeTrainProgramParser.StartContext tree = parser.start();

        // Crear un Visitor personalizado para procesar los comandos
        LeTrainSensorProgramVisitor visitor = new LeTrainSensorProgramVisitor(model);

        // Visitar el árbol utilizando el Visitor personalizado
        visitor.visit(tree);
    }
}