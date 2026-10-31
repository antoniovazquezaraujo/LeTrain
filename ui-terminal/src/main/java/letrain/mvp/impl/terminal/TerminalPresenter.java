package letrain.mvp.impl.terminal;

import static letrain.mvp.Model.GameMode.CONNECT;
import static letrain.mvp.Model.GameMode.DRIVE;
import static letrain.mvp.Model.GameMode.FORKS;
import static letrain.mvp.Model.GameMode.LINK;
import static letrain.mvp.Model.GameMode.MENU;
import static letrain.mvp.Model.GameMode.PROGRAM;
import static letrain.mvp.Model.GameMode.RAILS;
import static letrain.mvp.Model.GameMode.SEMAPHORES;
import static letrain.mvp.Model.GameMode.STATIONS;
import static letrain.mvp.Model.GameMode.TRAINS;
import static letrain.mvp.Model.GameMode.UNLINK;

import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.ConstructionPlanList;
import letrain.construction.ConstructionProgress;
import letrain.construction.PlannedWork;
import letrain.construction.RouteConstructionQueue;
import letrain.construction.WorkQuote;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RoutePreviewSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.impl.SimulationController;
import letrain.game.audio.SoundscapeAmbience;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import letrain.visitor.terminal.InfoVisitor;
import letrain.visitor.terminal.RenderVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalPresenter implements letrain.mvp.Presenter, CoreTrainEventListener {
    private InputEvent translate(com.googlecode.lanterna.input.KeyStroke ls) {
        if (ls == null)
            return null;
        KeyType kt = KeyType.Unknown;
        try {
            kt = KeyType.valueOf(ls.getKeyType().name());
        } catch (Exception e) {
        }
        return new InputEvent(kt, ls.getCharacter(), ls.isCtrlDown(), ls.isAltDown(),
                ls.isShiftDown());
    }

    Logger log = LoggerFactory.getLogger(TerminalPresenter.class);

    Model model;
    TerminalView view;
    private final RenderVisitor renderer;
    private final InfoVisitor informer;
    boolean running;

    int forkId;
    int semaphoreId;
    int speedSignalId;
    int locomotiveId;
    int StationId;
    int sensorId;

    private long forkInputTimeout = 0;
    private long sensorInputTimeout = 0;
    private long speedSignalInputTimeout = 0;
    private long semaphoreInputTimeout = 0;
    private long stationInputTimeout = 0;
    private long locomotiveInputTimeout = 0;

    private final java.util.List<String> commandHistory = new java.util.ArrayList<>();
    private int historyIndex = -1;

    private final Map<letrain.mvp.Model.GameMode, ModeKeyHandler> modeKeyHandlers =
            new EnumMap<>(letrain.mvp.Model.GameMode.class);

    private interface ModeKeyHandler {
        void handle(InputEvent keyEvent);
    }

    RailTrackMaker railTrackMaker;
    letrain.audio.AudioController audioController;
    SimulationController simulationController;
    private final GameSaveService gameSaveService;

    /**
     * Title cover (issue #743): shown at startup, any key continues. Kept in the presenter so the
     * key that dismisses the cover is consumed and never reaches the game.
     */
    private boolean coverActive = false;
    private boolean coverEnabled = true;

    /**
     * True once a game is running. Interactive boot starts without a game (D8): the cover gives way
     * to the File menu and only New/Load start the world. Headless/scenario presenters and direct
     * launches keep the default, so they land straight in the world.
     */
    private boolean gameStarted = true;

    /**
     * Soundscape glue (styles, zones, weather), owned by the launcher; {@code null} in tests and
     * when audio is disabled, in which case the legacy {@link letrain.audio.AudioController}
     * ambient keeps playing.
     */
    private final SoundscapeAmbience ambience;

    /**
     * True while a programmatic replay (scenario import, undo/redo) executes commands. Train-event
     * sounds (link/unlink) are muted then, since the player did not perform those actions.
     */
    private boolean eventSoundsSuppressed = false;

    /**
     * Paused-editing undo/redo session (ADR-020 item 3). Created lazily on the first pause toggle;
     * survives model swaps because it is a field of the presenter, not of the model.
     */
    private letrain.command.UndoRedoHistory undoRedoHistory;

    /**
     * Editing command journal (ADR-020 item 2). Presenter-owned so it survives the model swaps that
     * undo/redo perform (the model field is transient); the model is bound to this instance.
     */
    private final letrain.command.CommandJournal commandJournal =
            new letrain.command.CommandJournal();

    /**
     * Experiment-mode session (ADR-020 item 5): snapshots the world on enter and restores it on
     * exit, so the user can try things in the live simulation without consequences.
     */
    private letrain.command.ExperimentSession experimentSession;

    /**
     * ADR-035 CONNECT session, created lazily from the current terrain/economy on first entry. It
     * is presenter-owned state: it never lives in the model and is recreated on every model swap.
     */
    private RoutePreviewSession routePreviewSession;

    /** Mode the player came from; CONNECT returns there on cancel or selection. */
    private Model.GameMode routePreviewReturnMode = RAILS;

    /**
     * True when CONNECT is being used by the planned-works list's {@code Add} action: accepting
     * stores the plan in the list instead of starting the construction queue. The normal mode menu
     * is hidden during this modal flow.
     */
    private boolean routePreviewSaveToList;

    /** Planned work being edited, or {@code -1} when CONNECT is a new route. */
    private int routePreviewEditingWorkId = -1;

    /** Whether the Add/Edit one-line status is visible; Tab toggles it during save mode. */
    private boolean routePreviewStatusVisible = true;

    /**
     * Plans kept by the phase-1 works list. Presenter-owned because it is tied to the current
     * terrain/economy and must be discarded on model swaps.
     */
    private ConstructionPlanList plannedWorks;
    private int plannedWorksSelectedIndex;

    private String plannedWorksNotice;
    private long plannedWorksNoticeUntilMillis;

    /**
     * Plan accepted in CONNECT, kept for the construction queue. The queue builds it cell by cell
     * while the simulation keeps running (ADR-037).
     */
    private RoutePlan pendingRoutePlan;

    /**
     * Buildability preflight cached for the plan currently shown in CONNECT. The rail map cannot
     * change while the author previews, so the check runs once per plan identity; a different
     * policy/endpoint plan triggers a fresh check.
     */
    private RoutePlan buildabilityPlan;
    private String buildabilityWarning;

    /** Presenter-owned construction queue; recreated on every model swap. */
    private RouteConstructionQueue constructionQueue;

    /**
     * First Esc on an active construction only arms the confirmation; the queue pauses meanwhile.
     */
    private boolean constructionCancelArmed;

    private String constructionNotice;
    private long constructionNoticeUntilMillis;

    /**
     * A/B focus toggle in PREVIEW (ADR-036). {@code false} means the next Space jumps to A and
     * {@code true} means B; every valid jump flips it. Manual navigation does not change it, so the
     * next Space resumes from the endpoint the author focused last.
     */
    private boolean routePreviewFocusOnEnd;

    private static final int AMBIENT_BASE_CELLS = 80 * 25;
    private static final int AMBIENT_FULL_CELLS = 120 * 40;

    /** Fixed simulation period, single-sourced with the game clock (50 ms at 20 TPS). */
    private static final long SIMULATION_PERIOD_NANOS =
            1_000_000_000L / letrain.time.GameClock.TICKS_PER_SECOND;

    /** Menu + help bar shown from the start so newcomers see the controls (see setHelpLevel). */
    private static final int FULL_HELP_LEVEL = 2;

    public TerminalPresenter() {
        this(null);
    }

    public TerminalPresenter(Model model) {
        this(model, null, null);
    }

    /** Production: the launcher owns the soundscape glue and passes it in (ADR-025). */
    public TerminalPresenter(Model model, SoundscapeAmbience ambience) {
        this(model, null, ambience);
    }

    /**
     * Seam for tests: when {@code view} is non-null it is used as-is instead of creating a real
     * Lanterna terminal, so tests need no tty/display and never open the Swing terminal emulator.
     * Production always passes {@code null}.
     */
    TerminalPresenter(Model model, TerminalView view) {
        this(model, view, null);
    }

    TerminalPresenter(Model model, TerminalView view, SoundscapeAmbience ambience) {
        this.ambience = ambience;
        setModel(model);
        this.view = view != null ? view : new TerminalView(this);
        wireUserMessageSink();
        renderer = new RenderVisitor(this.view);
        informer = new InfoVisitor(this.view);
        railTrackMaker = new RailTrackMaker(this);
        audioController = new letrain.audio.AudioController(this.model);
        simulationController =
                new SimulationController(this.model, audioController, railTrackMaker);
        this.gameSaveService = new GameSaveService();
        initModeKeyHandlers();
        // Start with the full menu/help visible so the controls are discoverable (Tab cycles it).
        this.model.setHelpLevel(FULL_HELP_LEVEL);
        // Start with the full-screen camera dead zone (#697): the view only scrolls when the
        // followed focus is about to leave the screen.
        this.view.setCameraDeadzone(CAMERA_DEADZONE_STEPS[cameraDeadzoneIndex]);
        this.view.setHelpLevel(FULL_HELP_LEVEL);
    }

    private Locomotive lastCreatedLoco;

    @Override
    public letrain.command.UndoRedoHistory getUndoRedoHistory() {
        if (undoRedoHistory == null) {
            undoRedoHistory = new letrain.command.UndoRedoHistory(
                    new letrain.command.UndoRedoHistory.Codec() {
                        @Override
                        public byte[] toBytes(letrain.mvp.Model m) {
                            return gameSaveService.toBytes(m);
                        }

                        @Override
                        public letrain.mvp.Model fromBytes(byte[] data) {
                            return gameSaveService.fromBytes(data);
                        }
                    });
        }
        return undoRedoHistory;
    }

    /** Experiment-mode session (ADR-020 item 5), created lazily with the same in-memory codec. */
    public letrain.command.ExperimentSession getExperimentSession() {
        if (experimentSession == null) {
            experimentSession = new letrain.command.ExperimentSession(
                    new letrain.command.ExperimentSession.Codec() {
                        @Override
                        public byte[] toBytes(letrain.mvp.Model m) {
                            return gameSaveService.toBytes(m);
                        }

                        @Override
                        public letrain.mvp.Model fromBytes(byte[] data) {
                            return gameSaveService.fromBytes(data);
                        }
                    });
        }
        return experimentSession;
    }

    /**
     * Toggles experiment mode (ADR-020 item 5). Entering turns paused editing off and snapshots the
     * live world; leaving restores that snapshot. While active the simulation runs (no journal, no
     * undo), exactly like a "try things freely" sandbox.
     */
    public void toggleExperimentMode() {
        letrain.command.ExperimentSession session = getExperimentSession();
        if (session.isActive()) {
            letrain.mvp.Model restored = session.end();
            if (restored != null) {
                applyModel(restored);
            }
            view.setStatusBarText("Experiment: OFF (state restored)");
        } else {
            if (model.isPauseEditing()) {
                model.setPauseEditing(false);
                getUndoRedoHistory().end();
            }
            session.begin(model);
            view.setStatusBarText("Experiment: ON (live simulation; X to discard and restore)");
        }
    }

    private void initModeKeyHandlers() {
        modeKeyHandlers.put(RAILS, keyEvent -> railTrackMaker.onChar(keyEvent));
        modeKeyHandlers.put(letrain.mvp.Model.GameMode.ADD, keyEvent -> handleAddModeKey(keyEvent));

        modeKeyHandlers.put(DRIVE, keyEvent -> trainDriverOnChar(keyEvent));
        modeKeyHandlers.put(FORKS, keyEvent -> handleForksModeKey(keyEvent));
        modeKeyHandlers.put(SEMAPHORES, keyEvent -> handleSemaphoresModeKey(keyEvent));
        modeKeyHandlers.put(letrain.mvp.Model.GameMode.SENSORS,
                keyEvent -> handleSensorsModeKey(keyEvent));
        modeKeyHandlers.put(letrain.mvp.Model.GameMode.SPEED_SIGNALS,
                keyEvent -> handleSpeedSignalsModeKey(keyEvent));
        modeKeyHandlers.put(TRAINS, keyEvent -> {
            if (keyEvent.getKeyType() == KeyType.Backspace) {
                deleteVehicle();
            } else if (keyEvent.getKeyType() == KeyType.Character) {
                handleTrainsModeKey(keyEvent);
            }
        });
        modeKeyHandlers.put(LINK, keyEvent -> handleLinkModeKey(keyEvent));
        modeKeyHandlers.put(UNLINK, keyEvent -> handleUnlinkModeKey(keyEvent));
        modeKeyHandlers.put(STATIONS, keyEvent -> handleStationsModeKey(keyEvent));
        modeKeyHandlers.put(PROGRAM, keyEvent -> handleProgramModeKey(keyEvent));
        modeKeyHandlers.put(MENU, keyEvent -> {
            // Allow cursor movement in NORMAL/MENU mode
            if (keyEvent.getKeyType() == KeyType.ArrowUp
                    || keyEvent.getKeyType() == KeyType.ArrowDown
                    || keyEvent.getKeyType() == KeyType.ArrowLeft
                    || keyEvent.getKeyType() == KeyType.ArrowRight) {
                railTrackMaker.onChar(keyEvent);
            } else if (keyEvent.getKeyType() == KeyType.Character) {
                Character c = keyEvent.getCharacter();
                if (c != null && (c == 'h' || c == 'j' || c == 'k' || c == 'l' || c == 'H'
                        || c == 'J' || c == 'K' || c == 'L')) {
                    railTrackMaker.onChar(keyEvent);
                }
            }
        });
        modeKeyHandlers.put(letrain.mvp.Model.GameMode.LOAD_TRAINS, keyEvent -> {
            // no-op
        });
    }

    void setModel(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new letrain.mvp.impl.Model();
        }
        resetRoutePreviewState();
        wireUserMessageSink();
        // Re-create audio controller for the new model
        if (this.audioController != null) {
            this.audioController.stop();
        }
        this.audioController = new letrain.audio.AudioController(this.model);
        this.simulationController =
                new SimulationController(this.model, audioController, railTrackMaker);

        // Register this as global listener for all present and future trains
        this.model.addCoreTrainEventListener(this);
        // Loading a savegame / starting fresh uses normal economy (scenario import re-enables it).
        this.model.getEconomyManager().setFreeConstruction(false);
        // Fresh world: no recipe yet. Bind the presenter-owned journal to the new model.
        this.commandJournal.clear();
        this.commandJournal.stopRecording();
        this.model.setCommandJournal(commandJournal);
        if (this.view != null) {
            this.view.setMenuVisible(true);
        }
    }

    /**
     * Atomically swaps the live model for {@code newModel} while keeping the presenter running, and
     * re-applies transient UI state that serialization drops (pause-editing). Used by the undo/redo
     * path (ADR-020 item 3); unlike {@link #onLoadGame} it registers the presenter listener exactly
     * once (via {@link #setModel}).
     */
    void applyModel(letrain.mvp.Model newModel) {
        if (newModel == null) {
            return;
        }
        boolean wasPaused = model.isPauseEditing();
        // Lightweight swap for paused editing: re-point the audio controller in place (no sample
        // reload / mixer restart) and rebuild only the cheap simulation controller. The view reads
        // the model field every frame.
        this.model = newModel;
        resetRoutePreviewState();
        wireUserMessageSink();
        if (this.audioController == null) {
            this.audioController = new letrain.audio.AudioController(this.model);
        } else {
            this.audioController.retarget(this.model);
        }
        // CRITICAL for deterministic undo/redo replay: RailTrackMaker keeps per-model internal
        // state
        // (oldTrack, oldGroundType, dir, makingTracks...). If we reused the instance built against
        // the outgoing model, the replay of a recorded command could "continue" from a stale rail
        // tile of the discarded world and redraw the figure misplaced / truncated. Recreate it
        // bound
        // to the restored model, exactly as GraphicPresenter.applyLoadedModel does on a load.
        this.railTrackMaker = new RailTrackMaker(this);
        this.simulationController =
                new SimulationController(this.model, audioController, railTrackMaker);
        this.model.addCoreTrainEventListener(this);
        this.model.setPauseEditing(wasPaused);
        this.model.setCommandJournal(commandJournal);
        // Recording follows the edit mode: keep capturing if we swapped while editing.
        if (wasPaused) {
            commandJournal.startRecording();
        } else {
            commandJournal.stopRecording();
        }
        letrain.map.Point focus = getActiveFocusPoint();
        if (focus != null) {
            view.centerOn(focus.getX(), focus.getY());
        }
        this.model.updateGroundMap(view.getScrollOffset(), view.getCols(), view.getRows());
        view.setMenuVisible(true);
    }

    /**
     * Points the model's DSL message sink at the visible overlay (D1). Programs, itineraries and
     * missions then report their problems where the player can see them, not only in the log.
     */
    private void wireUserMessageSink() {
        if (view != null && model != null) {
            model.setUserMessageSink((title, message) -> view.showMessage(title, message));
        }
    }

    private boolean stopped = false;

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        running = false;
        // Hand the terminal back FIRST: it must never be skipped by a later failure (e.g. audio),
        // or the shell would be left in raw mode. Each step is isolated so one cannot block
        // another.
        if (view != null) {
            try {
                view.stop();
            } catch (Exception e) {
                log.warn("Error stopping the view", e);
            }
        }
        if (audioController != null) {
            try {
                audioController.stop();
            } catch (Exception e) {
                log.warn("Error stopping audio", e);
            }
        }
    }

    /**
     * Pushes the current focus to the soundscape glue (zone weights, weather) once per tick, or
     * falls back to the legacy {@link letrain.audio.AudioController} ambient when the glue is not
     * wired (tests, audio disabled). The zoom factor from the visible cells acts as the 2D
     * listening height, mirroring the 3D camera zoom (ADR-025).
     */
    void updateAmbientAudio() {
        Point listenerPos =
                model.getEffectiveMode() == DRIVE && model.getSelectedLocomotive() != null
                        ? model.getSelectedLocomotive().getPosition()
                        : model.getCursor().getPosition();
        int ambientCells = view.getCols() * view.getRows();
        float ambientZoom = Math.max(0f, Math.min(1f, (ambientCells - AMBIENT_BASE_CELLS)
                / (float) (AMBIENT_FULL_CELLS - AMBIENT_BASE_CELLS)));
        if (ambience != null) {
            ambience.update(model, ambientZoom);
        }
        if (audioController != null) {
            audioController.setListenerPosition((float) listenerPos.getX(),
                    (float) listenerPos.getY(), 0f, 0);
            if (ambience == null) {
                audioController.updateAmbient(true, ambientZoom, (float) listenerPos.getX(),
                        (float) listenerPos.getY(), 0f);
            }
            audioController.update();
        }
    }

    /**
     * Skips the title cover. Used by headless runs and direct scenario launches that must land on
     * the world without a splash (issue #743).
     */
    public void setCoverEnabled(boolean enabled) {
        this.coverEnabled = enabled;
    }

    /** True while the title cover is on screen waiting for any key. */
    public boolean isCoverActive() {
        return coverActive;
    }

    /** Shows the cover; {@link #start()} calls this unless the cover was disabled. */
    void showCover() {
        coverActive = true;
        view.setCoverVisible(true);
    }

    /** Dismisses the cover (any key continues) and hands the screen back to the game. */
    void dismissCover() {
        coverActive = false;
        view.setCoverVisible(false);
    }

    /**
     * Interactive boot (D8): the cover leads to the File menu instead of auto-starting the world.
     * Package-visible so tests can drive the boot flow without the blocking main loop.
     */
    void beginStartup() {
        gameStarted = false;
        showCover();
    }

    /**
     * One step of the boot flow: shows the File menu. New/Load start a game; when the player
     * dismisses the menu (not Esc any more at boot, #803: Esc there quits) and still has no game,
     * the cover comes back (there is no world yet to return to). A quit request ends the flow
     * without repainting the cover. Returns true when a game was started.
     */
    boolean startupMenuStep() {
        view.showFileMenu();
        if (view.isEndOfGame(null)) {
            return false;
        }
        if (!gameStarted) {
            showCover();
            return false;
        }
        view.clear();
        return true;
    }

    public void start() {
        running = true;
        try {
            if (coverEnabled) {
                beginStartup();
            }
            model.setMode(RAILS);
            letrain.map.Point startPos = model.getCursor().getPosition();
            view.centerOn(startPos.getX(), startPos.getY());
            model.updateGroundMap(view.getScrollOffset(), view.getCols(), view.getRows());
            while (running) {
                long frameStartNanos = System.nanoTime();
                com.googlecode.lanterna.input.KeyStroke rawStroke = view.readKey();
                boolean endOfGame = view.isEndOfGame(rawStroke);
                if (coverActive) {
                    // Cover: any key continues. The dismissing keystroke is consumed with the
                    // rest of the queued burst, so it never reaches the game as an action.
                    if (rawStroke != null) {
                        dismissCover();
                        while (rawStroke != null) {
                            rawStroke = view.readKey();
                            endOfGame = endOfGame || view.isEndOfGame(rawStroke);
                        }
                    }
                    if (endOfGame) {
                        break;
                    }
                    if (coverActive) {
                        view.paint();
                    } else if (!gameStarted) {
                        // Interactive boot (D8): no auto-start. The File menu offers New/Load;
                        // Esc there returns to the cover.
                        startupMenuStep();
                    } else {
                        view.clear();
                    }
                    paceFrame(frameStartNanos);
                    continue;
                }
                // Handles the whole queued burst: the old code discarded every key after the first
                // one of the frame, so fast typing (commands) and quick taps got lost.
                while (rawStroke != null) {
                    InputEvent stroke = translate(rawStroke);
                    if (stroke != null) {
                        onChar(stroke);
                    }
                    rawStroke = view.readKey();
                    endOfGame = endOfGame || view.isEndOfGame(rawStroke);
                }
                if (endOfGame) {
                    break;
                }
                pollRoutePreview();
                tickConstruction();
                simulationController.tick();
                updateAmbientAudio();
                renderer.visitModel(model);
                informer.visitModel(model);
                drawPlannedWorksOverlay();
                drawRoutePreviewOverlay();
                drawConstructionOverlay();
                view.paint();
                if (model.getEffectiveMode() == DRIVE) {
                    Locomotive selectedLocomotive = model.getSelectedLocomotive();
                    if (selectedLocomotive != null) {
                        view.ensureVisible(selectedLocomotive.getPosition().getX(),
                                selectedLocomotive.getPosition().getY(), view.getCameraDeadzone(),
                                view.isCameraPagination());
                    }
                }

                updateTimeouts();

                paceFrame(frameStartNanos);
                view.clear();
            }
        } catch (Exception e) {
            log.error("Error in main loop", e);
        }
    }

    /**
     * Sleeps only the remainder of the fixed simulation period, so the work done in the iteration
     * (input, tick, paint) does not lengthen it. Before #480 the loop always added 50 ms on top of
     * the work, making the terminal run below 20 TPS. A frame longer than the period does not try
     * to catch up, matching the 3D loop's at-most-one-tick-per-frame rule.
     */
    private static void paceFrame(long frameStartNanos) {
        long remainingNanos = SIMULATION_PERIOD_NANOS - (System.nanoTime() - frameStartNanos);
        if (remainingNanos <= 0) {
            return;
        }
        try {
            Thread.sleep(remainingNanos / 1_000_000L, (int) (remainingNanos % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /***********************************************************
     * Presenter implementation
     **********************************************************/
    @Override
    public letrain.mvp.View getView() {
        return view;
    }

    @Override
    public letrain.mvp.Model getModel() {
        return model;
    }

    @Override
    public letrain.audio.AudioController getAudioController() {
        return audioController;
    }

    /***********************************************************
     * GameViewListener implementation
     *********************************************************
     * @param mode
     */
    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
        // Avisamos al anterior y al nuevo
    }

    // [r:Rails d:Drive f:Forks t:Trains l:Link u:Unlink

    private void executeCommand(String cmd) {
        log.info("Execute command: " + cmd);
        boolean fromConsole = model.getMode() == letrain.mvp.Model.GameMode.COMMAND;
        if (isConstructionActive() && !RoutePreviewConsolePolicy.isAllowed(cmd)) {
            // ADR-037: while the works run, the console may still navigate but must not edit the
            // world, save/load or start a new game. Keep the console open so the notice is read.
            model.setCommandError(ConstructionStatus.BLOCKED_COMMAND_MESSAGE);
            model.setCommandNotice("");
            return;
        }
        if (fromConsole && routePreviewSession != null
                && !RoutePreviewConsolePolicy.isAllowed(cmd)) {
            // ADR-036: while previewing, the console only navigates; the world, the route and A/B
            // must survive until the author selects or cancels. Keep the console open so the
            // notice stays readable.
            model.setCommandError(RoutePreviewConsolePolicy.BLOCKED_MESSAGE);
            model.setCommandNotice("");
            return;
        }
        letrain.mvp.Model.GameMode returnMode = model.getPreviousMode();
        // Capture the cursor BEFORE executing so the journaled copy is self-positioned and the
        // replay (undo) is deterministic regardless of any (unrecorded) keyboard navigation.
        String prefix = cursorPrefix();
        String error = letrain.command.PlayerCommandExecutor.execute(cmd, model,
                file -> onSaveGame(file), file -> onLoadGame(file),
                new letrain.command.TurtleBuilder(model, railTrackMaker),
                (title, msg) -> view.showMessage(title, msg), () -> onExitGame(),
                steps -> undo(steps), steps -> redo(steps), file -> saveScenario(file),
                file -> playScenario(file), () -> onNewGame(), false);

        if (error != null) {
            // Short form for the one-line command bar; the full message goes to the scrollable
            // panel.
            model.setCommandError(letrain.command.SyntaxMessages.shorten(error));
            if (letrain.command.SyntaxMessages.needsPanel(error)) {
                view.showMessage("Command error", error);
            }
            return;
        }
        // Command journal (ADR-020 item 2): record the canonical, self-positioned form so an
        // exported scenario replays at the right place. Only editing commands, and only while the
        // world is frozen in edit mode (same gate as undo, so both stay 1:1).
        letrain.command.CommandJournal journal = model.getCommandJournal();
        if (model.isSimulationPaused() && journal.isRecording()
                && !letrain.command.EditCommandFilter.isNonRecordable(cmd)) {
            journal.record(prefix + cmd);
        }
        // Auto-capture in pause (ADR-020 item 3): while pause-editing freezes the world, every
        // successful editing command is journaled for undo/redo.
        letrain.command.UndoRedoHistory history = getUndoRedoHistory();
        if (model.isSimulationPaused() && !letrain.command.EditCommandFilter.isNonRecordable(cmd)) {
            history.record(prefix + cmd);
        }
        if (fromConsole && model.getMode() == letrain.mvp.Model.GameMode.COMMAND) {
            // D1 contextual channel: with a console notice the command bar stays open so the
            // player can read it (the input is cleared below); a silent success returns to the
            // mode the player was in.
            String notice = model.getCommandNotice();
            if (notice == null || notice.isEmpty()) {
                model.setMode(returnMode);
            }
        } else if (!fromConsole) {
            // The '.' repeat path keeps the old behaviour of landing in RAILS.
            model.setMode(letrain.mvp.Model.GameMode.RAILS);
            String notice = model.getCommandNotice();
            if (notice != null && !notice.isEmpty()) {
                // Outside COMMAND mode no command bar is painted: surface the notice in the
                // status bar.
                view.setStatusBarText(notice);
            }
        }
        model.setCommandText("");
        model.setCommandError("");
        // In DRIVE the view keeps following the locomotive; centring on the cursor would yank it.
        if (model.getEffectiveMode() != letrain.mvp.Model.GameMode.DRIVE) {
            view.centerOn(model.getCursor().getPosition().getX(),
                    model.getCursor().getPosition().getY());
        }
    }

    /**
     * Leaving the console (Esc or an empty Enter) returns to the mode the player was in before
     * opening it, instead of always landing in RAILS.
     */
    private void leaveCommandMode() {
        model.setMode(model.getPreviousMode());
        model.setCommandText("");
        model.setCommandError("");
        model.setCommandNotice("");
    }

    /** Absolute cursor prefix: {@code "go x,y; face d; "} from the current cursor state. */
    private String cursorPrefix() {
        letrain.map.Point pos = model.getCursor().getPosition();
        return "go " + pos.getX() + "," + pos.getY() + "; face "
                + model.getCursor().getDir().name().toLowerCase() + "; ";
    }

    /**
     * Records a keyboard state toggle (signal invert/mode/limit) as a canonical, self-positioned
     * DSL command into the journal and the undo history, exactly like the console funnel. Only
     * while the world is frozen in edit mode, so undo and the exported scenario stay 1:1 with the
     * edits.
     */
    private void recordKeyboardEdit(String action) {
        if (!model.isSimulationPaused()) {
            return;
        }
        String text = cursorPrefix() + action + ";";
        if (commandJournal.isRecording()) {
            commandJournal.recordCoalescing(text);
        }
        letrain.command.UndoRedoHistory history = getUndoRedoHistory();
        if (history != null) {
            history.recordCoalescing(text);
        }
    }

    /** Undoes {@code steps} editing commands (ADR-020 item 3). */
    public void undo(int steps) {
        if (!model.isSimulationPaused()) {
            view.setStatusBarText("Undo needs the Record/edit mode (R)");
            return;
        }
        letrain.command.UndoRedoHistory history = getUndoRedoHistory();
        letrain.command.UndoRedoHistory.UndoPlan plan = history.planUndo(steps);
        if (plan == null) {
            view.setStatusBarText("Nothing to undo");
            return;
        }
        applyPlan(history, plan, true);
    }

    /** Redoes {@code steps} editing commands (ADR-020 item 3). */
    public void redo(int steps) {
        if (!model.isSimulationPaused()) {
            view.setStatusBarText("Redo needs the Record/edit mode (R)");
            return;
        }
        letrain.command.UndoRedoHistory history = getUndoRedoHistory();
        letrain.command.UndoRedoHistory.UndoPlan plan = history.planRedo(steps);
        if (plan == null) {
            view.setStatusBarText("Nothing to redo");
            return;
        }
        applyPlan(history, plan, false);
    }

    /**
     * Runs {@code action} with train-event sounds muted. Used by programmatic replays (scenario
     * import, undo/redo): the replayed commands must not play the sounds of the user actions they
     * represent (e.g. the coupling "link" sound when an exported linked train is imported).
     */
    void runWithoutEventSounds(Runnable action) {
        boolean previous = eventSoundsSuppressed;
        eventSoundsSuppressed = true;
        try {
            action.run();
        } finally {
            eventSoundsSuppressed = previous;
        }
    }

    /**
     * Applies an undo/redo plan: restore the checkpoint base (undo) via {@link #applyModel}, then
     * re-execute the recorded command slice on the restored world through the same console path. On
     * a replay error the applied counter is only advanced to the last command that actually
     * succeeded, so the history never claims commands were applied when they were not (a desync
     * here makes the next redo incomplete).
     */
    private void applyPlan(letrain.command.UndoRedoHistory history,
            letrain.command.UndoRedoHistory.UndoPlan plan, boolean restoring) {
        log.info("[undoredo] {} from={} to={} target={} appliedBefore={} size={}",
                restoring ? "UNDO" : "REDO", plan.fromIndex(), plan.toIndex(), plan.target(),
                history.applied(), history.size());
        if (restoring) {
            letrain.mvp.Model restored = history.restore(plan);
            if (restored == null) {
                view.setStatusBarText("Undo failed: could not restore checkpoint");
                return;
            }
            applyModel(restored);
            history.bind(model);
        }
        // Re-seed the fresh maker so a slice that begins mid-gesture can continue the rail it
        // would have chained from before the slice. The maker's chaining state (oldTrack) is
        // transient UI state that a checkpoint restore loses: without it the first replayed piece
        // starts disconnected (or with the wrong entry direction) and the undo breaks rails that
        // should survive. See UndoRedoHistory.resumeFrom / RailTrackMaker.resumeChainFrom.
        letrain.map.Point resumeOrigin = history.resumeFrom(plan.fromIndex());
        if (resumeOrigin != null) {
            letrain.track.Track predecessor =
                    model.getRailMap().getTrackAt(resumeOrigin.getX(), resumeOrigin.getY());
            if (predecessor != null) {
                railTrackMaker.resumeChainFrom(predecessor);
            }
        }
        int[] progress = {plan.fromIndex(), 0};
        runWithoutEventSounds(() -> {
            for (String cmd : plan.commandsToReplay(history.entries())) {
                String error = letrain.command.PlayerCommandExecutor.execute(cmd, model,
                        file -> onSaveGame(file), file -> onLoadGame(file),
                        new letrain.command.TurtleBuilder(model, railTrackMaker),
                        (title, msg) -> view.showMessage(title, msg), () -> onExitGame(), null,
                        null, false);
                if (error != null) {
                    log.error("Undo/redo replay failed on '{}': {}", cmd, error);
                    view.setStatusBarText("Replay error: " + error);
                    break;
                }
                // Reproduce the terrain materialization the live session performed between edits
                // (the
                // render loop generates ground blocks around the cursor each frame). Replay runs
                // with
                // no frames, so without this, painting beyond the blocks stored in the checkpoint
                // would
                // hit void (-1) terrain and silently fail to lay rails.
                letrain.map.Point cp = model.getCursor().getPosition();
                int radius = model.getEconomyManager().getViewRadius();
                model.getGroundMap().renderBlock(cp.getX() - radius, cp.getY() - radius,
                        radius * 2 + 1, radius * 2 + 1);
                progress[0]++;
                progress[1]++;
            }
        });
        int replayedTo = progress[0];
        int count = progress[1];
        log.info("[undoredo] replayed={} replayedTo={} committing", count, replayedTo);
        // Commit only up to the last command that really applied, so the history stays in sync with
        // the live world (never claim commands were applied when a replay broke half-way).
        history.commit(replayedTo);
        // Keep the export journal cursor in sync with the applied edits of this session.
        commandJournal.setApplied(commandJournal.base() + replayedTo);
        view.setStatusBarText((restoring ? "Undid" : "Redid") + " " + plan.toIndex() + " steps");
        view.centerOn(model.getCursor().getPosition().getX(),
                model.getCursor().getPosition().getY());
    }

    /**
     * Resets all CONNECT state. Called whenever the model is swapped (load, new game, undo/redo),
     * because a session is bound to one terrain/economy and must never survive into another world.
     */
    private void resetRoutePreviewState() {
        routePreviewSession = null;
        routePreviewReturnMode = RAILS;
        routePreviewSaveToList = false;
        routePreviewEditingWorkId = -1;
        routePreviewStatusVisible = true;
        pendingRoutePlan = null;
        routePreviewFocusOnEnd = false;
        clearBuildabilityCache();
        constructionQueue = null;
        constructionCancelArmed = false;
        constructionNotice = null;
        constructionNoticeUntilMillis = 0;
        plannedWorks = null;
        plannedWorksSelectedIndex = -1;
        plannedWorksNotice = null;
        plannedWorksNoticeUntilMillis = 0;
    }

    private RoutePreviewSession routePreviewSession() {
        if (routePreviewSession == null) {
            routePreviewSession =
                    new RoutePreviewSession(model.getGroundMap(), model.getEconomyManager());
        }
        return routePreviewSession;
    }

    /** {@code Shift+C}: uppercase C, or a lower-case c delivered with the shift flag. */
    private static boolean isConnectEntry(InputEvent event) {
        if (event.getKeyType() != KeyType.Character || event.getCharacter() == null) {
            return false;
        }
        char c = event.getCharacter();
        return c == 'C' || (c == 'c' && event.isShiftDown());
    }

    /**
     * CONNECT is a normal map/edit tool; it is not offered from the console, editor or TRAINS/ADD.
     */
    private static boolean canEnterConnectMode(Model.GameMode mode) {
        switch (mode) {
            case COMMAND:
            case PROGRAM:
            case TRAINS:
            case ADD:
            case CONNECT:
                return false;
            default:
                return true;
        }
    }

    private void enterConnectMode() {
        enterConnectMode(false);
    }

    /**
     * Opens CONNECT. In save mode the resulting plan is added to the planned-works list instead of
     * starting construction, and the normal mode menu is hidden while the modal planning task runs
     * (phase 1 roadmap).
     */
    private void enterConnectMode(boolean saveToList) {
        routePreviewReturnMode = model.getMode();
        routePreviewSaveToList = saveToList;
        routePreviewEditingWorkId = -1;
        routePreviewStatusVisible = true;
        clearBuildabilityCache();
        routePreviewSession().begin(model.getCursor().getPosition());
        model.setMode(CONNECT);
        routePreviewFocusOnEnd = false;
        view.setMenuVisible(!saveToList);
    }

    private void leaveConnectMode() {
        Model.GameMode returnMode =
                routePreviewReturnMode == CONNECT ? RAILS : routePreviewReturnMode;
        model.setMode(returnMode);
        routePreviewSession = null;
        routePreviewFocusOnEnd = false;
        routePreviewSaveToList = false;
        routePreviewEditingWorkId = -1;
        routePreviewStatusVisible = true;
        clearBuildabilityCache();
        view.setMenuVisible(true);
    }

    /**
     * Preflights the current feasible plan once per plan identity. The rail map is static during
     * CONNECT, so switching back and forth between cached plans reuses the result. Returns
     * {@code null} when the plan is buildable or when it is infeasible (the planner reason wins).
     */
    private String buildabilityWarning(RoutePlan plan) {
        if (plan == null || !plan.feasible()) {
            return null;
        }
        if (plan != buildabilityPlan) {
            buildabilityPlan = plan;
            buildabilityWarning = RailBuildability.check(model, plan)
                    .map(RailBuildability.Issue::description).orElse(null);
        }
        return buildabilityWarning;
    }

    private void clearBuildabilityCache() {
        buildabilityPlan = null;
        buildabilityWarning = null;
    }

    /**
     * Handles every key while CONNECT is active, before the global Enter/Esc/mode-hotkey routing
     * (ADR-036).
     *
     * <p>
     * Route keys are unique and come first in {@code PREVIEW}: {@code [}/{@code ]} switch policy,
     * {@code Enter} selects and {@code Esc}/{@code Shift+C} cancel. In {@code PLANNING} only cancel
     * keys are accepted. Everything else in {@code PREVIEW} is navigation and goes through the
     * sanitized path, so movement never builds or deletes track even when Shift/Ctrl are held.
     */
    private void handleConnectKey(InputEvent keyEvent) {
        RoutePreviewSession session = routePreviewSession();
        RoutePreviewSession.State state = session.state();
        if (keyEvent.getKeyType() == KeyType.Escape) {
            boolean wasSave = routePreviewSaveToList;
            session.escape();
            leaveConnectMode();
            if (wasSave) {
                showPlannedWorksNotice("Route planning cancelled · nothing added");
            }
            return;
        }
        if (isConnectEntry(keyEvent)) {
            boolean wasSave = routePreviewSaveToList;
            session.escape();
            leaveConnectMode();
            if (wasSave) {
                showPlannedWorksNotice("Route planning cancelled · nothing added");
            }
            return;
        }
        if (keyEvent.getKeyType() == KeyType.Tab && routePreviewSaveToList) {
            routePreviewStatusVisible = !routePreviewStatusVisible;
            return;
        }
        if (state == RoutePreviewSession.State.PLANNING) {
            // The worker owns the plan; only Esc / Shift+C (handled above) can interrupt it.
            return;
        }
        if (keyEvent.getKeyType() == KeyType.Enter) {
            if (!routePreviewSaveToList && state == RoutePreviewSession.State.PREVIEW
                    && buildabilityWarning(session.lastPlan().orElse(null)) != null) {
                // Keep the author in PREVIEW: selecting would only queue a job the preflight
                // rejects. In Add/save mode the plan is only a budget, so it can be stored with a
                // warning and fixed later.
                return;
            }
            RoutePreviewSession.State next = session.enter(model.getCursor().getPosition());
            if (next == RoutePreviewSession.State.SELECTED) {
                if (routePreviewSaveToList) {
                    if (routePreviewEditingWorkId >= 0) {
                        updateSelectedPlanInWorks(session);
                    } else {
                        addSelectedPlanToWorks(session);
                    }
                    leaveConnectMode();
                    return;
                }
                pendingRoutePlan = session.selectedPlan().orElse(null);
                leaveConnectMode();
                if (pendingRoutePlan != null) {
                    startConstruction(pendingRoutePlan);
                }
            }
            return;
        }
        if (state == RoutePreviewSession.State.PREVIEW) {
            if (isPlainCharacter(keyEvent, ' ')) {
                focusNextRouteEndpoint(session);
                return;
            }
            if (isPlainCharacter(keyEvent, '[')) {
                session.previousPolicy();
                return;
            }
            if (isPlainCharacter(keyEvent, ']')) {
                session.nextPolicy();
                return;
            }
            if (isPlainCharacter(keyEvent, ':')) {
                openCommandFromRoutePreview();
                return;
            }
            if (isPlainCharacter(keyEvent, 'z')) {
                cycleCameraDeadzone();
                return;
            }
            if (isPlainCharacter(keyEvent, 'Z')) {
                view.setCameraPagination(!view.isCameraPagination());
                view.flashCameraDeadzone();
                return;
            }
        }
        InputEvent movement = movementOnly(keyEvent);
        if (movement != null) {
            railTrackMaker.onChar(movement);
        }
    }

    /**
     * Jumps to whichever endpoint the toggle points at (A first, then B, repeating) and centres the
     * view on it. The route, endpoints and policy are untouched: this is only a camera/cursor
     * shortcut for getting back after panning or zooming around. Outside PREVIEW (or when an
     * endpoint is still unknown) it does nothing.
     */
    private void focusNextRouteEndpoint(RoutePreviewSession session) {
        Point target =
                routePreviewFocusOnEnd ? session.end().orElse(null) : session.start().orElse(null);
        if (target == null) {
            return;
        }
        model.getCursor().setPosition(target);
        view.centerOn(target.getX(), target.getY());
        routePreviewFocusOnEnd = !routePreviewFocusOnEnd;
    }

    /**
     * Opens the console without dropping the route preview. {@code Model.setMode} remembers
     * {@code CONNECT} as the previous mode, so leaving the console returns to {@code CHOOSING}
     * (represented by {@code PREVIEW}).
     */
    private void openCommandFromRoutePreview() {
        model.setMode(letrain.mvp.Model.GameMode.COMMAND);
        model.setCommandText("");
        model.setCommandError("");
        model.setCommandNotice("");
    }

    /**
     * True for an unmodified character key (Ctrl/Alt combinations are left alone). Used for the
     * route-preview keys, which must not clash with navigation or console shortcuts.
     */
    private static boolean isPlainCharacter(InputEvent event, char expected) {
        return event.getKeyType() == KeyType.Character && event.getCharacter() != null
                && event.getCharacter() == expected && !event.isCtrlDown() && !event.isAltDown();
    }

    /**
     * Converts arrows/hjkl/Page keys into a movement event. Arrows and letters are stripped of all
     * modifiers so Shift/Ctrl cannot turn navigation into drawing, erasing or paging the wrong way.
     * Page keys keep Ctrl because the existing map navigation uses it for horizontal paging.
     *
     * <p>
     * Any other key returns {@code null} and is swallowed in CONNECT, so no construction or mode
     * shortcut leaks through.
     */
    private static InputEvent movementOnly(InputEvent event) {
        KeyType type = event.getKeyType();
        if (type == KeyType.PageUp || type == KeyType.PageDown) {
            return new InputEvent(type, null, event.isCtrlDown(), false, false);
        }
        if (type == KeyType.ArrowUp || type == KeyType.ArrowDown || type == KeyType.ArrowLeft
                || type == KeyType.ArrowRight) {
            return new InputEvent(type, null, false, false, false);
        }
        if (type == KeyType.Character && event.getCharacter() != null) {
            char c = Character.toLowerCase(event.getCharacter());
            KeyType direction = switch (c) {
                case 'k' -> KeyType.ArrowUp;
                case 'j' -> KeyType.ArrowDown;
                case 'h' -> KeyType.ArrowLeft;
                case 'l' -> KeyType.ArrowRight;
                default -> null;
            };
            if (direction != null) {
                return new InputEvent(direction, null, false, false, false);
            }
        }
        return null;
    }

    /**
     * Drives the asynchronous preview state machine from the UI thread once per frame (ADR-036).
     * Outside CONNECT it does nothing: a session created for a preview never runs while another
     * mode owns the screen.
     */
    void pollRoutePreview() {
        if (routePreviewSession != null && model.getMode() == CONNECT) {
            routePreviewSession.poll();
        }
    }

    /**
     * Presenter-owned list of phase-1 planned works. Created lazily so loading a game starts with
     * an empty list and a model swap discards plans bound to the old terrain.
     */
    ConstructionPlanList plannedWorks() {
        if (plannedWorks == null) {
            plannedWorks = new ConstructionPlanList();
        }
        return plannedWorks;
    }

    /**
     * Adds the plan accepted in CONNECT to the list. The caller must only invoke this while
     * {@link #routePreviewSaveToList} is true; construction is deliberately not started.
     */
    private void addSelectedPlanToWorks(RoutePreviewSession session) {
        RoutePlan plan = session.selectedPlan().orElse(null);
        Point start = session.start().orElse(null);
        Point end = session.end().orElse(null);
        if (plan == null || !plan.feasible() || start == null || end == null) {
            showPlannedWorksNotice("Route could not be added to planning");
            return;
        }
        PlannedWork work = plannedWorks().add(start, end, session.policy(), plan,
                session.effectiveConstructionTicks());
        model.setMark(wayMarkName(work.id(), "start"), start);
        model.setMark(wayMarkName(work.id(), "end"), end);
        plannedWorksSelectedIndex = plannedWorks().size() - 1;
        showPlannedWorksNotice("Route #" + work.id() + " added to planning · b opens the list");
    }

    /**
     * Updates policy/plan/cost/time of the work being edited; endpoints and marks stay unchanged.
     */
    private void updateSelectedPlanInWorks(RoutePreviewSession session) {
        RoutePlan plan = session.selectedPlan().orElse(null);
        if (plan == null || !plan.feasible()) {
            showPlannedWorksNotice("Route could not be updated");
            return;
        }
        plannedWorks().find(routePreviewEditingWorkId)
                .flatMap(work -> plannedWorks().replacePlan(work.id(), session.policy(), plan,
                        session.effectiveConstructionTicks()))
                .ifPresentOrElse(work -> showPlannedWorksNotice("Route #" + work.id() + " updated"),
                        () -> showPlannedWorksNotice("Route could not be updated"));
    }

    /**
     * Opens an existing planned work in CONNECT. The endpoints are fixed: only the policy can be
     * changed, exactly like Add but starting from the saved plan. Accepting updates the row; the
     * author must create a new work to change origin or destination.
     */
    private void startEditingPlannedWork(PlannedWork work) {
        routePreviewReturnMode = model.getMode();
        routePreviewSaveToList = true;
        routePreviewEditingWorkId = work.id();
        routePreviewStatusVisible = true;
        clearBuildabilityCache();
        routePreviewSession().beginEdit(work.start(), work.end(), work.policy(), work.plan());
        model.setMode(CONNECT);
        routePreviewFocusOnEnd = false;
        view.setMenuVisible(false);
    }

    /**
     * Opens the modal planned-works list and applies its actions. The list is a transient
     * {@link Model.GameMode#PLANNED_WORKS} mode: the previous mode is restored on close, and before
     * Add/Edit hand control to CONNECT so the preview returns to the original map mode. Delete
     * keeps the list loop alive. The dialog never reopens itself after Add, by owner decision: the
     * player opens it again when they want.
     */
    void openPlannedWorks() {
        if (isConstructionActive()) {
            view.showMessage("Planning", "Finish or cancel the active construction first");
            return;
        }
        Model.GameMode returnMode = model.getMode();
        model.setMode(Model.GameMode.PLANNED_WORKS);
        try {
            while (true) {
                List<PlannedWork> works = plannedWorks().works();
                ConstructionPlansDialog.Result result = view.showConstructionPlansDialog(
                        plannedWorkRows(works), plannedWorksSelectedIndex, plannedWorksTotalLine());
                if (result == null) {
                    return;
                }
                plannedWorksSelectedIndex =
                        ConstructionPlansDialog.clampIndex(result.selectedIndex(), works.size());
                int selectedId =
                        plannedWorksSelectedIndex >= 0 && plannedWorksSelectedIndex < works.size()
                                ? works.get(plannedWorksSelectedIndex).id()
                                : -1;
                switch (result.action()) {
                    case ADD -> {
                        restorePlannedWorksMode(returnMode);
                        enterConnectMode(true);
                        return;
                    }
                    case EDIT -> {
                        if (selectedId >= 0) {
                            java.util.Optional<PlannedWork> work = plannedWorks().find(selectedId);
                            if (work.isPresent()) {
                                restorePlannedWorksMode(returnMode);
                                startEditingPlannedWork(work.get());
                                return;
                            }
                        }
                    }
                    case QUOTES -> {
                        openQuotesFor(selectedId);
                    }
                    case DELETE -> {
                        if (selectedId >= 0) {
                            plannedWorks().remove(selectedId);
                            plannedWorksSelectedIndex = plannedWorks().isEmpty() ? -1
                                    : Math.min(plannedWorksSelectedIndex,
                                            plannedWorks().size() - 1);
                        }
                    }
                    case CLOSE -> {
                        return;
                    }
                }
            }
        } finally {
            restorePlannedWorksMode(returnMode);
        }
    }

    /** Leaves the transient Planning mode without disturbing a mode chosen by Add/Edit. */
    private void restorePlannedWorksMode(Model.GameMode returnMode) {
        if (model.getMode() == Model.GameMode.PLANNED_WORKS) {
            model.setMode(returnMode);
        }
    }

    /** Shows companies for the selected work and stores the chosen feasible quote. */
    private void openQuotesFor(int id) {
        if (id < 0) {
            return;
        }
        plannedWorks().find(id).ifPresent(work -> {
            List<WorkQuote> quotes = ConstructionCompanyCatalog.all().stream()
                    .map(company -> WorkQuote.quote(company, work.plan())).toList();
            int current = work.quote().map(quote -> indexOfCompany(quote.company().id())).orElse(0);
            QuotesDialog.Result result = view.showQuotesDialog(quotes, current);
            if (result != null && result.action() == QuotesDialog.Action.SELECT
                    && result.quote() != null && result.quote().feasible()) {
                plannedWorks().replaceQuote(work.id(), result.quote());
            }
        });
    }

    private static int indexOfCompany(String companyId) {
        for (int index = 0; index < ConstructionCompanyCatalog.all().size(); index++) {
            if (ConstructionCompanyCatalog.all().get(index).id().equals(companyId)) {
                return index;
            }
        }
        return 0;
    }

    /** Builds the display rows for the modal list; formatting stays in the UI layer. */
    private List<ConstructionPlansDialog.Row> plannedWorkRows(List<PlannedWork> works) {
        return works.stream().map(work -> {
            String warning = RailBuildability.check(model, work.plan())
                    .map(RailBuildability.Issue::description).orElse("");
            String company = work.quote().map(quote -> quote.company().name()).orElse("-");
            String quality = work.quote().map(quote -> qualityLabel(quote)).orElse("-");
            String cost = work.quote()
                    .map(quote -> RoutePreviewStatus.formatCost(quote.totalCost())).orElse("-");
            String time = work.quote()
                    .map(quote -> RoutePreviewStatus.formatTicks(quote.totalTicks())).orElse("-");
            return new ConstructionPlansDialog.Row(work.id(), "Draft", formatPoint(work.start()),
                    formatPoint(work.end()), shortPolicy(work.policy()), company, quality,
                    work.cells(), cost, time, warning);
        }).toList();
    }

    private static String qualityLabel(WorkQuote quote) {
        return switch (quote.quality()) {
            case LOW -> "Low";
            case HIGH -> "High";
        };
    }

    /** Exact total line from the plans, not from the rounded display strings. */
    private String plannedWorksTotalLine() {
        ConstructionPlanList list = plannedWorks();
        if (list.quotedCount() == 0) {
            return ConstructionPlansDialog.totalLine(list.size(), "-", "-");
        }
        return ConstructionPlansDialog.totalLine(list.size(),
                RoutePreviewStatus.formatCost(list.totalQuotedCost()),
                RoutePreviewStatus.formatTicks(list.totalQuotedTicks()));
    }

    private static String formatPoint(Point point) {
        return point.getX() + "," + point.getY();
    }

    /** Stable mark name for one endpoint of a planned work, e.g. {@code Way-24-end}. */
    static String wayMarkName(int workId, String endpoint) {
        return "Way-" + workId + "-" + endpoint;
    }

    /** Short labels fitted to the phase-1 table; the underlying policy is never lost in the row. */
    private static String shortPolicy(RoutePolicy policy) {
        return switch (policy) {
            case CHEAPEST -> "CHEAPEST";
            case DIRECT -> "DIRECT";
            case AVOID_MOUNTAINS -> "AVOID_MTN";
            case AVOID_WATER -> "AVOID_WATER";
            case AVOID_ALL -> "AVOID_ALL";
        };
    }

    private void showPlannedWorksNotice(String notice) {
        plannedWorksNotice = notice;
        plannedWorksNoticeUntilMillis = System.currentTimeMillis() + 2_500;
        constructionNotice = null;
    }

    /** True for the phase-1 works-list key ({@code b}); no modifier is accepted. */
    private static boolean isPlannedWorksEntry(InputEvent event) {
        return event.getKeyType() == KeyType.Character && event.getCharacter() != null
                && (event.getCharacter() == 'b' || event.getCharacter() == 'B')
                && !event.isCtrlDown() && !event.isAltDown();
    }

    /** The list is a map tool; it is not offered in modal/console/editor modes. */
    private static boolean canOpenPlannedWorks(Model.GameMode mode) {
        return mode == RAILS || mode == MENU;
    }

    /**
     * Starts the construction queue for an accepted plan (ADR-037). Preflight runs immediately; a
     * rejection is surfaced as a notice and leaves no job building. A terminal state also unlocks
     * the editing guard.
     */
    boolean startConstruction(RoutePlan plan) {
        if (plan == null) {
            return false;
        }
        RouteConstructionQueue queue = constructionQueue();
        boolean started = queue.start(plan, new RailTrackMaterializer(this));
        constructionCancelArmed = false;
        ConstructionProgress progress = queue.progress();
        if (!started) {
            showConstructionNotice(ConstructionStatus.rejected("another construction is running"));
            return false;
        }
        if (progress.state() == letrain.construction.ConstructionState.REJECTED) {
            showConstructionNotice(ConstructionStatus
                    .rejected(progress.reasonOptional().orElse("route cannot be built")));
            return false;
        }
        if (progress.state() == letrain.construction.ConstructionState.DONE) {
            // Empty feasible plan: nothing to lay, but the job is complete.
            showConstructionNotice(ConstructionStatus.COMPLETE);
            return false;
        }
        constructionNotice = null;
        constructionNoticeUntilMillis = 0;
        return true;
    }

    /**
     * Advances the active construction once per frame. While the cancel confirmation is armed the
     * queue is paused: only the second Esc cancels, any other key resumes on the next frame.
     */
    void tickConstruction() {
        if (constructionQueue == null || !constructionQueue.isActive() || constructionCancelArmed) {
            return;
        }
        ConstructionProgress progress = constructionQueue.tick();
        if (progress.state() == letrain.construction.ConstructionState.DONE) {
            showConstructionNotice(ConstructionStatus.COMPLETE);
        } else if (progress.state() == letrain.construction.ConstructionState.CANCELLED) {
            showConstructionNotice(ConstructionStatus.CANCELLED);
        }
    }

    private RouteConstructionQueue constructionQueue() {
        if (constructionQueue == null) {
            constructionQueue = new RouteConstructionQueue(model.getEconomyManager());
        }
        return constructionQueue;
    }

    private void showConstructionNotice(String notice) {
        constructionNotice = notice;
        constructionNoticeUntilMillis = System.currentTimeMillis() + 2_500;
        constructionCancelArmed = false;
    }

    /** True while a construction job is actively laying cells (and manual editing is locked). */
    boolean isConstructionActive() {
        return constructionQueue != null && constructionQueue.isEditingLocked();
    }

    boolean isConstructionCancelArmed() {
        return constructionCancelArmed;
    }

    /**
     * Two-step Esc confirmation for an active construction: first Esc pauses and arms, second Esc
     * cancels (partial track remains), any other key resumes. Returns true when the key was
     * consumed by the confirmation.
     */
    private boolean handleConstructionConfirmation(InputEvent keyEvent) {
        if (!isConstructionActive()) {
            return false;
        }
        if (constructionCancelArmed) {
            if (keyEvent.getKeyType() == KeyType.Escape) {
                constructionQueue.cancel();
                showConstructionNotice(ConstructionStatus.CANCELLED);
            } else {
                constructionCancelArmed = false;
            }
            return true;
        }
        if (keyEvent.getKeyType() == KeyType.Escape) {
            constructionCancelArmed = true;
            return true;
        }
        return false;
    }

    /**
     * Blocks manual editing while construction is active. Navigation, camera keys and mode changes
     * stay available; DRIVE is left untouched because trains are not restricted by the works.
     * Returns true when the key was consumed.
     */
    private boolean handleConstructionEditLock(InputEvent keyEvent) {
        if (!isConstructionActive()) {
            return false;
        }
        if (model.getMode() == DRIVE) {
            return false;
        }
        if (isPlainCharacter(keyEvent, ':')) {
            model.setMode(letrain.mvp.Model.GameMode.COMMAND);
            model.setCommandText("");
            model.setCommandError("");
            model.setCommandNotice("");
            return true;
        }
        // Movement quantifiers (digits, Space reset) are navigation, not editing: keep them
        // available in RAILS while the works run. The sanitized events cannot build or erase.
        if (model.getMode() == RAILS && keyEvent.getKeyType() == KeyType.Character
                && keyEvent.getCharacter() != null && !keyEvent.isCtrlDown()
                && !keyEvent.isAltDown()) {
            char quantifier = keyEvent.getCharacter();
            if ((quantifier >= '0' && quantifier <= '9') || quantifier == ' ') {
                railTrackMaker
                        .onChar(new InputEvent(KeyType.Character, quantifier, false, false, false));
                return true;
            }
        }
        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != null) {
            Character c = keyEvent.getCharacter();
            boolean ctrlR = c == 18 || (keyEvent.isCtrlDown() && (c == 'r' || c == 'R'));
            if (ctrlR) {
                return true; // no redo while the works are running
            }
            if (!keyEvent.isCtrlDown() && !keyEvent.isAltDown()) {
                if (c == 'R' || c == 'X' || c == 'p' || c == '.') {
                    return true; // pause-editing, experiment, IDE and repeat-command are edits
                }
                if (c == 'u' && model.isSimulationPaused()) {
                    return true; // undo would rewrite the world under the works
                }
            }
        }
        if (handleModeHotkey(keyEvent)) {
            return true;
        }
        InputEvent movement = movementOnly(keyEvent);
        if (movement != null) {
            railTrackMaker.onChar(movement);
        }
        // Everything else is swallowed: no build/erase/infrastructure key reaches its mode handler.
        return true;
    }

    /**
     * Paints the construction HUD after the world renderer: remaining cells as ghost circles, A/B
     * endpoints and the one-line status. Built cells are real rails, so they are not repainted.
     */
    void drawConstructionOverlay() {
        if (constructionQueue != null && constructionQueue.isActive()) {
            ConstructionProgress progress = constructionQueue.progress();
            RoutePlan plan = constructionQueue.job()
                    .map(letrain.construction.RouteConstructionJob::plan).orElse(null);
            for (RoutePreviewOverlay.Cell cell : ConstructionOverlay.cells(plan,
                    progress.builtSteps())) {
                view.drawRoutePreviewCell(cell.x(), cell.y(), cell.glyph(), cell.endpoint(),
                        cell.style());
            }
            view.drawRoutePreviewStatus(
                    ConstructionStatus.format(progress, plan, constructionCancelArmed));
            return;
        }
        if (constructionNotice != null) {
            if (System.currentTimeMillis() <= constructionNoticeUntilMillis) {
                view.drawRoutePreviewStatus(constructionNotice);
            } else {
                constructionNotice = null;
            }
            return;
        }
        if (plannedWorksNotice != null) {
            if (System.currentTimeMillis() <= plannedWorksNoticeUntilMillis) {
                view.drawRoutePreviewStatus(plannedWorksNotice);
            } else {
                plannedWorksNotice = null;
            }
        }
    }

    /** Last construction progress, exposed for focused presenter tests. */
    ConstructionProgress getConstructionProgress() {
        return constructionQueue == null ? ConstructionProgress.idle()
                : constructionQueue.progress();
    }

    RouteConstructionQueue getConstructionQueue() {
        return constructionQueue;
    }

    /** Current planned works, exposed for focused presenter tests. */
    ConstructionPlanList getPlannedWorks() {
        return plannedWorks();
    }

    boolean isRoutePreviewSaveToList() {
        return routePreviewSaveToList;
    }

    /**
     * Paints every stored planned work on the map. These are budgets, not previews: they stay
     * visible until their row is deleted (or, in a later phase, the work is built). Active CONNECT
     * previews and construction ghosts are painted afterwards, so they take precedence on shared
     * cells.
     */
    void drawPlannedWorksOverlay() {
        if (plannedWorks == null || plannedWorks.isEmpty()) {
            return;
        }
        for (PlannedWork work : plannedWorks.works()) {
            if (work.id() == routePreviewEditingWorkId) {
                // While editing, the CONNECT preview owns this route: keeping the old overlay
                // underneath would show both the old and the candidate policy at once.
                continue;
            }
            for (RoutePreviewOverlay.Cell cell : RoutePreviewOverlay.cells(work.plan(),
                    work.start(), work.end())) {
                view.drawRoutePreviewCell(cell.x(), cell.y(), cell.glyph(), cell.endpoint(),
                        cell.style());
            }
        }
    }

    /**
     * Paints the CONNECT overlay after the world renderer: the single current plan plus A/B, and
     * the one-line status. It is deliberately a per-frame draw, so leaving CONNECT (or an
     * infeasible policy) simply stops painting and nothing persists into the next frame.
     */
    void drawRoutePreviewOverlay() {
        if (model.getMode() == CONNECT) {
            if (routePreviewSession == null) {
                return;
            }
            Point start = routePreviewSession.start().orElse(null);
            Point end = routePreviewSession.end().orElse(null);
            RoutePlan plan = routePreviewSession.lastPlan().orElse(null);
            String buildWarning = buildabilityWarning(plan);
            if (plan != null && !plan.feasible()) {
                // Infeasible: keep only the endpoints, never a stale route.
                plan = null;
            }
            for (RoutePreviewOverlay.Cell cell : RoutePreviewOverlay.cells(plan, start, end)) {
                view.drawRoutePreviewCell(cell.x(), cell.y(), cell.glyph(), cell.endpoint(),
                        cell.style());
            }
            if (!routePreviewSaveToList || routePreviewStatusVisible) {
                view.drawRoutePreviewStatus(RoutePreviewStatus.format(routePreviewSession,
                        buildWarning, routePreviewSaveToList, routePreviewEditingWorkId >= 0));
            }
            return;
        }
    }

    /** Plan accepted in CONNECT and handed to the construction queue (ADR-035/ADR-037). */
    RoutePlan getPendingRoutePlan() {
        return pendingRoutePlan;
    }

    /**
     * Current CONNECT session, exposed for focused presenter tests; {@code null} outside CONNECT.
     */
    RoutePreviewSession getRoutePreviewSession() {
        return routePreviewSession;
    }

    @Override
    public void onChar(InputEvent keyEvent) {
        if (coverActive) {
            // Any key continues from the cover; the key is consumed, never acted on.
            dismissCover();
            return;
        }
        if (((TerminalView) view).isShowingOverlay()) {
            // Vim-style pager: j/k (or Ctrl+N/P) scroll, h/l change width, PageUp/PageDown page,
            // +/- width, F maximize.
            TerminalView tv = (TerminalView) view;
            KeyType type = keyEvent.getKeyType();
            Character raw = keyEvent.getCharacter();
            char c = raw == null ? 0 : Character.toLowerCase(raw);
            boolean ctrl = keyEvent.isCtrlDown();
            boolean plain = !ctrl && !keyEvent.isAltDown();
            if (type == KeyType.ArrowUp || (plain && c == 'k')) {
                tv.scrollOverlay(-1);
            } else if (type == KeyType.ArrowDown || (plain && c == 'j')) {
                tv.scrollOverlay(1);
            } else if (type == KeyType.PageUp || (ctrl && (c == 'p' || c == 16))) {
                tv.scrollOverlayPage(-1);
            } else if (type == KeyType.PageDown || (ctrl && (c == 'n' || c == 14))) {
                tv.scrollOverlayPage(1);
            } else if (type == KeyType.ArrowLeft || (plain && c == 'h')) {
                tv.resizeOverlay(4);
            } else if (type == KeyType.ArrowRight || (plain && c == 'l')) {
                tv.resizeOverlay(-4);
            } else if (type == KeyType.Escape) {
                tv.clearOverlay();
            } else if (plain && (c == '+' || c == '=')) {
                tv.resizeOverlay(4);
            } else if (plain && (c == '-' || c == '_')) {
                tv.resizeOverlay(-4);
            } else if (plain && c == 'f') {
                tv.toggleOverlayMaximize();
            }
            return;
        }

        if (model.getMode() == letrain.mvp.Model.GameMode.COMMAND) {
            if (keyEvent.getKeyType() == KeyType.Escape) {
                leaveCommandMode();
                return;
            } else if (keyEvent.getKeyType() == KeyType.Enter) {
                String cmd = model.getCommandText().trim();
                if (cmd.isEmpty()) {
                    leaveCommandMode();
                    return;
                }
                if (commandHistory.isEmpty()
                        || !commandHistory.get(commandHistory.size() - 1).equals(cmd)) {
                    commandHistory.add(cmd);
                }
                historyIndex = commandHistory.size();

                executeCommand(model.getCommandText());
                return;
            } else if (keyEvent.getKeyType() == KeyType.Backspace) {
                String t = model.getCommandText();
                if (t.length() > 0) {
                    model.setCommandText(t.substring(0, t.length() - 1));
                    model.setCommandError("");
                    model.setCommandNotice("");
                }
                return;
            } else if (keyEvent.getKeyType() == KeyType.Character) {
                Character c = keyEvent.getCharacter();
                if (c != null) {
                    if (keyEvent.isCtrlDown() && (c == 'p' || c == 'P' || c == 16)) {
                        if (historyIndex > 0) {
                            historyIndex--;
                            model.setCommandText(commandHistory.get(historyIndex));
                            model.setCommandError("");
                            model.setCommandNotice("");
                        }
                        return;
                    } else if (keyEvent.isCtrlDown() && (c == 'n' || c == 'N' || c == 14)) {
                        if (historyIndex < commandHistory.size() - 1) {
                            historyIndex++;
                            model.setCommandText(commandHistory.get(historyIndex));
                            model.setCommandError("");
                            model.setCommandNotice("");
                        } else if (historyIndex == commandHistory.size() - 1) {
                            historyIndex++;
                            model.setCommandText("");
                            model.setCommandError("");
                            model.setCommandNotice("");
                        }
                        return;
                    } else if (!keyEvent.isCtrlDown() && !keyEvent.isAltDown()) {
                        model.setCommandText(model.getCommandText() + c);
                        model.setCommandError("");
                        model.setCommandNotice("");
                    }
                }
                return;
            } else if (keyEvent.getKeyType() == KeyType.ArrowUp) {
                if (historyIndex > 0) {
                    historyIndex--;
                    model.setCommandText(commandHistory.get(historyIndex));
                    model.setCommandError("");
                    model.setCommandNotice("");
                }
                return;
            } else if (keyEvent.getKeyType() == KeyType.ArrowDown) {
                if (historyIndex < commandHistory.size() - 1) {
                    historyIndex++;
                    model.setCommandText(commandHistory.get(historyIndex));
                    model.setCommandError("");
                    model.setCommandNotice("");
                } else if (historyIndex == commandHistory.size() - 1) {
                    historyIndex++;
                    model.setCommandText("");
                    model.setCommandError("");
                    model.setCommandNotice("");
                }
                return;
            }
            return; // Ignore other keys in COMMAND mode
        }

        // CONNECT (ADR-035) owns all keys while active. It is checked before the global
        // Enter/Esc/mode-hotkey routing so preview actions never fall through to menu or editing
        // shortcuts.
        if (model.getMode() == CONNECT) {
            handleConnectKey(keyEvent);
            return;
        }
        // ADR-037: Esc on an active construction arms a two-step cancel and never opens the File
        // menu. Any other key while armed resumes the queue.
        if (handleConstructionConfirmation(keyEvent)) {
            return;
        }
        if (isConnectEntry(keyEvent)) {
            if (!isConstructionActive() && canEnterConnectMode(model.getMode())) {
                enterConnectMode();
            }
            return;
        }
        // Phase-1 planned works: a map-side list of routes that have not been built. Checked
        // before the edit lock so an active construction gets the explicit "finish or cancel"
        // notice instead of the key being silently swallowed.
        if (isPlannedWorksEntry(keyEvent) && canOpenPlannedWorks(model.getMode())) {
            openPlannedWorks();
            return;
        }
        // While the construction queue builds, manual edits are locked; navigation, camera, mode
        // changes and the read-only console remain available.
        if (handleConstructionEditLock(keyEvent)) {
            return;
        }

        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != null) {
            Character c = keyEvent.getCharacter();
            // Ctrl+R -> redo paused editing (ADR-020 item 3). Lanterna may deliver the control code
            // 0x12 (18) with or without the ctrl flag, or the letter 'r'/'R' with the ctrl flag —
            // mirroring the Ctrl+P/Ctrl+N handling in COMMAND mode. A plain 'r' is NOT redo (it
            // switches to RAILS via the mode hotkeys below).
            // (Ctrl+Z is NOT used because in a terminal it suspends the task via SIGTSTP.)
            boolean ctrlR = c == 18 || (keyEvent.isCtrlDown() && (c == 'r' || c == 'R'));
            if (ctrlR) {
                redo(1);
                return;
            }
        }

        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != null
                && keyEvent.getCharacter() == '.') {
            if (model.getMode() != letrain.mvp.Model.GameMode.COMMAND
                    && !commandHistory.isEmpty()) {
                String cmd = commandHistory.get(commandHistory.size() - 1);
                executeCommand(cmd);
                return;
            }
        }

        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != null
                && keyEvent.getCharacter() == ':') {
            if (model.getMode() != letrain.mvp.Model.GameMode.PROGRAM) {
                model.setMode(letrain.mvp.Model.GameMode.COMMAND);
                model.setCommandText("");
                model.setCommandError("");
                model.setCommandNotice("");
                return;
            }
        }

        if (keyEvent.getKeyType() == KeyType.F1) {
            log.info("\n" + model.getRailwayGraphReport());
            return;
        }

        if (keyEvent.getKeyType() == KeyType.Enter) {
            // In DRIVE mode, Enter is for loading/unloading, not for switching to MENU.
            // The logic is handled inside trainDriverOnChar.
            if (model.getMode() != DRIVE) {
                lastCreatedLoco = null;
                // Finishing a train returns to the editing mode (so the player keeps building)
                // instead of the empty main menu; any other mode opens the menu as before.
                model.setMode(model.getMode() == TRAINS ? RAILS : MENU);
                return;
            }
        } else if (keyEvent.getKeyType() == KeyType.Tab) {
            int current = model.getHelpLevel();
            int next = (current - 1 + 3) % 3; // 2 -> 1 -> 0 -> 2 -> 1 -> 0... wait 2-1 = 1, 1-1 =
                                              // 0, 0-1 = 2
            if (next < 0)
                next = 2; // (0-1 = -1 -> 2)
            model.setHelpLevel(next);
            view.setHelpLevel(next);
            return;
        } else if (keyEvent.getKeyType() == KeyType.Escape) {
            // Esc opens the File menu (owner decision D11); Quit lives inside it now. During an
            // active construction Esc is consumed by the two-step cancel confirmation above.
            if (!isConstructionActive()) {
                view.showFileMenu();
            }
        } else if (handleModeHotkey(keyEvent)) {
            return;
        }

        ModeKeyHandler handler = modeKeyHandlers.get(model.getMode());
        if (handler != null) {
            handler.handle(keyEvent);
        }
    }

    /**
     * Dead-zone radii cycled by 'z', smallest first. The startup index is the last step,
     * {@link TerminalView#FULL_SCREEN_DEADZONE} (#697), so the camera only scrolls when the focus
     * is about to leave the screen; the first press then goes back to 1 and climbs again (999 → 1 →
     * 3 → 6 → ...). Radii at or above half the map height are skipped because they would not change
     * the framing.
     */
    private static final int[] CAMERA_DEADZONE_STEPS =
            {1, 3, 6, 10, 15, 20, 25, TerminalView.FULL_SCREEN_DEADZONE};
    private int cameraDeadzoneIndex = CAMERA_DEADZONE_STEPS.length - 1;

    private void cycleCameraDeadzone() {
        int maxRadius = view.getRows() / 2 - 1;
        if (maxRadius < 1) {
            maxRadius = 1;
        }

        int deadzone;
        do {
            cameraDeadzoneIndex = (cameraDeadzoneIndex + 1) % CAMERA_DEADZONE_STEPS.length;
            deadzone = CAMERA_DEADZONE_STEPS[cameraDeadzoneIndex];
        } while (deadzone != TerminalView.FULL_SCREEN_DEADZONE && deadzone >= maxRadius);

        view.setCameraDeadzone(deadzone);
        view.flashCameraDeadzone();
    }

    private void togglePauseEditing() {
        if (getExperimentSession().isActive()) {
            view.setStatusBarText("Experiment ON: press X to leave and restore before Record mode");
            return;
        }
        boolean recording = !model.isPauseEditing();
        model.setPauseEditing(recording);
        view.setStatusBarText(
                recording ? "Record: ON (edit mode: frozen world, instant build, undo/redo)"
                        : "Record: OFF");
        if (recording) {
            // Editing session: snapshot for undo/redo and start capturing the scenario recipe.
            getUndoRedoHistory().begin(model);
            commandJournal.startRecording();
        } else {
            // Leaving edit mode drops the undo session; the recipe is baked and kept for export.
            getUndoRedoHistory().end();
            commandJournal.stopRecording();
            commandJournal.bake();
        }
    }

    private boolean handleModeHotkey(InputEvent keyEvent) {
        if (keyEvent.getKeyType() != KeyType.Character || keyEvent.getCharacter() == ' ') {
            return false;
        }

        if (model.getMode() == letrain.mvp.Model.GameMode.ADD) {
            return false; // Let onChar handle Add mode keys (s, e, m, g)
        }

        if (model.getMode() == TRAINS) {
            handleTrainsModeKey(keyEvent);
            return true;
        }

        switch (keyEvent.getCharacter()) {
            case 'X':
                if (model.getMode() != letrain.mvp.Model.GameMode.PROGRAM) {
                    toggleExperimentMode();
                    return true;
                }
                return false;
            case 'R':
                if (model.getMode() != letrain.mvp.Model.GameMode.PROGRAM) {
                    togglePauseEditing();
                    return true;
                }
                return false;
            case 'z':
                if (model.getMode() != TRAINS) {
                    cycleCameraDeadzone();
                    return true;
                }
                return false;
            case 'Z':
                if (model.getMode() != TRAINS) {
                    view.setCameraPagination(!view.isCameraPagination());
                    view.flashCameraDeadzone();
                    return true;
                }
                return false;
            case 'a':
                model.setMode(letrain.mvp.Model.GameMode.ADD);
                return true;
            case 'r':
                model.setMode(RAILS);
                return true;
            case 'd':
                if (!model.getLocomotives().isEmpty()) {
                    model.setMode(DRIVE);
                    return true;
                }
                return false;
            case 'f':
                if (!model.getForks().isEmpty()) {
                    model.setMode(FORKS);
                    return true;
                }
                return false;
            case 's':
                if (!model.getSemaphores().isEmpty()) {
                    model.setMode(SEMAPHORES);
                    return true;
                }
                return false;
            case 't':
                if (model.getCursorRailTrack() != null) {
                    model.setMode(TRAINS);
                    newTrain = null;
                    return true;
                }
                return false;
            case 'c':
                if (model.canEnterLinkMode()) {
                    model.setMode(LINK);
                    if (model.getSelectedLocomotive() != null
                            && model.getSelectedLocomotive().getTrain() != null) {
                        Train train = model.getSelectedLocomotive().getTrain();
                        train.getTrainCouplingManager().resetLinkState(train);
                    }
                    return true;
                }
                return false;
            case 'u':
                // While pause-editing is on (undo history active), 'u' = undo (vim-like). Ctrl+Z
                // cannot be used because in a terminal it suspends the task (SIGTSTP).
                if (model.isSimulationPaused()) {
                    undo(1);
                    return true;
                }
                if (model.canEnterUnlinkMode()) {
                    model.setMode(UNLINK);
                    if (model.getSelectedLocomotive() != null
                            && model.getSelectedLocomotive().getTrain() != null) {
                        Train train = model.getSelectedLocomotive().getTrain();
                        train.getTrainCouplingManager().resetUnlinkState(train);
                    }
                    return true;
                }
                return false;
            case 'n':
                if (!model.getStations().isEmpty()) {
                    model.setMode(STATIONS);
                    return true;
                }
                return false;
            case 'e':
                if (!model.getSensors().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.SENSORS);
                    return true;
                }
                return false;
            case 'g':
                if (!model.getSpeedSignals().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.SPEED_SIGNALS);
                    return true;
                }
                return false;
            case 'p':
                model.setMode(PROGRAM);
                view.showIDE();
                // The IDE window is modal; once it closes, leave PROGRAM so the menu does not stay
                // highlighting it and the player is back in the editing mode.
                model.setMode(RAILS);
                return true;
            case 'o':
                handleSnapCursor();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onKeyUp(InputEvent keyEvent) {
        if (model.getMode() == RAILS) {
            railTrackMaker.onKeyUp(keyEvent);
        }
    }

    void handleProgramModeKey(InputEvent keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() == ' ') {
            view.showIDE();
        } else if (keyEvent.getKeyType() == KeyType.F12) {
            view.showIDE();
        }
    }


    private KeyType getEffectiveKeyType(InputEvent event) {
        if (event.getKeyType() == KeyType.Character && event.getCharacter() != null) {
            switch (Character.toLowerCase(event.getCharacter())) {
                case 'k':
                    return KeyType.ArrowUp;
                case 'j':
                    return KeyType.ArrowDown;
                case 'h':
                    return KeyType.ArrowLeft;
                case 'l':
                    return KeyType.ArrowRight;
            }
        }
        return event.getKeyType();
    }

    private boolean tryMoveSelectedWithShift(InputEvent keyEvent) {
        if (!keyEvent.isShiftDown() && !isShiftedVimMoveKey(keyEvent)) {
            return false;
        }
        KeyType keyType = getEffectiveKeyType(keyEvent);
        if (keyType == KeyType.ArrowUp) {
            moveSelectedElement(true);
            return true;
        }
        if (keyType == KeyType.ArrowDown) {
            moveSelectedElement(false);
            return true;
        }
        return false;
    }

    private boolean isShiftedVimMoveKey(InputEvent keyEvent) {
        Character c = keyEvent.getCharacter();
        return c != null && (c == 'K' || c == 'J');
    }

    private Dir selectedElementDir() {
        switch (model.getMode()) {
            case STATIONS:
                Station station = model.getSelectedStation();
                return station != null ? station.getCreationDir() : null;
            case SENSORS:
                letrain.track.Sensor sensor = model.getSelectedSensor();
                return sensor != null ? sensor.getCreationDir() : null;
            case SPEED_SIGNALS:
                letrain.track.SpeedSignal signal = model.getSelectedSpeedSignal();
                return signal != null ? signal.getCreationDir() : null;
            case SEMAPHORES:
                letrain.track.RailSemaphore semaphore = model.getSelectedSemaphore();
                return semaphore != null ? semaphore.getCreationDir() : null;
            default:
                return null;
        }
    }

    private void moveSelectedElement(boolean forward) {
        letrain.track.Track destTrack = null;
        switch (model.getMode()) {
            case STATIONS:
                if (model.getSelectedStation() != null) {
                    Station station = model.getSelectedStation();
                    boolean moved = forward ? model.moveSensorForward(station)
                            : model.moveSensorBackward(station);
                    if (moved) {
                        destTrack = station.getTrack();
                    }
                }
                break;
            case SENSORS:
                if (model.getSelectedSensor() != null) {
                    letrain.track.Sensor sensor = model.getSelectedSensor();
                    boolean moved = forward ? model.moveSensorForward(sensor)
                            : model.moveSensorBackward(sensor);
                    if (moved) {
                        destTrack = sensor.getTrack();
                    }
                }
                break;
            case SPEED_SIGNALS:
                if (model.getSelectedSpeedSignal() != null) {
                    letrain.track.SpeedSignal signal = model.getSelectedSpeedSignal();
                    boolean moved = forward ? model.moveSensorForward(signal)
                            : model.moveSensorBackward(signal);
                    if (moved) {
                        destTrack = signal.getTrack();
                    }
                }
                break;
            case SEMAPHORES:
                if (model.getSelectedSemaphore() != null) {
                    letrain.track.RailSemaphore semaphore = model.getSelectedSemaphore();
                    boolean moved = forward ? model.moveSensorForward(semaphore)
                            : model.moveSensorBackward(semaphore);
                    if (moved) {
                        destTrack = semaphore.getTrack();
                    }
                }
                break;
            default:
                return;
        }
        if (destTrack == null) {
            return;
        }
        model.getCursor().setPosition(destTrack.getPosition());
        Dir front = selectedElementDir();
        model.getCursor().setDir(front != null ? front : Dir.E);
        setPageOfPoint(destTrack.getPosition());
    }

    void handleStationsModeKey(InputEvent keyEvent) {
        if (tryMoveSelectedWithShift(keyEvent)) {
            return;
        }
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                StationId = StationId / 10;
                selectStation(StationId);
                break;
            case Character:
                if (keyEvent.getCharacter() == '-') {
                    // Legacy manual load
                    /*
                     * Station selectedStation = model.getSelectedStation(); if (selectedStation !=
                     * null) { Linker linker = selectedStation.getTrack().getLinker(); if (linker !=
                     * null) { Train train = linker.getTrain(); train.getTrip().restart(actualStop);
                     * } } }
                     */
                } else if (keyEvent.getCharacter() == ' ') {
                    if (StationId > 0) {
                        selectStation(StationId);
                        StationId = 0;
                        stationInputTimeout = 0;
                    }
                    // Unified Industrial Action (Space bar)
                    Station selectedStation = model.getSelectedStation();
                    if (selectedStation != null) {
                        letrain.vehicle.rail.Linker linker = selectedStation.getTrack().getLinker();
                        if (linker != null && linker.getTrain() != null) {
                            Train train = linker.getTrain();
                            train.getLogisticsManager().performIndustrialAction(selectedStation);
                        } else {
                            selectedStation.flipOrientation();
                            journalEditingCommand(
                                    "station " + selectedStation.getId() + " invert;");
                        }
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    StationId = StationId * 10 + (keyEvent.getCharacter() - '0');
                    selectStation(StationId);
                }
                break;
            case ArrowLeft:
                selectPrevStation();
                break;
            case ArrowRight:
                selectNextStation();
                break;
            default:
                break;
        }
    }

    private void handleSpeedSignalsModeKey(InputEvent keyEvent) {
        if (tryMoveSelectedWithShift(keyEvent)) {
            return;
        }
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                speedSignalId = speedSignalId / 10;
                model.selectSpeedSignal(speedSignalId);
                break;
            case Character:
                if (keyEvent.getCharacter() == 'm' || keyEvent.getCharacter() == 'M') {
                    if (model.getSelectedSpeedSignal() != null) {
                        letrain.track.SpeedSignal sig = model.getSelectedSpeedSignal();
                        sig.setMax(!sig.isMax());
                        recordKeyboardEdit("signal " + sig.getId() + " set mode "
                                + (sig.isMax() ? "max" : "min"));
                    }
                } else if (keyEvent.getCharacter() == ' ') {
                    if (speedSignalId > 0) {
                        model.selectSpeedSignal(speedSignalId);
                        speedSignalId = 0;
                        speedSignalInputTimeout = 0;
                    }
                    if (model.getSelectedSpeedSignal() != null) {
                        letrain.track.SpeedSignal sig = model.getSelectedSpeedSignal();
                        sig.setCreationDir(sig.getCreationDir().inverse());
                        recordKeyboardEdit("signal " + sig.getId() + " invert");
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (model.getSelectedSpeedSignal() != null) {
                        int val = keyEvent.getCharacter() - '0';
                        if (val == 0) {
                            val = 10;
                        }
                        model.getSelectedSpeedSignal().setLimit(val);
                        recordKeyboardEdit("signal " + model.getSelectedSpeedSignal().getId()
                                + " set limit " + val);
                    } else {
                        speedSignalId = speedSignalId * 10 + (keyEvent.getCharacter() - '0');
                        speedSignalInputTimeout = System.currentTimeMillis() + 1000;
                    }
                }
                break;
            case ArrowLeft:
                if (model.selectPrevSpeedSignal()) {
                    setPageOfPoint(model.getSelectedSpeedSignal().getPosition());
                }
                break;
            case ArrowRight:
                if (model.selectNextSpeedSignal()) {
                    setPageOfPoint(model.getSelectedSpeedSignal().getPosition());
                }
                break;
            case ArrowUp:
                if (model.getSelectedSpeedSignal() != null) {
                    int l = model.getSelectedSpeedSignal().getLimit();
                    if (l < 10) {
                        model.getSelectedSpeedSignal().setLimit(l + 1);
                        recordKeyboardEdit("signal " + model.getSelectedSpeedSignal().getId()
                                + " set limit " + (l + 1));
                    }
                }
                break;
            case ArrowDown:
                if (model.getSelectedSpeedSignal() != null) {
                    int l = model.getSelectedSpeedSignal().getLimit();
                    if (l > 1) {
                        model.getSelectedSpeedSignal().setLimit(l - 1);
                        recordKeyboardEdit("signal " + model.getSelectedSpeedSignal().getId()
                                + " set limit " + (l - 1));
                    }
                }
                break;
            default:
                break;
        }
    }

    private void handleSensorsModeKey(InputEvent keyEvent) {
        if (tryMoveSelectedWithShift(keyEvent)) {
            return;
        }
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                sensorId = sensorId / 10;
                model.selectSensor(sensorId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (sensorId > 0) {
                        model.selectSensor(sensorId);
                        sensorId = 0;
                        sensorInputTimeout = 0;
                    }
                    if (model.getSelectedSensor() != null) {
                        letrain.track.Sensor sensor = model.getSelectedSensor();
                        sensor.setCreationDir(sensor.getCreationDir().inverse());
                        journalEditingCommand("sensor " + sensor.getId() + " invert;");
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    sensorId = sensorId * 10 + (keyEvent.getCharacter() - '0');
                    sensorInputTimeout = System.currentTimeMillis() + 1000;
                }
                break;
            case ArrowLeft:
                if (model.selectPrevSensor()) {
                    setPageOfPoint(model.getSelectedSensor().getPosition());
                }
                sensorId = 0;
                break;
            case ArrowRight:
                if (model.selectNextSensor()) {
                    setPageOfPoint(model.getSelectedSensor().getPosition());
                }
                sensorId = 0;
                break;
            default:
                break;
        }
    }

    private void handleSemaphoresModeKey(InputEvent keyEvent) {
        if (tryMoveSelectedWithShift(keyEvent)) {
            return;
        }
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                semaphoreId = semaphoreId / 10;
                selectSemaphore(semaphoreId);
                break;
            case Character:
                if (keyEvent.getCharacter() == 'm' || keyEvent.getCharacter() == 'M') {
                    toggleSemaphore();
                } else if (keyEvent.getCharacter() == ' ') {
                    if (semaphoreId > 0) {
                        selectSemaphore(semaphoreId);
                        semaphoreId = 0;
                        semaphoreInputTimeout = 0;
                    }
                    if (model.getSelectedSemaphore() != null
                            && model.getSelectedSemaphore().getCreationDir() != null) {
                        letrain.track.RailSemaphore semaphore = model.getSelectedSemaphore();
                        semaphore.setCreationDir(semaphore.getCreationDir().inverse());
                        journalEditingCommand("semaphore " + semaphore.getId() + " invert;");
                    }
                    semaphoreId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    semaphoreId = semaphoreId * 10 + (keyEvent.getCharacter() - '0');
                    semaphoreInputTimeout = System.currentTimeMillis() + 1000;
                }
                break;
            case ArrowUp:
                toggleSemaphore();
                semaphoreId = 0;
                break;
            case ArrowDown:
                toggleSemaphore();
                semaphoreId = 0;
                break;
            case ArrowLeft:
                selectPrevSemaphore();
                break;
            case ArrowRight:
                selectNextSemaphore();
                break;
            default:
                break;
        }
    }

    private void handleUnlinkModeKey(InputEvent keyEvent) {
        switch (getEffectiveKeyType(keyEvent)) {
            case ArrowUp:
                selectFrontDivisionSense();
                break;
            case ArrowDown:
                selectBackDivisionSense();
                break;
            case ArrowLeft:
                selectPrevLink();
                break;
            case ArrowRight:
                selectNextLink();
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    divideTrain();
                    model.setMode(model.getPreviousMode());
                }
                break;
            case Enter:
                divideTrain();
                model.setMode(model.getPreviousMode());
                break;
            case Delete:
                destroyLinkers();
                model.setMode(model.getPreviousMode());
                break;
            default:
                break;
        }
    }

    private void handleLinkModeKey(InputEvent keyEvent) {
        switch (getEffectiveKeyType(keyEvent)) {
            case ArrowUp:
                selectVehiclesInFront();
                break;
            case ArrowDown:
                selectVehiclesAtBack();
                break;
            case ArrowLeft:
                if (model.getSelectedLocomotive() != null
                        && model.getSelectedLocomotive().getTrain() != null) {
                    Train train = model.getSelectedLocomotive().getTrain();
                    if (train.getNumLinkersToJoin() > 0) {
                        train.setNumLinkersToJoin(train.getNumLinkersToJoin() - 1);
                    }
                }
                break;
            case ArrowRight:
                if (model.getSelectedLocomotive() != null
                        && model.getSelectedLocomotive().getTrain() != null) {
                    Train train = model.getSelectedLocomotive().getTrain();
                    if (train.getNumLinkersToJoin() < train.getLinkersToJoin().size()) {
                        train.setNumLinkersToJoin(train.getNumLinkersToJoin() + 1);
                    }
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    linkSelectedVehicles();
                    model.setMode(model.getPreviousMode());
                }
                break;
            case Enter:
                linkSelectedVehicles();
                model.setMode(model.getPreviousMode());
                break;
            default:
                break;
        }
    }

    private void handleTrainsModeKey(InputEvent keyEvent) {
        if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
            return;
        }
        char cChar = keyEvent.getCharacter();
        String c = String.valueOf(cChar);
        if (c.isEmpty()) {
            return;
        }

        if (Character.isDigit(cChar)) {
            if (lastCreatedLoco != null) {
                int colorIdx = cChar - '0';
                lastCreatedLoco.setColor(
                        Locomotive.COLOR_PALETTE[colorIdx % Locomotive.COLOR_PALETTE.length]);
                return;
            }
            if (c.equals("1")) {
                model.setSelectedWagonType(letrain.track.CargoTypes.GOLD);
            } else if (c.equals("2"))
                model.setSelectedWagonType(letrain.track.CargoTypes.COAL);
            else if (c.equals("3"))
                model.setSelectedWagonType(letrain.track.CargoTypes.RUBY);
            return;
        }

        if (!c.matches("([A-Za-z])?")) {
            return;
        }
        RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (track.getLinker() != null) {
            log.warn("Can't add a train to a track with a linker");
            return;
        }
        Dir cursorDir = Dir.E;
        String prefix = cursorPrefix();
        // Uppercase builds a locomotive. Some terminals report the unshifted character, so the
        // shift flag counts as uppercase too.
        if (Character.isUpperCase(cChar) || keyEvent.isShiftDown()) {
            String aspect = c.toUpperCase(java.util.Locale.ROOT);
            int locoId = model.peekNextLocomotiveId();
            Locomotive locomotive = new Locomotive(locoId, aspect);
            int trainId = model.peekNextTrainId();
            Train train = new Train(trainId);
            train.pushBack(locomotive);
            train.addCoreTrainEventListener(this);
            train.setDirectorLinker(locomotive);

            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), locomotive);

            if (locomotive.getDir() == null) {
                track.removeLinker();
                return;
            }

            model.nextLocomotiveId();
            model.nextTrainId();
            model.addLocomotive(locomotive);
            model.getEconomyManager().onLocomotiveConstructed(locomotive);
            train.getSafetyManager().claimOccupiedSegments();
            cursorDir = locomotive.getDir();
            lastCreatedLoco = locomotive;
            journalEditingCommand(prefix + "new locomotive " + aspect + " "
                    + locomotive.getColor().toLowerCase() + ";");
        } else {
            Wagon wagon = new Wagon(c);
            wagon.setExclusiveCargoType(model.getSelectedWagonType());

            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), wagon);

            if (wagon.getDir() == null) {
                track.removeLinker();
                return;
            }

            model.addWagon(wagon);
            model.getEconomyManager().onWagonConstructed(wagon);
            lastCreatedLoco = null;
            if (wagon.getTrain() != null) {
                wagon.getTrain().getSafetyManager().claimOccupiedSegments();
            }
            cursorDir = wagon.getDir();
            String cargoToken = wagon.getExclusiveCargoType() == null
                    || wagon.getExclusiveCargoType() == letrain.track.CargoTypes.NONE ? ""
                            : " " + wagon.getExclusiveCargoType().name().toLowerCase();
            journalEditingCommand(prefix + "new wagon " + c + cargoToken + ";");
        }
        Point newPos = new Point(model.getCursor().getPosition());
        newPos.move(cursorDir, 1);
        model.getCursor().setDir(cursorDir);
        model.getCursor().setPosition(newPos);
    }

    private void handleSnapCursor() {
        letrain.map.Point targetPos = null;
        switch (model.getMode()) {
            case DRIVE:
            case LINK:
            case UNLINK:
                if (model.getSelectedLocomotive() != null) {
                    targetPos = model.getSelectedLocomotive().getPosition();
                }
                break;
            case FORKS:
                if (model.getSelectedFork() != null) {
                    targetPos = model.getSelectedFork().getPosition();
                }
                break;
            case SEMAPHORES:
                if (model.getSelectedSemaphore() != null) {
                    targetPos = model.getSelectedSemaphore().getPosition();
                }
                break;
            case STATIONS:
                if (model.getSelectedStation() != null) {
                    targetPos = model.getSelectedStation().getPosition();
                }
                break;
            case SENSORS:
                if (model.getSelectedSensor() != null) {
                    targetPos = model.getSelectedSensor().getPosition();
                }
                break;
            case SPEED_SIGNALS:
                if (model.getSelectedSpeedSignal() != null) {
                    targetPos = model.getSelectedSpeedSignal().getPosition();
                }
                break;
            default:
                break;
        }

        if (targetPos != null) {
            model.getCursor().setPosition(targetPos);
        }
        // Flash a white cross around the cursor so it is easy to spot (issue #696), whether it
        // jumped to the selection or stayed where it was.
        model.getCursor().ping(System.currentTimeMillis());
    }

    private void deleteVehicle() {
        letrain.map.Dir cursorDir = model.getCursor().getDir();
        // Move back to the previous track
        model.getCursor().getPosition().move(cursorDir.inverse());

        letrain.track.rail.RailTrack track =
                model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (track != null && track.getLinker() != null) {
            letrain.vehicle.rail.Linker linker = track.getLinker();
            if (linker instanceof Locomotive) {
                model.removeLocomotive((Locomotive) linker);
            } else if (linker instanceof Wagon) {
                model.removeWagon((Wagon) linker);
            }
            track.removeLinker();

            // Restore proper cursor direction before curve
            letrain.map.Dir entryDir = track.getRouter().getDir(cursorDir);
            if (entryDir != null) {
                model.getCursor().setDir(entryDir.inverse());
            }
        } else {
            // Restore cursor if nothing was deleted
            model.getCursor().getPosition().move(cursorDir);
        }
    }

    private void handleForksModeKey(InputEvent keyEvent) {
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                forkId = forkId / 10;
                selectFork(forkId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (forkId > 0) {
                        selectFork(forkId);
                        forkId = 0;
                        forkInputTimeout = 0;
                    }
                    toggleFork();
                    forkId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    forkId = forkId * 10 + (keyEvent.getCharacter() - '0');
                    selectFork(forkId);
                    forkInputTimeout = System.currentTimeMillis() + 1000;
                }
                break;
            case ArrowUp:
                toggleFork();
                forkId = 0;
                break;
            case ArrowDown:
                toggleFork();
                forkId = 0;
                break;
            case ArrowLeft:
                selectPrevFork();
                break;
            case ArrowRight:
                selectNextFork();
                break;
            default:
                break;
        }
    }


    private void handleAddModeKey(InputEvent keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Escape) {
            model.setMode(model.getPreviousMode());
            return;
        }
        if (keyEvent.getKeyType() == KeyType.Character) {
            Character c = keyEvent.getCharacter();
            if (c != null) {
                switch (Character.toLowerCase(c)) {
                    case 'n':
                        railTrackMaker
                                .onChar(new InputEvent(KeyType.End, null, false, false, false));
                        model.setMode(model.getPreviousMode());
                        break;
                    case 'e':
                        railTrackMaker
                                .onChar(new InputEvent(KeyType.Insert, null, false, false, false));
                        model.setMode(model.getPreviousMode());
                        break;
                    case 's':
                        railTrackMaker
                                .onChar(new InputEvent(KeyType.Home, null, false, false, false));
                        model.setMode(model.getPreviousMode());
                        break;
                    case 'g':
                        railTrackMaker
                                .onChar(new InputEvent(KeyType.Delete, null, false, false, false));
                        model.setMode(model.getPreviousMode());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void trainDriverOnChar(InputEvent keyEvent) {
        model.setShowId(false);
        switch (getEffectiveKeyType(keyEvent)) {
            case Backspace:
                locomotiveId = locomotiveId / 10;
                selectLocomotive(locomotiveId);
                break;
            case ArrowUp:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                        accelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowDown:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                        decelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowLeft:
                selectPrevLocomotive();
                locomotiveId = 0;
                break;
            case ArrowRight:
                selectNextLocomotive();
                locomotiveId = 0;
                break;
            case PageUp:
                if (keyEvent.isCtrlDown()) {
                    mapPageRight();
                } else {
                    mapPageUp();
                }
                break;
            case PageDown:
                if (keyEvent.isCtrlDown()) {
                    mapPageLeft();
                } else {
                    mapPageDown();
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (locomotiveId > 0) {
                        selectLocomotive(locomotiveId);
                        locomotiveId = 0;
                        locomotiveInputTimeout = 0;
                    }
                    // Space bar now only toggles reverse when stopped
                    toggleReversed();
                    locomotiveId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && locomotiveId == 0) {
                        model.setShowId(true);
                    } else {
                        locomotiveId = locomotiveId * 10 + (keyEvent.getCharacter() - '0');
                        selectLocomotive(locomotiveId);
                    }
                } else if (keyEvent.getCharacter() == 'm') {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco != null) {
                        if (!loco.isEngineOn()) {
                            audioController.startEngine(loco);
                        } else if (loco.getSpeed() == 0 && loco.getTargetSpeed() == 0) {
                            audioController.stopEngineWithSound(loco.getId(), loco);
                        }
                    }
                }
                break;
            case Enter:
                // Enter key now handles loading/unloading at a station, or switches to menu
                if (model.getSelectedLocomotive() != null
                        && model.getSelectedLocomotive().getSpeed() == 0) { // Solo si
                    // el tren
                    // está
                    // detenido
                    Train train = model.getSelectedLocomotive().getTrain();
                    Station station = train.getLogisticsManager().getStationAtTrain();
                    if (station != null) {
                        if (train.getLogisticsManager().isLoading()) { // Si ya está
                                                                       // cargando/descargando, lo
                                                                       // termina
                            train.getLogisticsManager().endLoadUnloadProcess();
                        } else {
                            if (station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                if (!train.getLogisticsManager().getCapableWagons(station, true)
                                        .isEmpty()) {
                                    train.getLogisticsManager().startUnloadProcess(station);
                                    train.recordStopAtStation();
                                }
                            } else if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                if (!train.getLogisticsManager().getCapableWagons(station, false)
                                        .isEmpty()) {
                                    train.getLogisticsManager().startLoadProcess(station);
                                    train.recordStopAtStation();
                                }
                            }
                        }
                        return; // Consume the event
                    }
                }
                // If not on a station or not stopped, Enter should switch to menu
                model.setMode(MENU);
                break;
            default:
                break;
        }
    }

    private void destroyLinkers() {
        if (model.getSelectedLocomotive() != null) {
            Train train = model.getSelectedLocomotive().getTrain();

            List<Linker> linkersToDestroy = train.getTrainCouplingManager().destroyLinkers(train,
                    () -> model.nextTrainId());
            for (Linker linker : linkersToDestroy) {
                if (linker instanceof Locomotive) {
                    model.removeLocomotive((Locomotive) linker);
                } else {
                    model.removeWagon((Wagon) linker);
                }
                linker.getTrack().removeLinker();
            }
        }
    }

    private void toggleReversed() {
        if (model.getSelectedLocomotive() != null) {
            if (model.getSelectedLocomotive().getSpeed() == 0) {
                model.getSelectedLocomotive().toggleReversed();
            }
        }
    }

    private void linkSelectedVehicles() {
        if (model.getSelectedLocomotive() != null) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco.getTrain() != null) {
                Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    boolean forward = train.isJoinFront();
                    int count = train.getNumLinkersToJoin();
                    train.getTrainCouplingManager().joinLinkers(train);
                    journalEditingCommand("train " + loco.getId() + " couple "
                            + (forward ? "forward" : "backward") + " " + count + ";");
                }
                model.setMode(letrain.mvp.Model.GameMode.MENU);
            }
        }
    }

    private void selectVehiclesAtBack() {
        if (model.getSelectedLocomotive() != null
                && model.getSelectedLocomotive().getTrain() != null) {
            Train train = model.getSelectedLocomotive().getTrain();

            train.getTrainCouplingManager().updateLinkersToJoin(train, false);
        }
    }

    private void selectVehiclesInFront() {
        if (model.getSelectedLocomotive() != null) {
            if (model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.getTrainCouplingManager().updateLinkersToJoin(train, true);
            } else {
                // handle error
            }
        } else {
            // handle error
        }
    }

    private void selectFrontDivisionSense() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().setFrontDivisionSense(train);
    }

    private void selectBackDivisionSense() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().setBackDivisionSense(train);
    }

    private void selectNextLink() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().selectNextDivisionLink(train);
    }

    private void selectPrevLink() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().selectPrevDivisionLink(train);
    }

    private void divideTrain() {
        Locomotive loco = model.getSelectedLocomotive();
        if (loco != null && loco.getTrain() != null) {
            Train train = loco.getTrain();
            // Map the division sense to the DSL 'forward/backward' the canonical replay expects
            // (prepareUnlink flips the sense when the director is reversed).
            boolean forward = train.isDivisionFront() != loco.isReversed();
            int count = train.getNumLinkersToRemove();

            train.getTrainCouplingManager().divideTrain(train, () -> model.nextTrainId());
            if (count > 0) {
                journalEditingCommand("train " + loco.getId() + " uncouple "
                        + (forward ? "forward" : "backward") + " " + count + ";");
            }
            audioController.playOneShot("link", (float) loco.getPosition().getX(),
                    (float) loco.getPosition().getY());
        }
    }

    /**
     * Records a canonical editing command (e.g. a coupling) into the command journal while
     * recording and into the paused-editing undo history. Coupling commands are id-based, so no
     * cursor prefix.
     */
    private void journalEditingCommand(String command) {
        letrain.command.CommandJournal journal = model.getCommandJournal();
        if (journal != null && journal.isRecording()) {
            journal.recordCoalescing(command);
        }
        if (model.isSimulationPaused()) {
            letrain.command.UndoRedoHistory history = getUndoRedoHistory();
            if (history != null) {
                history.recordCoalescing(command);
            }
        }
    }

    public void selectLocomotive(int id) {
        if (model.selectLocomotive(id)) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    Train newTrain;

    /***********************************************************
     * FORKS
     **********************************************************/
    private void selectNextFork() {
        if (model.selectNextFork()) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void selectPrevFork() {
        if (model.selectPrevFork()) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void selectFork(int id) {
        if (model.selectFork(id)) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void toggleFork() {
        if (model.getSelectedFork() != null) {
            letrain.track.rail.ForkRailTrack fork = model.getSelectedFork();
            boolean wasAlternative = fork.isUsingAlternativeRoute();
            fork.flipRoute();
            if (fork.isUsingAlternativeRoute() != wasAlternative) {
                journalEditingCommand("fork " + fork.getId() + " set "
                        + (fork.isUsingAlternativeRoute() ? "curved" : "straight") + ";");
            }
            audioController.playOneShot("fork",
                    (float) model.getSelectedFork().getPosition().getX(),
                    (float) model.getSelectedFork().getPosition().getY());
        }
    }

    /***********************************************************
     * SEMAPHORES
     **********************************************************/
    private void selectNextSemaphore() {
        if (model.selectNextSemaphore()) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }
    }

    private void selectPrevSemaphore() {
        if (model.selectPrevSemaphore()) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }
    }

    private void selectSemaphore(int id) {
        if (model.selectSemaphore(id)) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }
    }

    private void toggleSemaphore() {
        if (model.getSelectedSemaphore() != null) {
            model.getSelectedSemaphore().setOpen(!model.getSelectedSemaphore().isOpen());
        }
    }

    /***********************************************************
     * TRAINS
     **********************************************************/
    private void selectNextLocomotive() {
        if (model.selectNextLocomotive()) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    private void selectPrevLocomotive() {
        if (model.selectPrevLocomotive()) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    private void decelerateLocomotive() {
        if (model.getSelectedLocomotive() == null) {
            return;
        }
        model.getSelectedLocomotive().decSpeed();
    }

    private void accelerateLocomotive() {
        if (model.getSelectedLocomotive() == null) {
            return;
        }
        model.getSelectedLocomotive().incSpeed();
    }

    private void mapPageDown() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX(), offset.getY() + view.getRows()));
        view.clear();
    }

    private void mapPageLeft() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX() - view.getCols(), offset.getY()));
        view.clear();
    }

    private void mapPageUp() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX(), offset.getY() - view.getRows()));
        view.clear();
    }

    private void mapPageRight() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX() + view.getCols(), offset.getY()));
        view.clear();
    }

    void setPageOfPoint(Point point) {
        if (point != null) {
            view.ensureVisible(point.getX(), point.getY(), view.getCameraDeadzone(),
                    view.isCameraPagination());
        }
    }

    /***********************************************************
     * StationS
     **********************************************************/
    private void selectNextStation() {
        if (model.selectNextStation()) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    private void selectPrevStation() {
        if (model.selectPrevStation()) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    private void selectStation(int id) {
        if (model.selectStation(id)) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    public File changeExtension(File originalFile, String newExtension) {
        String directory = originalFile.getParent();
        String fileName = originalFile.getName();
        int lastDotIndex = fileName.lastIndexOf('.');

        String baseName = fileName;
        if (lastDotIndex > 0) {
            baseName = fileName.substring(0, lastDotIndex);
        }
        String newFileName = baseName + "." + newExtension;
        return new File(directory, newFileName);
    }

    /**
     * New Game entry point (File → New and console {@code new game;}, D1). With a game in progress
     * it first confirms the discard (simple flow until #417), then opens the dialog and starts the
     * chosen world.
     */
    @Override
    public void onNewGame() {
        Map<String, String> currentSettings =
                model.getEconomyManager() != null ? model.getEconomyManager().effectiveConfig()
                        : Map.of();
        onNewGame(currentSettings);
    }

    /**
     * New Game flow with explicit prefill (used by New and by the Settings dialog's "New game with
     * these values…", S3b): confirms the discard when a game is running, opens the dialog and
     * starts the chosen world.
     */
    void onNewGame(Map<String, String> prefill) {
        if (gameStarted && !view.confirm(letrain.mvp.NewGameText.CONFIRM_TITLE,
                letrain.mvp.NewGameText.CONFIRM_MESSAGE)) {
            return;
        }
        Map<String, String> settings = view.showNewGameDialog(prefill);
        if (settings == null || settings.isEmpty()) {
            return;
        }
        startNewGame(settings);
    }

    /**
     * File → Settings (S3b, #764): opens the in-game dialog with the current effective values (plus
     * the world seed), the scenario-locked keys and a port that applies settings to the running
     * game and writes {@code letrain.cfg} defaults (D4). Choosing "New game" closes the dialog and
     * re-enters the New Game flow with the form prefilled.
     */
    @Override
    public void onSettings() {
        if (model.getEconomyManager() == null) {
            return;
        }
        Map<String, String> current =
                new java.util.LinkedHashMap<>(model.getEconomyManager().effectiveConfig());
        current.put(letrain.settings.NewGameForm.SEED_KEY, String.valueOf(model.getSeed()));
        SettingsDialog.Action action = view.showSettingsDialog(current,
                model.getScenarioSettingKeys(), new SettingsDialog.SettingsPort() {
                    @Override
                    public letrain.settings.SettingsApplyResult apply(Map<String, String> values) {
                        return model.applyLiveSettings(values);
                    }

                    @Override
                    public String saveDefaults(Map<String, String> values) {
                        return saveSettingsAsDefaults(values);
                    }
                });
        if (action instanceof SettingsDialog.Action.StartGame startGame) {
            onNewGame(startGame.settings());
        }
    }

    /**
     * Writes the form values into the game's {@code letrain.cfg} (D4); when the game found no file
     * it creates one in the working directory. Returns {@code null} on success or the failure
     * message the dialog shows.
     */
    String saveSettingsAsDefaults(Map<String, String> values) {
        return letrain.settings.ConfigFileWriter
                .writeDefaultsOrMessage(model.getEconomyManager().getConfigFile(), values);
    }

    /** Writes the values into {@code target}; {@code null} on success or the failure message. */
    static String writeDefaults(File target, Map<String, String> values) {
        return letrain.settings.ConfigFileWriter.writeDefaultsOrMessage(target, values);
    }

    /**
     * Builds a fresh world from the dialog settings (S2) and swaps it in. Reuses the load/rebuild
     * machinery: {@code Model.newGame} creates a seeded model and applies the validated settings
     * before any terrain is generated, then {@link #setModel} rebinds the presenter (journal,
     * listeners, free construction off).
     */
    void startNewGame(Map<String, String> settings) {
        int seed = resolveSeed(settings.get(letrain.settings.NewGameForm.SEED_KEY));
        letrain.mvp.impl.Model fresh = letrain.mvp.impl.Model.newGame(seed, settings);
        setModel(fresh);
        gameStarted = true;
        model.setMode(RAILS);
        view.centerOn(model.getCursor().getPosition().getX(),
                model.getCursor().getPosition().getY());
        model.updateGroundMap(view.getScrollOffset(), view.getCols(), view.getRows());
        view.setStatusBarText(letrain.mvp.NewGameText.startedMessage(seed));
    }

    /**
     * Resolves the form's seed text; {@code random} (or a missing/invalid value) draws a fresh one.
     * Delegates to the shared {@link letrain.settings.NewGameForm#resolveSeed(String)} so the 2D
     * terminal and the 3D client start the same world for the same text (S4, #777).
     */
    static int resolveSeed(String seedText) {
        return letrain.settings.NewGameForm.resolveSeed(seedText);
    }

    /** True once New/Load started a game; the boot File menu disables Save until then (D8). */
    @Override
    public boolean hasActiveGame() {
        return gameStarted;
    }

    @Override
    public void onSaveGame(File file) {
        if (file == null) {
            return;
        }
        file = letrain.command.FileNames.withSavegameExtension(file);
        boolean ok = gameSaveService.save(this.model, file);
        if (!ok) {
            view.showMessage("Save Error", "Could not save game to\n" + file.getAbsolutePath());
        }
    }

    /** Exports the current editing journal as a scenario file (seed + commands). */
    private void saveScenario(File file) {
        try {
            if (commandJournal.appliedEntries().isEmpty()) {
                view.showMessage("Scenario",
                        "Cannot export: nothing recorded yet (toggle Record/edit mode with 'R' and edit).");
                return;
            }
            writeScenario(file, letrain.command.ScenarioExporter.render(model,
                    commandJournal.appliedEntries()));
        } catch (Exception e) {
            log.error("Error saving scenario to {}", file.getAbsolutePath(), e);
            view.showMessage("Scenario Error", "Could not save scenario: " + e.getMessage());
        }
    }

    /**
     * Generates the world recipe (seed + on build + on start, no settings/program) for the editor.
     */
    @Override
    public String getScenarioText() {
        return letrain.command.ScenarioExporter.renderWorld(model, commandJournal.appliedEntries());
    }

    /** Generates the scenario's settings section from the settings catalogue. */
    @Override
    public String getConfigurationText() {
        String section =
                letrain.command.ScenarioFile.configurationSection(letrain.settings.SettingsCatalog
                        .scenarioConfiguration(model.getEconomyManager() != null
                                ? model.getEconomyManager().effectiveConfig()
                                : null));
        return section.isBlank() ? "configuration {\n}\n" : section;
    }

    /** Exports the (possibly hand-edited) scenario text to a file. */
    @Override
    public void onExportScenarioText(File file, String text) {
        if (file != null && text != null) {
            writeScenario(file, text);
        }
    }

    private void writeScenario(File file, String text) {
        file = letrain.command.FileNames.withScenarioExtension(file);
        try {
            java.nio.file.Files.writeString(file.toPath(), text);
            view.setStatusBarText("Scenario saved: " + file.getName());
        } catch (Exception e) {
            log.error("Error saving scenario to {}", file.getAbsolutePath(), e);
            view.showMessage("Scenario Error", "Could not save scenario: " + e.getMessage());
        }
    }

    /**
     * Rebuilds a fresh same-seed world and replays the scenario commands on it (free constructor).
     */
    private void playScenario(File file) {
        try {
            playScenarioText(java.nio.file.Files.readString(file.toPath()), file);
        } catch (Exception e) {
            log.error("Error reading scenario from {}", file.getAbsolutePath(), e);
            view.showMessage("Scenario Error", "Could not play scenario: " + e.getMessage());
        }
    }

    /** Plays a scenario from its text (used by the editor's Play button). */
    @Override
    public void onPlayScenarioText(String text) {
        playScenarioText(text, null);
    }

    private void playScenarioText(String text, File file) {
        try {
            letrain.command.ScenarioFile.Scenario scenario =
                    letrain.command.ScenarioFile.parse(text);
            // Apply the scenario's settings (configuration section) to the fresh world BEFORE any
            // terrain generation, so the scenario reproduces the same terrain and rules regardless
            // of the local letrain.cfg; the model remembers the keys as scenario-locked (S3a/D10)
            // and wires the clock with them.
            letrain.mvp.impl.Model fresh = new letrain.mvp.impl.Model(scenario.seed());
            fresh.applyScenarioConfiguration(scenario.configuration());
            applyModel(fresh);
            gameStarted = true;
            // Constructor libre: building the scenario costs nothing (ADR-020).
            model.getEconomyManager().setFreeConstruction(true);
            // Replay silently: re-running a coupling must not play the "link" sound of the user
            // action it represents.
            boolean[] failed = {false};
            runWithoutEventSounds(() -> {
                for (String cmd : scenario.buildCommands()) {
                    String error = executeScenarioCommand(cmd);
                    if (error != null) {
                        log.error("Scenario build command failed: '{}': {}", cmd, error);
                        view.showMessage("Scenario Error", cmd + "\n" + error);
                        failed[0] = true;
                        return;
                    }
                }
                for (String cmd : scenario.startCommands()) {
                    String error = executeScenarioCommand(cmd);
                    if (error != null) {
                        log.error("Scenario start command failed: '{}': {}", cmd, error);
                        view.showMessage("Scenario Error", cmd + "\n" + error);
                        failed[0] = true;
                        return;
                    }
                }
            });
            if (failed[0]) {
                return;
            }
            // Materialize the terrain under the whole rebuilt network; otherwise tracks outside the
            // cursor/render radius appear floating over void until the cursor passes over them.
            materializeGroundUnderTracks();
            // Install the scenario's automation program (the operator), if any. It references the
            // elements just built, so it runs after the build/start replay.
            if (scenario.program() != null && !scenario.program().isBlank()) {
                handleScriptErrors(model.setProgram(scenario.program()));
            }
            // The imported recipe becomes the journal base, so a later export keeps the whole
            // network (imported + subsequent edits).
            commandJournal.clear();
            for (String cmd : scenario.buildCommands()) {
                commandJournal.record(cmd);
            }
            commandJournal.bake();
            // Scenario = free construction mode: enter the Record/edit mode (frozen, instant,
            // undo).
            model.setPauseEditing(true);
            commandJournal.startRecording();
            getUndoRedoHistory().begin(model);
            view.setStatusBarText(
                    "Scenario played: " + (file != null ? file.getName() : "(editor)"));
        } catch (Exception e) {
            log.error("Error playing scenario", e);
            view.showMessage("Scenario Error", "Could not play scenario: " + e.getMessage());
        }
    }

    /** Runs one scenario command through the same console path used by the UI. */
    private String executeScenarioCommand(String cmd) {
        return letrain.command.PlayerCommandExecutor.execute(cmd, model, f -> onSaveGame(f),
                f -> onLoadGame(f), new letrain.command.TurtleBuilder(model, railTrackMaker),
                (title, msg) -> view.showMessage(title, msg), () -> onExitGame(), null, null,
                false);
    }

    /**
     * Generates the ground blocks around every rail tile so the loaded world has terrain visible.
     */
    private void materializeGroundUnderTracks() {
        int r = 2;
        model.getRailMap().forEach(track -> {
            letrain.map.Point p = track.getPosition();
            model.getGroundMap().renderBlock(p.getX() - r, p.getY() - r, r * 2 + 1, r * 2 + 1);
        });
    }

    /** Exports the current editing journal as a scenario file (called by the UI/DSL). */
    @Override
    public void onExportScenario(File file) {
        if (file != null) {
            saveScenario(file);
        }
    }

    /** Imports (plays) a scenario file: fresh same-seed world + replayed commands. */
    @Override
    public void onImportScenario(File file) {
        if (file != null) {
            playScenario(file);
        }
    }

    @Override
    public boolean isRecordingCommands() {
        return commandJournal.isRecording() && model.isSimulationPaused();
    }

    @Override
    public String getGameTimeText() {
        if (model == null || model.getGameClock() == null) {
            return "";
        }
        letrain.time.GameTime now = model.getGameClock().now();
        return String.format("D%d %02d:%02d", now.day(), now.hour(), now.minute());
    }

    @Override
    public void onLoadGame(File file) {
        if (file != null && file.exists()) {
            try {
                java.util.Optional<letrain.mvp.impl.Model> optionalModel =
                        gameSaveService.load(file);
                if (optionalModel.isPresent()) {
                    letrain.mvp.impl.Model loadedModel = optionalModel.get();
                    // setModel already registers this presenter as a train event listener.
                    setModel(loadedModel);
                    gameStarted = true;
                    letrain.map.Point startPos = getActiveFocusPoint();
                    if (startPos != null) {
                        view.centerOn(startPos.getX(), startPos.getY());
                    }
                    loadedModel.updateGroundMap(view.getScrollOffset(), view.getCols(),
                            view.getRows());
                } else {
                    view.showMessage("Load Error",
                            "Could not load game from\n" + file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Serious error while loading game in 2D", e);
                view.showMessage("Load Error", "Exception: " + e.getMessage());
            }
        }
    }

    @Override
    public void onSaveCommands(File file) {
        if (file != null) {
            saveProgram(this.model.getProgram(), file);
        }
    }

    @Override
    public void onLoadCommands(File file) {
        String input = loadProgram(file);
        if (input == null) {
            return;
        }
        // Program file from disk: the model's load path (strict, like every other entry point).
        List<String> errors = model.setProgramFromDisk(input);
        handleScriptErrors(errors);
    }

    @Override
    public void onEditCommands(String content) {
        List<String> errors = model.setProgram(content);
        handleScriptErrors(errors);
    }

    private void handleScriptErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            String combinedErrors = String.join("\n", errors);
            view.showMessage("Script Errors", combinedErrors);
        }
    }

    @Override
    public void onExitGame() {
        // Do not tear the terminal down from inside the command/event loop: that would let the
        // still-running frame paint into a closed screen and can leave the tty in raw mode. Mark
        // the loop for exit and let start() return normally; the launcher/presenter.stop() owns
        // the terminal hand-back afterwards.
        running = false;
        if (view != null) {
            try {
                view.setEndOfGame(true);
            } catch (Exception e) {
                log.warn("Error requesting end of game", e);
            }
        }
    }

    @Override
    public void onPlay() {}

    String loadProgram(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Error loading program", ex);
            return null;
        }
    }

    void saveProgram(String content, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
        } catch (IOException ex) {
            log.error("Error saving model", ex);
        }
    }

    @Override
    public String getProgram() {
        return model.getProgram();
    }

    @Override
    public void setProgram(String program) {
        model.setProgram(program);
    }

    @Override
    public String getGameObjectsReport() {
        return model.getGameObjectsReport();
    }

    @Override
    public java.util.List<String> getEventLogEntries() {
        return model.getEventLogManager().getEntries();
    }

    public Point getActiveFocusPoint() {
        if ((model.getMode() == DRIVE || model.getMode() == LINK || model.getMode() == UNLINK)
                && model.getSelectedLocomotive() != null) {
            return model.getSelectedLocomotive().getPosition();
        } else if (model.getMode() == FORKS && model.getSelectedFork() != null) {
            return model.getSelectedFork().getPosition();
        } else if (model.getMode() == SEMAPHORES && model.getSelectedSemaphore() != null) {
            return model.getSelectedSemaphore().getPosition();
        } else if (model.getMode() == STATIONS && model.getSelectedStation() != null) {
            return model.getSelectedStation().getPosition();
        } else if (model.getCursor() != null) {
            return model.getCursor().getPosition();
        }
        return new Point(0, 0);
    }

    @Override
    public void onScreenResized(int columns, int rows) {
        Point focus = getActiveFocusPoint();
        if (focus != null) {
            view.centerOn(focus.getX(), focus.getY());
        }
        Point offset = view.getScrollOffset();
        model.updateGroundMap(offset, columns, rows);
    }

    @Override
    public void onMapPageChanged(Point scrollOffset, int columns, int rows) {
        model.updateGroundMap(scrollOffset, columns, rows);
    }

    private void updateTimeouts() {
        long now = System.currentTimeMillis();
        if (sensorInputTimeout > 0 && now > sensorInputTimeout) {
            model.selectSensor(sensorId);
            sensorId = 0;
            sensorInputTimeout = 0;
        }
        if (forkInputTimeout > 0 && now > forkInputTimeout) {
            model.selectFork(forkId);
            forkId = 0;
            forkInputTimeout = 0;
        }
        if (speedSignalInputTimeout > 0 && now > speedSignalInputTimeout) {
            model.selectSpeedSignal(speedSignalId);
            speedSignalId = 0;
            speedSignalInputTimeout = 0;
        }
        if (semaphoreInputTimeout > 0 && now > semaphoreInputTimeout) {
            model.selectSemaphore(semaphoreId);
            semaphoreId = 0;
            semaphoreInputTimeout = 0;
        }
        if (stationInputTimeout > 0 && now > stationInputTimeout) {
            model.selectStation(StationId);
            StationId = 0;
            stationInputTimeout = 0;
        }
        if (locomotiveInputTimeout > 0 && now > locomotiveInputTimeout) {
            model.selectLocomotive(locomotiveId);
            locomotiveId = 0;
            locomotiveInputTimeout = 0;
        }
    }

    @Override
    public void onContact(Train train, letrain.map.Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("contact", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for locomotives involved in the contact
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.getTrain() != null && (loco.getSpeed() > 0 || loco.getTargetSpeed() > 0)) {
                    if (Point.distance(loco.getPosition(), pos) < 2.0) {
                        audioController.stopSynthesizer(loco.getId());
                    }
                }
            }
        }
    }

    @Override
    public void onLink(Train train) {
        if (eventSoundsSuppressed || audioController == null) {
            return;
        }
        Linker firstLinker = train.getLinkers().peekFirst();
        if (firstLinker != null) {
            letrain.map.Point pos = firstLinker.getPosition();
            if (pos != null) {
                audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
            }
        }
    }

    @Override
    public void onUnlink(Train train) {
        if (eventSoundsSuppressed || audioController == null) {
            return;
        }
        Linker firstLinker = train.getLinkers().peekFirst();
        if (firstLinker != null) {
            letrain.map.Point pos = firstLinker.getPosition();
            if (pos != null) {
                audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
            }
        }
    }

    @Override
    public void onCrash(Train train, letrain.map.Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("explosion", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for all locomotives involved in the crash
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.isDestroying()) {
                    audioController.stopSynthesizer(loco.getId());
                }
            }
        }
    }
}
