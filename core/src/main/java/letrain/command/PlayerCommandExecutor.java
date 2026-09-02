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
            if (ctx.STRING() != null) name = ctx.STRING().getText().replace("\"", "");

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

        } else if (ctx.MARK() != null || (ctx.STRING() != null && ctx.entityType() == null)) {
            String name = ctx.STRING() != null ? ctx.STRING().getText().replace("\"", "") : ctx.NUMBER(0).getText();
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
                    } else if (ctx.entityType().STATION() != null && t.getSensor() instanceof letrain.track.Station) {
                        found = true;
                        break;
                    } else if (ctx.entityType().SENSOR() != null && t.getSensor() != null && !(t.getSensor() instanceof letrain.track.Station) && !(t.getSensor() instanceof letrain.track.SpeedSignal)) {
                        found = true;
                        break;
                    } else if (ctx.entityType().SIGNAL() != null && t.getSensor() instanceof letrain.track.SpeedSignal) {
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
        if (ctx.entityType() == null) {
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
        } else {
            int id = -1;
            String name = null;
            if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
            if (ctx.STRING() != null) name = ctx.STRING().getText().replace("\"", "");

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
            
            letrain.track.SpeedSignal speedSignal = new letrain.track.SpeedSignal(model.nextSpeedSignalId(), dir, 3, true);
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
        int id = -1;
        String name = null;
        if (ctx.NUMBER() != null) id = Integer.parseInt(ctx.NUMBER().getText());
        if (ctx.STRING() != null) name = ctx.STRING().getText().replace("\"", "");

        letrain.map.Point pos = model.getCursor().getPosition();
        letrain.track.Track track = model.getRailMap().getTrackAt(pos.getX(), pos.getY());

        if (ctx.entityType().STATION() != null) {
            letrain.track.Station st = name != null ? model.findStationByName(name) : (id != -1 ? model.getStation(id) : (track != null && track.getSensor() instanceof letrain.track.Station ? (letrain.track.Station) track.getSensor() : null));
            if (st != null) model.removeStation(st);
            else throw new RuntimeException("Station not found.");
        } else if (ctx.entityType().SENSOR() != null || ctx.entityType().SIGNAL() != null) {
            letrain.track.Sensor s = null;
            if (ctx.entityType().SIGNAL() != null) {
                s = name != null ? model.findSpeedSignalByName(name) : (id != -1 ? model.getSpeedSignal(id) : (track != null && track.getSensor() instanceof letrain.track.SpeedSignal ? track.getSensor() : null));
            } else {
                s = name != null ? model.findSensorByName(name) : (id != -1 ? model.getSensor(id) : (track != null && track.getSensor() != null && !(track.getSensor() instanceof letrain.track.Station) ? track.getSensor() : null));
            }
            if (s != null) {
                if (ctx.entityType().SIGNAL() != null && !(s instanceof letrain.track.SpeedSignal)) throw new RuntimeException("Target is not a signal.");
                model.removeSensor(s);
            }
            else throw new RuntimeException(ctx.entityType().SIGNAL() != null ? "Signal not found." : "Sensor not found.");
        } else if (ctx.entityType().SEMAPHORE() != null) {
            letrain.track.RailSemaphore s = id != -1 ? model.getSemaphore(id) : (track != null ? track.getSemaphore() : null);
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
        if (ctx.STRING() != null) name = ctx.STRING().getText().replace("\"", "");

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
    public Object visitMarkCommand(PlayerCommandsParser.MarkCommandContext ctx) {
        String name;
        if (ctx.STRING() != null) {
            name = ctx.STRING().getText().replace("\"", "");
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
