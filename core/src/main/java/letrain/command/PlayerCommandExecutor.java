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
    private java.util.function.BiConsumer<String, String> onMessage;
    private Runnable onQuit;
    private java.util.function.IntConsumer onUndo;
    private java.util.function.IntConsumer onRedo;
    private java.util.function.Consumer<java.io.File> onExport;
    private java.util.function.Consumer<java.io.File> onImport;

    public PlayerCommandExecutor(Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate) {
        this(model, onSave, onLoad, turtleDelegate, null, null);
    }
    
    public PlayerCommandExecutor(Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit) {
        this(model, onSave, onLoad, turtleDelegate, onMessage, onQuit, null, null);
    }

    public PlayerCommandExecutor(Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit, java.util.function.IntConsumer onUndo, java.util.function.IntConsumer onRedo) {
        this(model, onSave, onLoad, turtleDelegate, onMessage, onQuit, onUndo, onRedo, null, null);
    }

    public PlayerCommandExecutor(Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit, java.util.function.IntConsumer onUndo, java.util.function.IntConsumer onRedo, java.util.function.Consumer<java.io.File> onExport, java.util.function.Consumer<java.io.File> onImport) {
        this.model = model;
        this.onSave = onSave;
        this.onLoad = onLoad;
        this.turtleDelegate = turtleDelegate;
        this.onMessage = onMessage;
        this.onQuit = onQuit;
        this.onUndo = onUndo;
        this.onRedo = onRedo;
        this.onExport = onExport;
        this.onImport = onImport;
    }

    public PlayerCommandExecutor(Model model) {
        this(model, null, null, null);
    }

    public static String execute(String commandText, Model model) {
        return execute(commandText, model, null, null, null, null, null, null, null);
    }

    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate) {
        return execute(commandText, model, onSave, onLoad, turtleDelegate, null, null, null, null);
    }

    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit) {
        return execute(commandText, model, onSave, onLoad, turtleDelegate, onMessage, onQuit, null, null);
    }

    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit, java.util.function.IntConsumer onUndo, java.util.function.IntConsumer onRedo) {
        return execute(commandText, model, onSave, onLoad, turtleDelegate, onMessage, onQuit, onUndo,
                onRedo, true);
    }

    /**
     * @param autoRecordJournal when false, the executor does not auto-record the raw command into
     *                          the {@link CommandJournal}; callers that capture a canonical,
     *                          self-positioned form (e.g. the console funnels prepend
     *                          {@code go x,y; face d;}) do their own recording. Pass false for
     *                          internal replays (undo/redo/scenario) to avoid re-journaling them.
     */
    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit, java.util.function.IntConsumer onUndo, java.util.function.IntConsumer onRedo, boolean autoRecordJournal) {
        return execute(commandText, model, onSave, onLoad, turtleDelegate, onMessage, onQuit, onUndo,
                onRedo, null, null, autoRecordJournal);
    }

    /**
     * @param autoRecordJournal when false, the executor does not auto-record the raw command into
     *                          the {@link CommandJournal}; callers that capture a canonical,
     *                          self-positioned form (e.g. the console funnels prepend
     *                          {@code go x,y; face d;}) do their own recording. Pass false for
     *                          internal replays (undo/redo/scenario) to avoid re-journaling them.
     */
    public static String execute(String commandText, Model model, java.util.function.Consumer<java.io.File> onSave, java.util.function.Consumer<java.io.File> onLoad, letrain.command.TurtleDelegate turtleDelegate, java.util.function.BiConsumer<String, String> onMessage, Runnable onQuit, java.util.function.IntConsumer onUndo, java.util.function.IntConsumer onRedo, java.util.function.Consumer<java.io.File> onExport, java.util.function.Consumer<java.io.File> onImport, boolean autoRecordJournal) {
        if (!commandText.trim().endsWith(";")) {
            commandText = commandText.trim() + ";";
        }
        
        List<String> errors = new ArrayList<>();
        
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(commandText));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
                errors.add("Token Error at " + charPositionInLine + ": " + msg);
            }
        });

        PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
                errors.add("Syntax Error at " + charPositionInLine + ": " + msg);
            }
        });

        PlayerCommandsParser.PlayerStartContext tree = parser.playerStart();
        if (!errors.isEmpty()) {
            return String.join(" | ", errors);
        }

        try {
            PlayerCommandExecutor executor = new PlayerCommandExecutor(model, onSave, onLoad, turtleDelegate, onMessage, onQuit, onUndo, onRedo, onExport, onImport);
            executor.setAutoRecordJournal(autoRecordJournal);
            executor.visit(tree);
            if (autoRecordJournal && !executor.toggledRecording && model != null) {
                letrain.command.CommandJournal journal = model.getCommandJournal();
                if (journal != null && journal.isRecording()) {
                    journal.record(commandText.trim());
                }
            }
            return null; // No errors
        } catch (Exception e) {
            log.error("Command execution error", e);
            return e.getMessage();
        }
    }

    @Override
    public Object visitUndoCommand(PlayerCommandsParser.UndoCommandContext ctx) {
        toggledRecording = true;
        int steps = ctx.NUMBER() != null ? Integer.parseInt(ctx.NUMBER().getText()) : 1;
        if (onUndo == null) {
            throw new RuntimeException("Undo is not supported in this context (no undo handler wired).");
        }
        onUndo.accept(steps);
        return null;
    }

    @Override
    public Object visitRedoCommand(PlayerCommandsParser.RedoCommandContext ctx) {
        toggledRecording = true;
        int steps = ctx.NUMBER() != null ? Integer.parseInt(ctx.NUMBER().getText()) : 1;
        if (onRedo == null) {
            throw new RuntimeException("Redo is not supported in this context (no redo handler wired).");
        }
        onRedo.accept(steps);
        return null;
    }

    private boolean toggledRecording = false;

    /** See the static {@code execute(..., boolean autoRecordJournal)} overload. */
    private boolean autoRecordJournal = true;

    public void setAutoRecordJournal(boolean autoRecordJournal) {
        this.autoRecordJournal = autoRecordJournal;
    }

    @Override
    public Object visitJournalCommand(PlayerCommandsParser.JournalCommandContext ctx) {
        letrain.command.CommandJournal journal = model.getCommandJournal();
        StringBuilder sb = new StringBuilder();
        sb.append(journal.isRecording() ? "Recording: ON\n" : "Recording: OFF\n");
        sb.append("Entries (").append(journal.size()).append("):");
        int i = 1;
        for (String entry : journal.entries()) {
            sb.append("\n  ").append(i++).append(". ").append(entry);
        }
        String text = sb.toString();
        log.info("[journal] {}", text.replace("\n", " | "));
        if (onMessage == null) {
            return text;
        }
        onMessage.accept("Command journal", text);
        return null;
    }


    public Object visitLsCommand(PlayerCommandsParser.LsCommandContext ctx) {
        if (onMessage == null) return "Command 'ls' not supported in this context";
        StringBuilder sb = new StringBuilder();
        if (ctx.entityType().TRAIN() != null) {
            sb.append("Trains:\n");
            for (letrain.vehicle.rail.impl.Locomotive l : model.getLocomotives()) {
                sb.append(" - ").append(l.getId()).append(": ").append(l.getTrain().getName()).append("\n");
            }
        } else if (ctx.entityType().STATION() != null) {
            sb.append("Stations:\n");
            for (letrain.track.Station s : model.getStations()) {
                sb.append(" - ").append(s.getId()).append(": ").append(s.getName()).append("\n");
            }
        } else if (ctx.entityType().SENSOR() != null) {
            sb.append("Sensors:\n");
            for (letrain.track.Sensor s : model.getSensors()) {
                sb.append(" - ").append(s.getId()).append(": ").append(s.getName()).append("\n");
            }
        } else if (ctx.entityType().SEMAPHORE() != null) {
            sb.append("Semaphores:\n");
            for (letrain.track.RailSemaphore s : model.getSemaphores()) {
                sb.append(" - ").append(s.getId()).append("\n");
            }
        } else if (ctx.entityType().FORK() != null) {
            sb.append("Forks:\n");
            for (letrain.track.rail.ForkRailTrack f : model.getForks()) {
                sb.append(" - ").append(f.getId()).append("\n");
            }
        } else if (ctx.entityType().SIGNAL() != null) {
            sb.append("Speed Signals:\n");
            for (letrain.track.SpeedSignal s : model.getSpeedSignals()) {
                sb.append(" - ").append(s.getId()).append("\n");
            }
        } else {
            return "Entity type not supported for 'ls'";
        }
        onMessage.accept("List", sb.toString());
        return null;
    }

    public Object visitInfoCommand(PlayerCommandsParser.InfoCommandContext ctx) {
        if (onMessage == null) return "Command 'info' not supported in this context";
        int id = -1;
        String name = null;
        if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.identifier() != null) name = ctx.identifier().getText().replace("\"", "");

        StringBuilder sb = new StringBuilder();
        if (ctx.entityType().TRAIN() != null) {
            letrain.vehicle.rail.impl.Locomotive found = null;
            for (letrain.vehicle.rail.impl.Locomotive l : model.getLocomotives()) {
                if ((name != null && name.equals(l.getTrain().getName())) || (id != -1 && l.getId() == id)) {
                    found = l; break;
                }
            }
            if (found != null) {
                sb.append("Train ID: ").append(found.getId()).append("\n");
                sb.append("Name: ").append(found.getTrain().getName()).append("\n");
                sb.append("Speed: ").append(found.getSpeed()).append("\n");
            } else return "Train not found";
        } else if (ctx.entityType().STATION() != null) {
            letrain.track.Station found = null;
            for (letrain.track.Station s : model.getStations()) {
                if ((name != null && name.equals(s.getName())) || (id != -1 && s.getId() == id)) {
                    found = s; break;
                }
            }
            if (found != null) {
                sb.append("Station ID: ").append(found.getId()).append("\n");
                sb.append("Name: ").append(found.getName()).append("\n");
                sb.append("Position: ").append(found.getPosition()).append("\n");
            } else return "Station not found";
        } else if (ctx.entityType().SENSOR() != null) {
            letrain.track.Sensor found = null;
            for (letrain.track.Sensor s : model.getSensors()) {
                if ((name != null && name.equals(s.getName())) || (id != -1 && s.getId() == id)) {
                    found = s; break;
                }
            }
            if (found != null) {
                sb.append("Sensor ID: ").append(found.getId()).append("\n");
                sb.append("Name: ").append(found.getName()).append("\n");
                sb.append("Position: ").append(found.getPosition()).append("\n");
            } else return "Sensor not found";
        } else {
            return "Entity type not supported for 'info'";
        }
        
        onMessage.accept("Info", sb.toString());
        return null;
    }

    public Object visitSetNameCommand(PlayerCommandsParser.SetNameCommandContext ctx) {
        int id = -1;
        String name = null;
        if (ctx.NUMBER() != null) {
            id = Integer.parseInt(ctx.NUMBER().getText());
        } else if (ctx.STRING().size() > 1) {
            name = ctx.STRING(0).getText().replace("\"", "");
        }
        
        String newName = ctx.STRING(ctx.STRING().size() - 1).getText().replace("\"", "");

        if (ctx.entityType().TRAIN() != null) {
            for (letrain.vehicle.rail.impl.Locomotive l : model.getLocomotives()) {
                if ((name != null && name.equals(l.getTrain().getName())) || (id != -1 && l.getId() == id)) {
                    l.getTrain().setName(newName);
                    return null;
                }
            }
            return "Train not found";
        } else if (ctx.entityType().STATION() != null) {
            for (letrain.track.Station s : model.getStations()) {
                if ((name != null && name.equals(s.getName())) || (id != -1 && s.getId() == id)) {
                    s.setName(newName);
                    return null;
                }
            }
            return "Station not found";
        } else if (ctx.entityType().SENSOR() != null) {
            for (letrain.track.Sensor s : model.getSensors()) {
                if ((name != null && name.equals(s.getName())) || (id != -1 && s.getId() == id)) {
                    s.setName(newName);
                    return null;
                }
            }
            return "Sensor not found";
        } else if (ctx.entityType().SEMAPHORE() != null) {
            return "Semaphores cannot be renamed";
        }
        return "Entity type not supported for renaming";
    }

    public Object visitQuitCommand(PlayerCommandsParser.QuitCommandContext ctx) {
        if (onQuit != null) {
            onQuit.run();
        } else {
            System.exit(0);
        }
        return null;
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
        if (ctx.COMMA() != null) {
            int x = Integer.parseInt(ctx.NUMBER(0).getText());
            int y = Integer.parseInt(ctx.NUMBER(1).getText());
            model.getCursor().getPosition().setX(x);
            model.getCursor().getPosition().setY(y);
        } else if (ctx.entityType() != null && ctx.NEXT() == null && ctx.PREV() == null && ctx.GN() == null && ctx.GP() == null) {
            // Absolute entity Go
            int id = -1;
            String name = null;
            if (ctx.NUMBER(0) != null) id = Integer.parseInt(ctx.NUMBER(0).getText());
            if (ctx.identifier() != null) name = ctx.identifier().getText().replace("\"", "");

            letrain.map.Point targetPos = null;
            if (ctx.entityType().STATION() != null) {
                letrain.track.Station st = name != null ? model.findStationByName(name) : model.getStation(id);
                if (st != null && st.getTrack() != null) targetPos = st.getTrack().getPosition();
            } else if (ctx.entityType().SENSOR() != null) {
                letrain.track.Sensor s = name != null ? model.findSensorByName(name) : model.getSensor(id);
                if (s != null && !(s instanceof letrain.track.SpeedSignal) && !(s instanceof letrain.track.Station) && s.getTrack() != null) targetPos = s.getTrack().getPosition();
            } else if (ctx.entityType().SIGNAL() != null) {
                letrain.track.SpeedSignal s = name != null ? model.findSpeedSignalByName(name) : model.getSpeedSignal(id);
                if (s != null && s.getTrack() != null) targetPos = s.getTrack().getPosition();
            } else if (ctx.entityType().SEMAPHORE() != null) {
                letrain.track.RailSemaphore s = model.getSemaphore(id);
                if (s != null) targetPos = s.getPosition();
            } else if (ctx.entityType().FORK() != null) {
                letrain.track.rail.ForkRailTrack f = model.getFork(id);
                if (f != null) targetPos = f.getPosition();
            } else if (ctx.entityType().TRAIN() != null) {
                letrain.vehicle.rail.impl.Train t = name != null ? model.findTrainByName(name) : model.getTrainFromLocomotiveId(id);
                if (t != null && t.getDirectorLinker() != null) targetPos = ((letrain.vehicle.Vehicle<?>) t.getDirectorLinker()).getPosition();
            }

            if (targetPos != null) {
                model.getCursor().getPosition().setX(targetPos.getX());
                model.getCursor().getPosition().setY(targetPos.getY());
            } else {
                throw new RuntimeException("Target entity not found.");
            }

        } else if (ctx.MARK() != null || ctx.M() != null || (ctx.identifier() != null && ctx.entityType() == null)) {
            String name = ctx.identifier() != null ? ctx.identifier().getText().replace("\"", "") : ctx.NUMBER(0).getText();
            letrain.map.Point p = model.getMark(name);
            if (p != null) {
                model.getCursor().getPosition().setX(p.getX());
                model.getCursor().getPosition().setY(p.getY());
            } else {
                throw new RuntimeException("Mark '" + name + "' not found.");
            }
        } else if (ctx.NEXT() != null || ctx.PREV() != null || ctx.END() != null || ctx.GN() != null || ctx.GP() != null) {
            // Topological navigation
            letrain.map.Point p = model.getCursor().getPosition();
            letrain.track.Track t = model.getRailMap().getTrackAt(p.getX(), p.getY());
            if (t == null) throw new RuntimeException("Cursor is not on a track.");
            
            letrain.map.Dir searchDir = model.getCursor().getDir();
            if (ctx.PREV() != null || ctx.GP() != null) searchDir = searchDir.inverse();
            
            boolean found = false;
            while (t != null) {
                letrain.track.Track nextTrack = t.getConnected(searchDir);
                if (nextTrack == null) break;
                
                letrain.map.Dir incoming = searchDir.inverse();
                letrain.map.Dir outgoing = nextTrack.getDir(incoming);
                if (outgoing == null) break;
                
                t = nextTrack;
                searchDir = outgoing;
                
                if (ctx.END() != null) {
                    // Just keep going until the end
                    continue;
                } else if (ctx.entityType() != null) {
                    if (ctx.entityType().RAIL() != null) {
                        found = true;
                        break;
                    } else if (ctx.entityType().FORK() != null && t instanceof letrain.track.rail.ForkRailTrack) {
                        found = true;
                        break;
                    } else if (ctx.entityType().STATION() != null && t.getComponent() instanceof letrain.track.Station) {
                        found = true;
                        break;
                    } else if (ctx.entityType().SENSOR() != null && t.getComponent() instanceof letrain.track.Sensor && !(t.getComponent() instanceof letrain.track.Station) && !(t.getComponent() instanceof letrain.track.SpeedSignal)) {
                        found = true;
                        break;
                    } else if (ctx.entityType().SIGNAL() != null && t.getComponent() instanceof letrain.track.SpeedSignal) {
                        found = true;
                        break;
                    } else if (ctx.entityType().SEMAPHORE() != null && model.getSemaphoreAt(t.getPosition()) != null) {
                        found = true;
                        break;
                    } else if (ctx.entityType().TRAIN() != null && t.getLinker() != null) {
                        found = true;
                        break;
                    }
                }
            }
            
            if (ctx.END() != null) {
                model.getCursor().getPosition().setX(t.getPosition().getX());
                model.getCursor().getPosition().setY(t.getPosition().getY());
                model.getCursor().setDir(ctx.PREV() != null ? searchDir.inverse() : searchDir);
            } else if (found) {
                model.getCursor().getPosition().setX(t.getPosition().getX());
                model.getCursor().getPosition().setY(t.getPosition().getY());
                model.getCursor().setDir(ctx.PREV() != null ? searchDir.inverse() : searchDir);
            } else {
                throw new RuntimeException("Target not found along the track.");
            }
        }
        return null;
    }


    @Override
    public Object visitFaceCommand(PlayerCommandsParser.FaceCommandContext ctx) {
        if (ctx.entityType() == null && ctx.MARK() == null && ctx.M() == null) {
            letrain.map.Dir dir = null;
            if (ctx.DIR_N() != null) dir = letrain.map.Dir.N;
            else if (ctx.DIR_S() != null) dir = letrain.map.Dir.S;
            else if (ctx.DIR_E() != null) dir = letrain.map.Dir.E;
            else if (ctx.DIR_W() != null) dir = letrain.map.Dir.W;
            else if (ctx.DIR_NE() != null) dir = letrain.map.Dir.NE;
            else if (ctx.DIR_NW() != null) dir = letrain.map.Dir.NW;
            else if (ctx.DIR_SE() != null) dir = letrain.map.Dir.SE;
            else if (ctx.DIR_SW() != null) dir = letrain.map.Dir.SW;
            
            if (dir != null) {
                model.getCursor().setDir(dir);
            }
        } else if (ctx.MARK() != null || ctx.M() != null) {
            String name = ctx.identifier() != null ? ctx.identifier().getText().replace("\"", "") : ctx.NUMBER().getText();
            letrain.map.Point targetPos = model.getMark(name);
            if (targetPos != null) {
                letrain.map.Dir dir = model.getCursor().getPosition().locate(targetPos);
                if (dir != null) model.getCursor().setDir(dir);
            } else {
                throw new RuntimeException("Mark not found.");
            }
        } else {
            int id = -1;
            String name = null;
            if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
            if (ctx.identifier() != null) name = ctx.identifier().getText().replace("\"", "");

            letrain.map.Point targetPos = null;
            if (ctx.entityType().STATION() != null) {
                letrain.track.Station st = name != null ? model.findStationByName(name) : model.getStation(id);
                if (st != null && st.getTrack() != null) targetPos = st.getTrack().getPosition();
            } else if (ctx.entityType().SENSOR() != null) {
                letrain.track.Sensor s = name != null ? model.findSensorByName(name) : model.getSensor(id);
                if (s != null && !(s instanceof letrain.track.SpeedSignal) && !(s instanceof letrain.track.Station) && s.getTrack() != null) targetPos = s.getTrack().getPosition();
            } else if (ctx.entityType().SIGNAL() != null) {
                letrain.track.SpeedSignal s = name != null ? model.findSpeedSignalByName(name) : model.getSpeedSignal(id);
                if (s != null && s.getTrack() != null) targetPos = s.getTrack().getPosition();
            } else if (ctx.entityType().SEMAPHORE() != null) {
                letrain.track.RailSemaphore s = model.getSemaphore(id);
                if (s != null) targetPos = s.getPosition();
            } else if (ctx.entityType().FORK() != null) {
                letrain.track.rail.ForkRailTrack f = model.getFork(id);
                if (f != null) targetPos = f.getPosition();
            } else if (ctx.entityType().TRAIN() != null) {
                letrain.vehicle.rail.impl.Train t = name != null ? model.findTrainByName(name) : model.getTrainFromLocomotiveId(id);
                if (t != null && t.getDirectorLinker() != null) targetPos = ((letrain.vehicle.Vehicle<?>) t.getDirectorLinker()).getPosition();
            }

            if (targetPos != null) {
                letrain.map.Dir dir = model.getCursor().getPosition().locate(targetPos);
                if (dir != null) model.getCursor().setDir(dir);
            } else {
                throw new RuntimeException("Target entity not found.");
            }
        }
        return null;
    }


    
    
    @Override
    public Object visitNewCommand(PlayerCommandsParser.NewCommandContext ctx) {
        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.map.Dir dir = model.getCursor().getDir();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());

        if (ctx.STATION() != null) {
            if (track == null) throw new RuntimeException("Cannot place station: No track here.");
            if (track.getComponent() != null) throw new RuntimeException("Cannot place station: Track already has a component.");
            
            letrain.track.Station station = new letrain.track.Station(model.nextStationId());
            station.setTrack(track);
            station.setCreationDir(dir);
            station.setSideDir(dir.turnRight().turnRight());
            model.applyStationRoleByIndustry(station, pos);
            model.addStation(station);
            track.setComponent(station);
        } else if (ctx.SENSOR() != null) {
            if (track == null) throw new RuntimeException("Cannot place sensor: No track here.");
            if (track.getComponent() != null) throw new RuntimeException("Cannot place sensor: Track already has a component.");
            
            letrain.track.Sensor sensor = new letrain.track.Sensor(model.nextSensorId());
            sensor.setTrack(track);
            sensor.setCreationDir(dir);
            model.addSensor(sensor);
            track.setComponent(sensor);
        } else if (ctx.SEMAPHORE() != null) {
            if (track == null) throw new RuntimeException("Cannot place semaphore: No track here.");
            if (track.getComponent() != null) throw new RuntimeException("Cannot place semaphore: Track already has a component.");
            letrain.track.RailSemaphore sem =
                    new letrain.track.RailSemaphore(model.nextSemaphoreId());
            sem.setCreationDir(dir);
            sem.setTrack(track);
            model.addSemaphore(sem);
        } else if (ctx.SIGNAL() != null) {
            if (track == null) throw new RuntimeException("Cannot place signal: No track here.");
            if (track.getComponent() != null) throw new RuntimeException("Cannot place signal: Track already has a component.");
            
            letrain.track.SpeedSignal speedSignal = new letrain.track.SpeedSignal(model.nextSpeedSignalId(), dir, 3, true);
            speedSignal.setTrack(track);
            model.addSensor(speedSignal);
            track.setComponent(speedSignal);
        } else if (ctx.FORK() != null) {
            throw new RuntimeException("Cannot place fork via script yet (use UI).");
        } else if (ctx.LOCOMOTIVE() != null || ctx.LOCO() != null) {
            if (track == null) throw new RuntimeException("Cannot place locomotive: No track here.");
            if (track.getLinker() != null) throw new RuntimeException("Cannot place locomotive: Track already occupied.");
            
            String colorStr = ctx.color() != null ? ctx.color().getText().toUpperCase() : "RED";
            int trainId = model.nextTrainId();
            
            String aspect = ctx.aspectId().getText().replace("\"", "").toUpperCase();
            letrain.vehicle.rail.impl.Locomotive loco = new letrain.vehicle.rail.impl.Locomotive(model.nextLocomotiveId(), aspect, colorStr);

            letrain.vehicle.rail.impl.Train train = new letrain.vehicle.rail.impl.Train(trainId);
            
            train.pushBack(loco);
            train.setDirectorLinker(loco);
            loco.setTrain(train);
            
            track.enterLinkerFromDir(dir.inverse(), loco);
            if (loco.getDir() == null) {
                track.removeLinker();
                throw new RuntimeException("Could not place locomotive");
            }
            
            model.addLocomotive(loco);
            model.getEconomyManager().onLocomotiveConstructed(loco);
            train.getSafetyManager().claimOccupiedSegments();

        } else if (ctx.WAGON() != null) {
            if (track == null) throw new RuntimeException("Cannot place wagon: No track here.");
            if (track.getLinker() != null) throw new RuntimeException("Cannot place wagon: Track already occupied.");
            
            
            
            String typeStr = ctx.cargoType() != null ? ctx.cargoType().getText().toUpperCase() : "COAL";
            if (!typeStr.equals("GOLD") && !typeStr.equals("COAL") && !typeStr.equals("RUBY")) {
                throw new RuntimeException("Invalid wagon type. Only GOLD, COAL, and RUBY are allowed.");
            }
            letrain.track.CargoTypes type = letrain.track.CargoTypes.valueOf(typeStr);
            
            String aspect = ctx.aspectId().getText().replace("\"", "").toLowerCase();
            letrain.vehicle.rail.impl.Wagon wagon = new letrain.vehicle.rail.impl.Wagon(aspect);


            
            wagon.setExclusiveCargoType(type);
            track.enterLinkerFromDir(dir.inverse(), wagon);
            if (wagon.getDir() == null) {
                track.removeLinker();
                throw new RuntimeException("Could not place wagon");
            }
            model.addWagon(wagon);

        }
        return null;
    }



    @Override
    public Object visitDelCommand(PlayerCommandsParser.DelCommandContext ctx) {
        int id = -1;
        String name = null;
        if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.identifier() != null) name = ctx.identifier().getText().replace("\"", "");

        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());

        if (ctx.entityType().STATION() != null) {
            letrain.track.Station st = name != null ? model.findStationByName(name) : (id != -1 ? model.getStation(id) : (track != null && track.getComponent() instanceof letrain.track.Station ? (letrain.track.Station) track.getComponent() : null));
            if (st != null) model.removeStation(st);
            else throw new RuntimeException("Station not found.");
        } else if (ctx.entityType().SENSOR() != null || ctx.entityType().SIGNAL() != null) {
            letrain.track.Sensor s = null;
            if (ctx.entityType().SIGNAL() != null) {
                s = name != null ? model.findSpeedSignalByName(name) : (id != -1 ? model.getSpeedSignal(id) : (track != null && track.getComponent() instanceof letrain.track.SpeedSignal ? (letrain.track.Sensor) track.getComponent() : null));
            } else {
                s = name != null ? model.findSensorByName(name) : (id != -1 ? model.getSensor(id) : (track != null && track.getComponent() instanceof letrain.track.Sensor && !(track.getComponent() instanceof letrain.track.Station) ? (letrain.track.Sensor) track.getComponent() : null));
            }
            if (s != null) {
                if (ctx.entityType().SIGNAL() != null && !(s instanceof letrain.track.SpeedSignal)) throw new RuntimeException("Target is not a signal.");
                model.removeSensor(s);
            }
            else throw new RuntimeException(ctx.entityType().SIGNAL() != null ? "Signal not found." : "Sensor not found.");
        } else if (ctx.entityType().SEMAPHORE() != null) {
            letrain.track.RailSemaphore s = id != -1 ? model.getSemaphore(id) : (track != null ? (track.getComponent() instanceof letrain.track.RailSemaphore ? (letrain.track.RailSemaphore) track.getComponent() : null) : null);
            if (s != null) model.removeSemaphore(s);
            else throw new RuntimeException("Semaphore not found.");
        } else if (ctx.entityType().FORK() != null || ctx.entityType().RAIL() != null) {
            letrain.map.Point targetPos = null;
            if (ctx.entityType().FORK() != null) {
                letrain.track.rail.ForkRailTrack f = id != -1 ? model.getFork(id) : (track instanceof letrain.track.rail.ForkRailTrack ? (letrain.track.rail.ForkRailTrack) track : null);
                if (f != null) targetPos = f.getPosition();
            } else if (ctx.entityType().RAIL() != null) {
                throw new RuntimeException("Must provide specific entity type to delete, not just RAIL.");
            }
            if (targetPos != null) model.removeTrack(targetPos);
            else throw new RuntimeException("Target entity not found.");
        } else if (ctx.entityType().TRAIN() != null) {
            throw new RuntimeException("Trains cannot be deleted via the DEL command. Use the CLEAR command instead (e.g., clear train 1).");
        }
        return null;
    }

    @Override
    public Object visitClearCommand(PlayerCommandsParser.ClearCommandContext ctx) {
        int id = -1;
        String name = null;
        if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.identifier() != null) name = ctx.identifier().getText().replace("\"", "");

        if (ctx.entityType().TRAIN() != null) {
            letrain.vehicle.rail.impl.Train t = name != null ? model.findTrainByName(name) : model.getTrainFromLocomotiveId(id);
            if (t != null) {
                for (letrain.vehicle.rail.Linker l : t.getLinkers()) {
                    if (l instanceof letrain.vehicle.rail.impl.Locomotive) {
                        model.removeLocomotive((letrain.vehicle.rail.impl.Locomotive) l);
                    } else if (l instanceof letrain.vehicle.rail.impl.Wagon) {
                        model.removeWagon((letrain.vehicle.rail.impl.Wagon) l);
                    }
                    if (l.getTrack() != null) l.getTrack().removeLinker();
                }
            } else {
                throw new RuntimeException("Train not found.");
            }
        } else {
            throw new RuntimeException("CLEAR command is only for vehicles (e.g. clear train 1). Use DEL for infrastructure.");
        }
        return null;
    }

    @Override
    public Object visitSlideCommand(PlayerCommandsParser.SlideCommandContext ctx) {
        int id = -1;
        String name = null;
        if (ctx.slideTarget().NUMBER() != null) {
            id = Integer.parseInt(ctx.slideTarget().NUMBER().getText());
        } else if (ctx.slideTarget().identifier() != null) {
            name = ctx.slideTarget().identifier().getText().replace("\"", "");
        }

        boolean forward = true;
        if (ctx.FORWARD() == null && ctx.BACKWARD() != null) {
            forward = false;
        }
        int steps = 1;
        if (ctx.NUMBER() != null) {
            steps = Integer.parseInt(ctx.NUMBER().getText());
        }
        if (steps < 1) {
            throw new RuntimeException("Slide steps must be a positive number.");
        }

        letrain.track.Sensor target = null;
        if (ctx.slideTarget().STATION() != null) {
            letrain.track.Station st = name != null ? model.findStationByName(name) : model.getStation(id);
            if (st == null) throw new RuntimeException("Station not found.");
            target = st;
        } else if (ctx.slideTarget().SEMAPHORE() != null) {
            letrain.track.RailSemaphore sm = model.getSemaphore(id);
            if (sm == null) throw new RuntimeException("Semaphore not found.");
            target = sm;
        } else if (ctx.slideTarget().SIGNAL() != null) {
            letrain.track.SpeedSignal sg = name != null ? model.findSpeedSignalByName(name) : model.getSpeedSignal(id);
            if (sg == null) throw new RuntimeException("Signal not found.");
            target = sg;
        } else if (ctx.slideTarget().SENSOR() != null) {
            letrain.track.Sensor sn = name != null ? model.findSensorByName(name) : model.getSensor(id);
            if (sn == null) throw new RuntimeException("Sensor not found.");
            target = sn;
        }

        for (int i = 0; i < steps; i++) {
            boolean moved = forward ? model.moveSensorForward(target) : model.moveSensorBackward(target);
            if (!moved) {
                throw new RuntimeException((forward ? "Forward" : "Backward")
                        + " slide blocked or at the end of the line after " + i + " step(s).");
            }
        }
        return null;
    }

    @Override
    public Object visitMarkCommand(PlayerCommandsParser.MarkCommandContext ctx) {
        String name;
        if (ctx.identifier() != null) {
            name = ctx.identifier().getText().replace("\"", "");
        } else {
            name = ctx.NUMBER().getText();
        }
        model.setMark(name, model.getCursor().getPosition());
        return null;
    }

    @Override
    public Object visitTurtleCommand(PlayerCommandsParser.TurtleCommandContext ctx) {
        if (turtleDelegate == null) {
            throw new RuntimeException("Turtle graphics not supported in this context (no UI handler available).");
        }
        
        letrain.vehicle.Cursor.CursorMode cursorMode = letrain.vehicle.Cursor.CursorMode.MOVING;
        boolean isClearing = false;
        if (ctx.WRITE() != null) cursorMode = letrain.vehicle.Cursor.CursorMode.DRAWING;
        else if (ctx.DEL() != null) cursorMode = letrain.vehicle.Cursor.CursorMode.ERASING;
        else if (ctx.CLEAR() != null) {
            cursorMode = letrain.vehicle.Cursor.CursorMode.MOVING;
            isClearing = true;
        }
        
        letrain.vehicle.Cursor.CursorMode oldMode = model.getCursor().getMode();
        model.getCursor().setMode(cursorMode);
        
        try {
            turtleDelegate.startSequence();
            if (ctx.turtleSequence() != null) {
                java.util.List<PlayerCommandsParser.TurtleStepContext> steps = ctx.turtleSequence().turtleStep();
                for (int i = 0; i < steps.size(); i++) {
                    PlayerCommandsParser.TurtleStepContext stepCtx = steps.get(i);
                    if (stepCtx.NUMBER() != null) {
                        int count = Integer.parseInt(stepCtx.NUMBER().getText());
                        for (int j = 0; j < count; j++) {
                            if (isClearing) {
                                clearTrainAtCursor();
                                turtleDelegate.moveForward();
                            } else if (cursorMode == letrain.vehicle.Cursor.CursorMode.DRAWING) turtleDelegate.buildForward();
                            else if (cursorMode == letrain.vehicle.Cursor.CursorMode.ERASING) {
                                clearTrainAtCursor(); // Force clear train so track can be deleted
                                turtleDelegate.eraseForward();
                            }
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
                            if (isClearing) {
                                clearTrainAtCursor();
                                turtleDelegate.moveForward();
                            } else if (cursorMode == letrain.vehicle.Cursor.CursorMode.DRAWING) turtleDelegate.buildForward();
                            else if (cursorMode == letrain.vehicle.Cursor.CursorMode.ERASING) {
                                clearTrainAtCursor();
                                turtleDelegate.eraseForward();
                            }
                            else turtleDelegate.moveForward();
                        }
                    }
                }
            } else {
                if (isClearing) {
                    clearTrainAtCursor();
                    turtleDelegate.moveForward();
                } else if (cursorMode == letrain.vehicle.Cursor.CursorMode.DRAWING) turtleDelegate.buildForward();
                else if (cursorMode == letrain.vehicle.Cursor.CursorMode.ERASING) {
                    clearTrainAtCursor();
                    turtleDelegate.eraseForward();
                }
                else turtleDelegate.moveForward();
            }
        } finally {
            turtleDelegate.endSequence();
            model.getCursor().setMode(oldMode);
        }
        return null;
    }

    private void clearTrainAtCursor() {
        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());
        if (track != null && track.getLinker() != null) {
            letrain.vehicle.rail.Linker linker = track.getLinker();
            letrain.vehicle.rail.impl.Train train = null;
            if (linker instanceof letrain.vehicle.rail.impl.Locomotive) {
                train = ((letrain.vehicle.rail.impl.Locomotive) linker).getTrain();
            } else if (linker instanceof letrain.vehicle.rail.impl.Wagon) {
                train = ((letrain.vehicle.rail.impl.Wagon) linker).getTrain();
            }
            if (train != null && !train.getLinkers().isEmpty()) {
                for (letrain.vehicle.rail.Linker l : train.getLinkers()) {
                    if (l instanceof letrain.vehicle.rail.impl.Locomotive) {
                        model.removeLocomotive((letrain.vehicle.rail.impl.Locomotive) l);
                    } else if (l instanceof letrain.vehicle.rail.impl.Wagon) {
                        model.removeWagon((letrain.vehicle.rail.impl.Wagon) l);
                    }
                    if (l.getTrack() != null) l.getTrack().removeLinker();
                }
            } else {
                // Delete loose wagon or loco without a train (or empty train)
                if (linker instanceof letrain.vehicle.rail.impl.Locomotive) {
                    model.removeLocomotive((letrain.vehicle.rail.impl.Locomotive) linker);
                } else if (linker instanceof letrain.vehicle.rail.impl.Wagon) {
                    model.removeWagon((letrain.vehicle.rail.impl.Wagon) linker);
                }
                if (linker.getTrack() != null) linker.getTrack().removeLinker();
            }
        }
    }

    @Override
    public Object visitSaveCommand(PlayerCommandsParser.SaveCommandContext ctx) {
        String filename = "quicksave.json";
        if (ctx.identifier() != null) {
            filename = withDefaultExtension(unquote(ctx.identifier().getText()));
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
        if (ctx.identifier() != null) {
            filename = withDefaultExtension(unquote(ctx.identifier().getText()));
        }
        if (onLoad != null) {
            onLoad.accept(new java.io.File(filename));
        } else {
             throw new RuntimeException("Load not supported in this context.");
        }
        return null;
    }

    @Override
    public Object visitExportCommand(PlayerCommandsParser.ExportCommandContext ctx) {
        toggledRecording = true;
        if (model != null && model.getCommandJournal() != null
                && model.getCommandJournal().isEmpty()) {
            throw new RuntimeException(
                    "Cannot export: nothing recorded yet (toggle Record/edit mode with 'R' and edit).");
        }
        String filename = ctx.identifier() != null
                ? withScenarioExtension(unquote(ctx.identifier().getText()))
                : "scenario" + ScenarioFile.EXTENSION;
        if (onExport == null) {
            throw new RuntimeException("Export not supported in this context.");
        }
        onExport.accept(new java.io.File(filename));
        return null;
    }

    @Override
    public Object visitImportCommand(PlayerCommandsParser.ImportCommandContext ctx) {
        toggledRecording = true;
        String filename = ctx.identifier() != null
                ? withScenarioExtension(unquote(ctx.identifier().getText()))
                : "scenario" + ScenarioFile.EXTENSION;
        if (onImport == null) {
            throw new RuntimeException("Import not supported in this context.");
        }
        onImport.accept(new java.io.File(filename));
        return null;
    }

    /** Appends the scenario extension ({@code .ltr}) only when the name has none. */
    private static String withScenarioExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "scenario" + ScenarioFile.EXTENSION;
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        return name.contains(".") ? filename : filename + ScenarioFile.EXTENSION;
    }

    /**
     * Appends the default savegame extension ({@code .json}) only when the name has none, so a
     * scenario name like {@code mi-red.ltr} is respected instead of being turned into
     * {@code mi-red.ltr.json}.
     */
    private static String withDefaultExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "quicksave.json";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        return name.contains(".") ? filename : filename + ".json";
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.charAt(0) == '"'
                && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
