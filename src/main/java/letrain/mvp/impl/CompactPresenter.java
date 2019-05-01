package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.mvp.GameViewListener;
import letrain.render.RenderingVisitor;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Train;

public class CompactPresenter implements GameViewListener, letrain.mvp.Presenter {
    public enum TrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    private final letrain.mvp.Model model;
    private final letrain.mvp.View view;
    private Timeline loop;
    private final RenderingVisitor renderer;
    Train selectedTrain;
    ForkRailTrack selectedFork;
    private int selectedTrainIndex;
    private int selectedForkIndex;
    private TrackType newTrackType = TrackType.NORMAL_TRACK;

    public CompactPresenter() {
        this(null);
    }

    public CompactPresenter(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new Model();
        }
        view = new View(this);
        renderer = new RenderingVisitor(view);
        selectedTrainIndex = 0;
        if(!this.model.getTrains().isEmpty()) {
            selectedTrain = model.getTrains().get(selectedTrainIndex);
        }
        selectedForkIndex = 0;
        if(!this.model.getForks().isEmpty()) {
            selectedFork = model.getForks().get(selectedForkIndex);
        }
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            view.clear();
            renderer.visitModel(model);
            view.paint();
            model.moveTrains();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
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


    /***********************************************************
     * GameViewListener implementation
     *********************************************************
     * @param mode*/
    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
        //Avisamos al anterior y al nuevo
    }


    @Override
    public void onChar(KeyEvent keyEvent) {
        switch (model.getMode()) {
            case TRACKS:
                if (keyEvent.getCode().equals(KeyCode.UP)) {
                    if (keyEvent.isShiftDown()) {
                        model.getCursor().setMode(Cursor.CursorMode.DRAWING);
                        createTrack();
                        makingTraks = true;
                    } else if (keyEvent.isControlDown()) {
                        model.getCursor().setMode(Cursor.CursorMode.ERASING);
                        removeTrack();
                        makingTraks = false;
                    } else {
                        model.getCursor().setMode(Cursor.CursorMode.MOVING);
                        cursorForward();
                        makingTraks = false;
                    }
                } else if (keyEvent.getCharacter().equals(KeyCode.PAGE_UP)) {
                    if (keyEvent.isControlDown()) {
                        mapPageRight();
                    } else {
                        mapPageUp();
                    }

                } else if (keyEvent.getCharacter().equals(KeyCode.PAGE_DOWN)) {
                    if (keyEvent.isControlDown()) {
                        mapPageLeft();
                    } else {
                        mapPageDown();
                    }
                } else {
                    switch (keyEvent.getCode()) {
                        case DOWN:
                            cursorBackward();
                            break;
                        case LEFT:
                            cursorTurnLeft();
                            break;
                        case RIGHT:
                            cursorTurnRight();
                            break;
                    }
                }
                break;
            case TRAINS:
                switch (keyEvent.getCode()) {
                    case UP:
                        accelerateTrain();
                        break;
                    case DOWN:
                        decelerateTrain();
                        break;
                    case LEFT:
                        selectPrevTrain();
                        break;
                    case RIGHT:
                        selectNextTrain();
                        break;
                }
                break;

            case CREATE_LOAD_PLATFORM:
                switch (keyEvent.getCode()) {
                    case UP:
                        createLoadPlatformTrack();
                        break;
                }
                break;
            case CREATE_FACTORY_PLATFORM:
                switch (keyEvent.getCode()) {
                    case UP:
                        createFactoryPlatformTrack();
                        break;
                }
                break;
            case FORKS:
                switch (keyEvent.getCode()) {
                    case UP:
                        toggleFork();
                        break;
                    case DOWN:
                        toggleFork();
                        break;
                    case LEFT:
                        selectPrevFork();
                        break;
                    case RIGHT:
                        selectNextFork();
                        break;
                }
                break;
            case USE_LOAD_PLATFORMS:
                switch (keyEvent.getCode()) {
                    case UP:
                        loadTrain();
                        break;
                    case DOWN:
                        unloadTrain();
                        break;
                    case LEFT:
                        selectPrevLoadPlatform();
                        break;
                    case RIGHT:
                        selectNextLoadPlatform();
                        break;
                }
                break;
            case USE_FACTORY_PLATFORMS:
                String c = keyEvent.getCharacter();
                if (!c.matches("([A-Za-z])*")) {
                    if (c.toUpperCase().equals(c)) {
                        createLocomotive(c);
                    } else {
                        createWagon(c);
                    }
                }

                break;
        }

    }

    private void mapPageDown() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageLeft() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageUp() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageRight() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void createWagon(String c) {

    }

    private void createLocomotive(String c) {

    }

    private void selectNextLoadPlatform() {

    }

    private void selectPrevLoadPlatform() {

    }

    private void unloadTrain() {

    }

    private void loadTrain() {

    }

    private void selectNextFork() {
        selectedForkIndex++;
        if (selectedForkIndex >= model.getTrains().size()) {
            selectedForkIndex = 0;
        }
        selectedFork = model.getForks().get(selectedForkIndex);

    }

    private void selectPrevFork() {
        selectedForkIndex--;
        if (selectedForkIndex < 0) {
            selectedForkIndex = model.getForks().size() - 1;
        }
        selectedFork = model.getForks().get(selectedForkIndex);

    }

    private void toggleFork() {
        if (selectedFork != null) {
            selectedFork.flipRoute();
        }
    }

    private void createFactoryPlatformTrack() {

    }

    private void createLoadPlatformTrack() {

    }

    private void selectNextTrain() {
        selectedTrainIndex++;
        if (selectedTrainIndex >= model.getTrains().size()) {
            selectedTrainIndex = 0;
        }
        selectedTrain = model.getTrains().get(selectedTrainIndex);
    }

    private void selectPrevTrain() {
        selectedTrainIndex--;
        if (selectedTrainIndex < 0) {
            selectedTrainIndex = model.getTrains().size() - 1;
        }
        selectedTrain = model.getTrains().get(selectedTrainIndex);
    }

    private void decelerateTrain() {
        if (selectedTrain == null) return;
        ((Tractor) selectedTrain.getDirectorLinker()).decForce(1000);
    }

    private void accelerateTrain() {
        if (selectedTrain == null) return;
        ((Tractor) selectedTrain.getDirectorLinker()).incForce(1000);
    }

    private void cursorForward() {
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }
    private void cursorBackward() {
        reversed= true;
        cursorForward();
        reversed = false;
    }
    private void removeTrack() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            model.getRailMap().removeTrack(position.getX(), position.getY());
        }
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point p = model.getCursor().getPosition();
        view.setPageOfPos(p.getX(), p.getY());

    }

    private void createTrack() {
        degreesOfRotation = 0;
        makeTrack();
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    private void cursorTurnRight() {
        if (makingTraks) {
            if (degreesOfRotation >= 0) {
                this.dir = this.dir.turnRight();
                model.getCursor().setDir(this.dir);
                degreesOfRotation -= 1;
            }
        } else {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
        }
    }

    private void cursorTurnLeft() {
        if (makingTraks) {
            if (degreesOfRotation <= 0) {
                this.dir = this.dir.turnLeft();
                model.getCursor().setDir(this.dir);
                degreesOfRotation += 1;
            }
        } else {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
        }
    }



    private int degreesOfRotation = 0;
    private Dir dir =Dir.N;
    Track oldTrack;
    Dir oldDir ;
    boolean reversed = false;
    boolean makingTraks = false;

    private boolean makeTrack() {
        makingTraks = true;
        Point cursorPosition = model.getCursor().getPosition();
        Dir dir = model.getCursor().getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

        RailTrack track = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            track = createTrackOfSelectedType();
        }
        track.addRoute(oldDir, dir);
        if (oldTrack != null) {
            track.connect(oldDir, oldTrack);
        }
        track.setPosition(cursorPosition);
        getModel().getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            model.addFork(myNewTrack);
            final Router router = track.getRouter();
            router.forEach(t -> myNewTrack.getRouter().addRoute(t.getKey(), t.getValue()));
            getModel().getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            getModel().getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        oldTrack = track;
        return true;
    }

    public void selectNewTrackType( TrackType type) {
        this.newTrackType = type;
    }

    public TrackType getNewTrackType() {
        return this.newTrackType;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STOP_TRACK:
                return new StopRailTrack();
            case TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case TUNNEL_GATE:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
}
