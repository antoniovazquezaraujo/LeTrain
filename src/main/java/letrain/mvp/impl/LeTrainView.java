package letrain.mvp.impl;

import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;

import java.util.Optional;


public class LeTrainView extends BorderPane implements GameView {
    private final GameViewListener gameViewListener;
    private final LeTrainViewGrid viewGrid;
    private Point position = new Point(0, 0); // scroll position of the viewer
    private Text statusBar = new Text();
    private Dialog<String> trainFactoryDialog;

    public LeTrainView(GameViewListener gameViewListener) {
        viewGrid = new LeTrainViewGrid();
        this.gameViewListener = gameViewListener;
        setCenter(viewGrid );
        setBottom(statusBar);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
        statusBar.setFill(Color.GREEN);
        statusBar.setStroke(Color.GREEN);
        statusBar.setFont(new Font("Lucida Sans Unicode", 10));
        createTrainFactoryDialog();
    }

    private void createTrainFactoryDialog() {
        trainFactoryDialog= new TextInputDialog("LeTrain, the letter train simulator");
        trainFactoryDialog.setTitle("Train factory");
        trainFactoryDialog.setHeaderText("Enter the wagons of your train");
        trainFactoryDialog.setContentText("Wagons:");
// The Java 8 way to get the response value (with lambda expression).
//        result.ifPresent(name -> System.out.println("Your name: " + name));
    }

    private void addEventListener() {

        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case ENTER:
                    break;
                case UP:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setY(p.getY() - 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onMakerAdvance();
                    }
                    break;
                case DOWN:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setY(p.getY() + 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onMakerInverse();
                        gameViewListener.onMakerAdvance();
                        gameViewListener.onMakerInverse();
                    }
                    break;
                case LEFT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setX(p.getX() - 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onMakerTurnLeft();
                    }
                    break;
                case RIGHT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getMapScrollPage();
                        p.setX(p.getX() + 1);
                        setMapScrollPage(p);
                        clear();
                    } else {
                        gameViewListener.onMakerTurnRight();
                    }
                    break;
                case M:
                    gameViewListener.onGameModeSelected(GameModel.Mode.MAP_WALK);
                    break;
                case T:
                    gameViewListener.onGameModeSelected(GameModel.Mode.TRACK_WALK);
                    break;
                case P:
                    gameViewListener.onGameModeSelected(GameModel.Mode.MAKE_TRACK);
                    break;
                case R:
                    gameViewListener.onGameModeSelected(GameModel.Mode.REMOVE_TRACK);
                    break;
                case W:
                    Optional<String> result = trainFactoryDialog.showAndWait();
                    if (result.isPresent()){
                        gameViewListener.onFactoryCreateTrain(result.get());
                    }
                    break;
                case C:
                    gameViewListener.onMakerCreateFactoryGateTrack();
                    break;
                case V:
                    gameViewListener.onMakerCreateTunnelTrack();
                    break;
                case B:
                    gameViewListener.onMakerCreateStopTrack();
                    break;
                case S:
                    gameViewListener.onIncTrainAcceleration();
                    break;
                case A:
                    gameViewListener.onDecTrainAcceleration();
                    break;
//                case F:
//                    this.model.moveTrains();
//                    break;
                case Q:
                    System.exit(0);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public Point getMapScrollPage() {
        return viewGrid.getMapScrollPage();
    }

    @Override
    public void setMapScrollPage(Point pos) {
        viewGrid.setMapScrollPage(pos);
        statusBar.setText("Página: "+ viewGrid.getMapScrollPage().getX()+ ", "+ viewGrid.getMapScrollPage().getY());
    }


    @Override
    public void paint() {
        viewGrid.paint();
    }

    @Override
    public void clear() {
        viewGrid.clear();
    }

    @Override
    public void set(int x, int y, String c) {
        viewGrid.set(x, y, c);
    }

    @Override
    public void setColor(Color color) {
        viewGrid.setColor(color);
    }

    @Override
    public void setPageOfPos(int x, int y) {
        viewGrid.setPageOfPos(x,y);
        statusBar.setText("Página: "+ viewGrid.getMapScrollPage().getX()+ ", "+ viewGrid.getMapScrollPage().getY());
    }

    @Override
    public void clear(int x, int y) {
        viewGrid.clear(x, y);
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
        viewGrid.fill(x, y, width, height, c);
    }

    @Override
    public void box(int x, int y, int width, int height) {
        viewGrid.box(x, y, width, height);
    }
}
