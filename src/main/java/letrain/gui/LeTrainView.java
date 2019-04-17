package letrain.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import letrain.map.Point;
import letrain.sim.GameModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.tui.SimpleUI;


public class LeTrainView extends BorderPane implements SimpleUI {
    GamePresenter presenter;
    public static final int ROWS = 40;
    public static final int COLS = 80;
    String[][] screen = new String [COLS][ROWS];
    TextArea textArea;
    Text statusBar = new Text();
    GridPane grid = new GridPane();
    private Point position = new Point(0,0); // scroll position of the viewer

    public LeTrainView(GamePresenter presenter) {
        this.presenter = presenter;
        Font font = Font.font("Monospace", 20);
        textArea = new TextArea();
        textArea.setPrefRowCount(ROWS);
        textArea.setPrefColumnCount(COLS);
        textArea.setFont(font);
        setCenter(textArea);
        setBottom(statusBar);
        textArea.setFocusTraversable(false);
        this.setFocusTraversable(true);
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
                    if(keyEvent.isControlDown()){
                        clear();
                        Point p = getPos();
                        p.setY(p.getY()-1);
                        setPos(p);
                        clear();
                    }else {
                        presenter.onMakerAdvance();
                    }
                    break;
                case DOWN:
                    if(keyEvent.isControlDown()){
                        clear();
                        Point p = getPos();
                        p.setY(p.getY()+1);
                        setPos(p);
                        clear();
                    }else {
                        presenter.onMakerInverse();
                        presenter.onMakerAdvance();
                        presenter.onMakerInverse();
                    }
                    break;
                case LEFT:
                    if(keyEvent.isControlDown()){
                        clear();
                        Point p = getPos();
                        p.setX(p.getX()-1);
                        setPos(p);
                        clear();
                    }else {
                        presenter.onMakerTurnLeft();
                    }
                    break;
                case RIGHT:
                    if(keyEvent.isControlDown()){
                        clear();
                        Point p = getPos();
                        p.setX(p.getX()+1);
                        setPos(p);
                        clear();
                    }else {
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
        return this.position;
    }

    @Override
    public void setPos(Point pos) {
        statusBar.setText("Página: "+ position.getY()+ "," + position.getX());
        this.position = pos;
    }

    @Override
    public void paint() {
        textArea.clear();
        StringBuffer sb = new StringBuffer();
        for (double row = 0; row < ROWS; row++) {
            for (double col = 0; col < COLS; col++) {
                sb.append(screen[(int)col][(int)row]);
            }
            textArea.appendText(sb.toString()+ System.lineSeparator());
            sb.setLength(0);
        }
    }

    @Override
    public void clear() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                screen[col][row] = " ";
            }
        }
    }

    @Override
    public void set(int x, int y, String c) {
        x-=position.getX()*COLS;
        y-=position.getY()*ROWS;
        if(x>=0 && x<COLS && y>=0 && y<ROWS) {
            screen[x][y] = c;
        }
    }

    @Override
    public void clear(int x, int y) {
        set(x,y, " ");
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
        for (int col = x; col < x + width; col++) {
            for (int row = y; row < y + height; row++) {
                set(col, row, c);
            }
        }
    }

    @Override
    public void box(int x, int y, int width, int height) {
        fill(x, y, width, 1, "-");
        fill(x, y + height, width, 1,  "-");
        fill(x, y, 1, height,  "|");
        fill(x + width, y, 1, height,  "|");
        set(x, y, "+");
        set(x, y + height, "+");
        set(x + width, y, "+");
        set(x + width, y + height, "+");
    }
}
