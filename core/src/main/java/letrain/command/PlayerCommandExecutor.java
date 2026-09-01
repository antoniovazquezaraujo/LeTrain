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

    public PlayerCommandExecutor(Model model) {
        this.model = model;
    }

    public static String execute(String commandText, Model model) {
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
            PlayerCommandExecutor executor = new PlayerCommandExecutor(model);
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
            if (track == null || track.getSensor() == null || (track.getSensor() instanceof letrain.track.Station)) {
                throw new RuntimeException("No sensor to delete here.");
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
    public Object visitModeCommand(PlayerCommandsParser.ModeCommandContext ctx) {
        if (ctx.WRITE() != null) {
            model.setMode(letrain.mvp.Model.GameMode.RAILS);
        } else if (ctx.MOVE() != null) {
            model.setMode(letrain.mvp.Model.GameMode.DRIVE);
        } else if (ctx.DEL() != null) {
            // mode del isn't really a game mode but maybe the eraser tool? 
            // In the UI erasing is done via Ctrl down. We can just fallback to RAILS.
            model.setMode(letrain.mvp.Model.GameMode.RAILS);
        }
        return null;
    }

    @Override
    public Object visitSaveCommand(PlayerCommandsParser.SaveCommandContext ctx) {
        throw new RuntimeException("Command 'save' not yet implemented in UI");
    }

    @Override
    public Object visitLoadCommand(PlayerCommandsParser.LoadCommandContext ctx) {
        throw new RuntimeException("Command 'load' not yet implemented in UI");
    }
}
