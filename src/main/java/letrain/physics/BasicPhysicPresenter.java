package letrain.physics;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.impl.View;
import letrain.mvp.impl.ViewGrid;
import letrain.physics.PhysicModel;
import letrain.physics.BasicPhysicModel;
import letrain.physics.Body2D;
import letrain.physics.Vector2D;
import letrain.physics.BasicPhysicVisitor;
import letrain.physics.PhysicVisitor;
import letrain.vehicle.impl.Cursor;

import java.util.Vector;

public class BasicPhysicPresenter implements GameViewListener, letrain.physics.PhysicPresenter {
    private Body2D newBody;
    private int selectedBodyIndex;
    private Body2D selectedBody;
    private PhysicModel model;
    private View view;
    private Timeline loop;
    private PhysicVisitor renderer;
    private final PhysicInfoVisitor informer;

    RailTrackMaker maker;
    private boolean reversed = false;
    private Dir dir = Dir.E;

    public BasicPhysicPresenter(PhysicModel model) {
        this.model = model;
        view = new View(this);
        this.model = new BasicPhysicModel();
        renderer = new BasicPhysicVisitor(view);
        informer = new PhysicInfoVisitor(view);
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            view.clear();
            renderer.visitModel(model);
            informer.visitModel(model);
            view.paint();
            model.moveBodies();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
    }


    /***********************************************************
     * Presenter implementation
     **********************************************************/
    @Override
    public View getView() {
        return view;
    }

    @Override
    public PhysicModel getModel() {
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
        {
            switch (keyEvent.getCode()) {
                case SPACE:
                    createBody();
                    break;
                case A:
                    if (model.getSelectedBody() != null) {
                        if (keyEvent.isShiftDown()) {
                            model.getSelectedBody().setMotorForce(model.getSelectedBody().getMotorForce() - 1.0f);
                        } else {
                            model.getSelectedBody().setMotorForce(model.getSelectedBody().getMotorForce() + 1.0f);
                        }
                        model.getSelectedBody().setBrakesActivated(false);
                        model.getSelectedBody().setBrakesForce(0);

                    }
                    break;
                case B:
                    if (model.getSelectedBody() != null) {
                        if (keyEvent.isShiftDown()) {
                            model.getSelectedBody().setBrakesForce(model.getSelectedBody().getBrakesForce() - 1.0f);
                        } else {
                            model.getSelectedBody().setBrakesForce(model.getSelectedBody().getBrakesForce() + 1.0f);
                        }
                    }
                    model.getSelectedBody().setBrakesActivated(true);
                    model.getSelectedBody().setMotorForce(0);
                    break;
                case M:
                    if (model.getSelectedBody() != null) {
                        if (keyEvent.isShiftDown()) {
                            model.getSelectedBody().setMass(model.getSelectedBody().getMass() - 1);
                        } else {
                            model.getSelectedBody().setMass(model.getSelectedBody().getMass() + 1);
                        }
                    }
                    break;
                case P:
                    selectPrevBody();
                    break;
                case N:
                    selectNextBody();
                    break;
                case PAGE_UP:
                    if (keyEvent.isControlDown()) {
                        mapPageRight();
                    } else {
                        mapPageUp();
                    }
                    break;
                case PAGE_DOWN:
                    if (keyEvent.isControlDown()) {
                        mapPageLeft();
                    } else {
                        mapPageDown();
                    }
                    break;
                case UP:
                    getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorForward();
                    break;
                case DOWN:
                    getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorBackward();
                    break;
                case LEFT:
                    if (keyEvent.isControlDown()) {
                        if (model.getSelectedBody() != null) {
                            Vector2D newVelocity = Vector2D.fromDir(model.getSelectedBody().getDir().turnLeft(), getModel().getSelectedBody().getVelocity().magnitude());
                            getModel().getSelectedBody().setVelocity(newVelocity);
                        }

                    } else {
                        cursorTurnLeft();
                    }
                    break;
                case RIGHT:
                    if (keyEvent.isControlDown()) {
                        if (model.getSelectedBody() != null) {
                            Vector2D newVelocity = Vector2D.fromDir(model.getSelectedBody().getDir().turnRight(), getModel().getSelectedBody().getVelocity().magnitude());
                            getModel().getSelectedBody().setVelocity(newVelocity);
                        }
                    } else {
                        cursorTurnRight();
                    }
                    break;
            }

        }

    }

    private void createBody() {
        Body2D body = new Body2D();
        body.setPosition(new Vector2D(model.getCursor().getPosition().getX(), model.getCursor().getPosition().getY()));
        body.setVelocity(Vector2D.fromDir(model.getCursor().getDir(), 1));
        model.addBody(body);
        model.setSelectedBody(body);
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

    public void selectNextBody() {
        if (model.getBodies().isEmpty()) {
            return;
        }
        selectedBodyIndex++;
        if (selectedBodyIndex >= model.getBodies().size()) {
            selectedBodyIndex = 0;
        }
        selectedBody = model.getBodies().get(selectedBodyIndex);
        model.setSelectedBody(selectedBody);
    }

    public void selectPrevBody() {
        if (model.getBodies().isEmpty()) {
            return;
        }
        selectedBodyIndex--;
        if (selectedBodyIndex < 0) {
            selectedBodyIndex = model.getBodies().size() - 1;
        }
        selectedBody = model.getBodies().get(selectedBodyIndex);
        model.setSelectedBody(selectedBody);
    }


    public void cursorForward() {
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        view.setPageOfPos(newPos.getX(), newPos.getY());
    }

    public void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }

    public void cursorTurnLeft() {
        this.dir = this.dir.turnLeft();
        model.getCursor().setDir(this.dir);
    }

    public void cursorTurnRight() {
        this.dir = this.dir.turnRight();
        model.getCursor().setDir(this.dir);
    }
}
