package letrain.command;

import letrain.mvp.Model;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class PlayerCommandExecutor extends PlayerCommandsParserBaseVisitor<Object> {
    private static final Logger log = LoggerFactory.getLogger(PlayerCommandExecutor.class);
    private Model model;

    private java.util.function.Consumer<java.io.File> onSave;
    private java.util.function.Consumer<java.io.File> onLoad;
    private letrain.command.TurtleDelegate turtleDelegate;

    public PlayerCommandExecutor(Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate) {
        this.model = model;
        this.onSave = onSave;
        this.onLoad = onLoad;
        this.turtleDelegate = turtleDelegate;
    }

    public PlayerCommandExecutor(Model model) {
        this(model, null, null, null);
    }

    public static String execute(String commandText, Model model) {
        return execute(commandText, model, null, null, null);
    }

    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate) {
        if (!commandText.trim().endsWith(";")) {
            commandText = commandText + ";";
        }
        
        List<String> errors = new ArrayList<>();
        PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(new LeTrainLexer(CharStreams.fromString(commandText))));
        parser.removeErrorListeners();
        parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
                errors.add("Syntax Error at " + charPositionInLine + ": " + msg);
            }
        });

        PlayerCommandsParser.PlayerStartContext tree = parser.playerStart();
        if (!errors.isEmpty()) {
            return String.join("\n", errors);
        }

        try {
            PlayerCommandExecutor executor = new PlayerCommandExecutor(model, onSave, onLoad, turtleDelegate);
            executor.visit(tree);
            return null; // No errors
        } catch (Exception e) {
            log.error("Command execution error", e);
            return e.getMessage();
        }
    }

    @Override
    public Object visitStatement(PlayerCommandsParser.StatementContext ctx) {
        // Since it's a script logic statement, we can re-parse it with ScriptLogicParser
        // This avoids duplicating all the visitor logic for trains, semaphores, etc.
        // Wait, ANTLR4 tokens contain the start and stop index!
        int start = ctx.getStart().getStartIndex();
        int stop = ctx.getStop().getStopIndex();
        String originalText = ctx.getStart().getInputStream().getText(new org.antlr.v4.runtime.misc.Interval(start, stop));
        
        ScriptLogicParser scriptParser = new ScriptLogicParser(new CommonTokenStream(new LeTrainLexer(CharStreams.fromString(originalText))));
        ScriptLogicParser.ScriptStartContext scriptTree = scriptParser.scriptStart();
        CommandManager scriptManager = new CommandManager(model);
        scriptManager.visit(scriptTree);
        return null;
    }

    @Override
    public Object visitGoCommand(PlayerCommandsParser.GoCommandContext ctx) {
        int x = Integer.parseInt(ctx.NUMBER(0).getText());
        int y = Integer.parseInt(ctx.NUMBER(1).getText());
        model.getCursor().getPosition().setX(x);
        model.getCursor().getPosition().setY(y);
        return null;
    }

    @Override
    public Object visitNewCommand(PlayerCommandsParser.NewCommandContext ctx) {
        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.map.Dir dir = model.getCursor().getDir();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());

        if (ctx.STATION() != null) {
            if (track == null) throw new RuntimeException("Cannot place station: No track here.");
            if (track.getSensor() != null) throw new RuntimeException("Cannot place station: Track already has a sensor/station.");
            
            letrain.track.Station station = new letrain.track.Station(model.nextStationId());
            station.setTrack(track);
            station.setCreationDir(dir);
            station.setSideDir(dir.turnRight().turnRight());
            Integer foundTerrain = model.getGroundMap().findClosestIndustry(pos, 5);
            if (foundTerrain != null) {
                int densityCount = model.getGroundMap().countIndustryDensity(pos, 5, foundTerrain);
                station.setCargoType(letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(foundTerrain));
                station.setRole(letrain.track.CargoTypes.IndustryMapper.getRoleForTerrain(foundTerrain));
                station.setIndustryCount(densityCount);
                if (station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER) {
                    station.setStorage(50);
                }
            } else {
                station.setCargoType(letrain.track.CargoTypes.NONE);
                station.setRole(letrain.track.CargoTypes.StationRole.GENERIC);
            }
            model.addStation(station);
            track.setSensor(station);
        } else if (ctx.SENSOR() != null) {
            if (track == null) throw new RuntimeException("Cannot place sensor: No track here.");
            if (track.getSensor() != null) throw new RuntimeException("Cannot place sensor: Track already has a sensor/station.");
            
            letrain.track.Sensor sensor = new letrain.track.Sensor(model.nextSensorId());
            sensor.setTrack(track);
            sensor.setCreationDir(dir);
            model.addSensor(sensor);
            track.setSensor(sensor);
        } else if (ctx.SEMAPHORE() != null) {
            letrain.track.RailSemaphore sem = model.getSemaphoreAt(pos);
            if (sem != null) throw new RuntimeException("Cannot place semaphore: Already exists here.");
            sem = new letrain.track.RailSemaphore(model.nextSemaphoreId(), pos);
            sem.setCreationDir(dir);
            model.addSemaphore(sem);
        } else if (ctx.SIGNAL() != null) {
            if (track == null) throw new RuntimeException("Cannot place signal: No track here.");
            if (track.getSensor() != null) throw new RuntimeException("Cannot place signal: Track already has a sensor/station.");
            
            letrain.track.SpeedSignal speedSignal = new letrain.track.SpeedSignal(model.nextSensorId(), dir, 3, true);
            speedSignal.setTrack(track);
            model.addSensor(speedSignal);
            track.setSensor(speedSignal);
        } else if (ctx.FORK() != null) {
            throw new RuntimeException("Cannot place fork via script yet (use UI).");
        }
        return null;
    }

    @Override
    public Object visitDelCommand(PlayerCommandsParser.DelCommandContext ctx) {
        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());

        if (ctx.STATION() != null) {
            if (track == null || track.getSensor() == null || !(track.getSensor() instanceof letrain.track.Station)) {
                throw new RuntimeException("No station to delete here.");
            }
            model.removeStation((letrain.track.Station) track.getSensor());
        } else if (ctx.SENSOR() != null) {
            if (track == null || track.getSensor() == null || (track.getSensor() instanceof letrain.track.Station) || (track.getSensor() instanceof letrain.track.SpeedSignal)) {
                throw new RuntimeException("No standard sensor to delete here.");
            }
            model.removeSensor(track.getSensor());
        } else if (ctx.SIGNAL() != null) {
            if (track == null || track.getSensor() == null || !(track.getSensor() instanceof letrain.track.SpeedSignal)) {
                throw new RuntimeException("No signal to delete here.");
            }
            model.removeSensor(track.getSensor());
        } else if (ctx.SEMAPHORE() != null) {
            letrain.track.RailSemaphore sem = model.getSemaphoreAt(pos);
            if (sem == null) throw new RuntimeException("No semaphore to delete here.");
            model.removeSemaphore(sem);
        } else if (ctx.FORK() != null) {
            throw new RuntimeException("Cannot delete fork via script yet (use UI).");
        } else if (ctx.TRAIN() != null) {
            throw new RuntimeException("Cannot delete train via script yet (use UI).");
        }
        return null;
    }

    @Override
    public Object visitTurtleCommand(PlayerCommandsParser.TurtleCommandContext ctx) {
        if (turtleDelegate == null) {
            throw new RuntimeException("Turtle graphics not supported in this context (no UI handler available).");
        }
        
        letrain.vehicle.Cursor.CursorMode cursorMode = letrain.vehicle.Cursor.CursorMode.MOVING;
        if (ctx.WRITE() != null) cursorMode = letrain.vehicle.Cursor.CursorMode.DRAWING;
        else if (ctx.DEL() != null) cursorMode = letrain.vehicle.Cursor.CursorMode.ERASING;
        else if (ctx.CLEAR() != null) cursorMode = letrain.vehicle.Cursor.CursorMode.ERASING;
        
        letrain.vehicle.Cursor.CursorMode oldMode = model.getCursor().getMode();
        model.getCursor().setMode(cursorMode);
        
        try {
            if (ctx.turtleSequence() != null) {
                java.util.List<PlayerCommandsParser.TurtleStepContext> steps = ctx.turtleSequence().turtleStep();
                for (int i = 0; i < steps.size(); i++) {
                    PlayerCommandsParser.TurtleStepContext stepCtx = steps.get(i);
                    if (stepCtx.NUMBER() != null) {
                        int count = Integer.parseInt(stepCtx.NUMBER().getText());
                        for (int j = 0; j < count; j++) {
                            if (cursorMode == letrain.vehicle.Cursor.CursorMode.DRAWING) turtleDelegate.buildForward();
                            else if (cursorMode == letrain.vehicle.Cursor.CursorMode.ERASING) turtleDelegate.eraseForward();
                            else turtleDelegate.moveForward();
                        }
                    } else if (stepCtx.L() != null || stepCtx.R() != null) {
                        if (stepCtx.L() != null) turtleDelegate.turnLeft();
                        else turtleDelegate.turnRight();
                        
                        // Look ahead to see if the next token is a number. If not, auto-advance 1.
                        boolean impliesOne = false;
                        if (i == steps.size() - 1) {
                            impliesOne = true;
                        } else {
                            if (steps.get(i + 1).NUMBER() == null) {
                                impliesOne = true;
                            }
                        }
                        
                        if (impliesOne) {
                            if (cursorMode == letrain.vehicle.Cursor.CursorMode.DRAWING) turtleDelegate.buildForward();
                            else if (cursorMode == letrain.vehicle.Cursor.CursorMode.ERASING) turtleDelegate.eraseForward();
                            else turtleDelegate.moveForward();
                        }
                    }
                }
            }
        } finally {
            turtleDelegate.endSequence();
            model.getCursor().setMode(oldMode);
        }
        return null;
    }

    @Override
    public Object visitSaveCommand(PlayerCommandsParser.SaveCommandContext ctx) {
        String filename = "quicksave.json";
        if (ctx.STRING() != null) {
            String text = ctx.STRING().getText();
            filename = text.substring(1, text.length() - 1);
            if (!filename.endsWith(".json")) filename += ".json";
        }
        if (onSave != null) {
            onSave.accept(new java.io.File(filename));
        } else {
             throw new RuntimeException("Save not supported in this context.");
        }
        return null;
    }

    @Override
    public Object visitLoadCommand(PlayerCommandsParser.LoadCommandContext ctx) {
        String filename = "quicksave.json";
        if (ctx.STRING() != null) {
            String text = ctx.STRING().getText();
            filename = text.substring(1, text.length() - 1);
            if (!filename.endsWith(".json")) filename += ".json";
        }
        if (onLoad != null) {
            onLoad.accept(new java.io.File(filename));
        } else {
             throw new RuntimeException("Load not supported in this context.");
        }
        return null;
    }
}
