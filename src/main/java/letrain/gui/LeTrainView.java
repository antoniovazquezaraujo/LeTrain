package letrain.gui;

import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.sim.GameModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.tui.SimpleUI;


public class LeTrainView extends BorderPane implements SimpleUI {
    GamePresenter presenter;
    LeTrainViewGrid viewGrid;
    Text statusBar = new Text();
    private Point position = new Point(0, 0); // scroll position of the viewer

    public LeTrainView(GamePresenter presenter) {
        viewGrid = new LeTrainViewGrid();
        this.presenter = presenter;
        setCenter(viewGrid);
        setBottom(statusBar);
        this.setFocusTraversable(true);
        clear();
        addEventListener();
        this.requestFocus();
    }

    public void addEventListener() {
        GraphicConverter converter = new BasicGraphicConverter();

        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case Y:
                    presenter.paintLoop();
                case ENTER:
                    break;
                case UP:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getPos();
                        p.setY(p.getY() - 1);
                        setPos(p);
                        clear();
                    } else {
                        presenter.onMakerAdvance();
                    }
                    break;
                case DOWN:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getPos();
                        p.setY(p.getY() + 1);
                        setPos(p);
                        clear();
                    } else {
                        presenter.onMakerInverse();
                        presenter.onMakerAdvance();
                        presenter.onMakerInverse();
                    }
                    break;
                case LEFT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getPos();
                        p.setX(p.getX() - 1);
                        setPos(p);
                        clear();
                    } else {
                        presenter.onMakerTurnLeft();
                    }
                    break;
                case RIGHT:
                    if (keyEvent.isControlDown()) {
                        clear();
                        Point p = getPos();
                        p.setX(p.getX() + 1);
                        setPos(p);
                        clear();
                    } else {
                        presenter.onMakerTurnRight();
                    }
                    break;
                case M:
                    presenter.onGameModeSelected(GameModel.Mode.MAP_WALK);
                    break;
                case T:
                    presenter.onGameModeSelected(GameModel.Mode.TRACK_WALK);
                    break;
                case P:
                    presenter.onGameModeSelected(GameModel.Mode.MAKE_TRACK);
                    break;
                case R:
                    presenter.onGameModeSelected(GameModel.Mode.REMOVE_TRACK);
                    break;
                case W:
                    presenter.onFactoryCreateTrain("A new train");
                    break;
                case S:
                    presenter.onIncTrainAcceleration();
                    break;
                case A:
                    presenter.onDecTrainAcceleration();
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
    public Point getPos() {
        return viewGrid.getPos();
    }

    @Override
    public void setPos(Point pos) {
        viewGrid.setPos(pos);
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
    public void setColor(int x, int y, Color color) {
        viewGrid.setColor(x, y, color);
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
