package letrain.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import letrain.sim.GameModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.tui.SimpleUI;


public class LeTrainView extends BorderPane implements SimpleUI {
    GamePresenter presenter;
    public static final int ROWS = 100;
    public static final int COLS = 100;
    String[][] screen = new String [COLS][ROWS];
    TextArea textArea;
    GridPane grid = new GridPane();


    public LeTrainView(GamePresenter presenter) {
        this.presenter = presenter;
        setPrefSize(800,800);
        Font font = Font.font("Monospace", 20);
        textArea = new TextArea();
        textArea.setFont(font);
        setCenter(textArea);
        textArea.requestFocus();
        addEventListener();
    }

    public void addEventListener() {
        GraphicConverter converter = new BasicGraphicConverter();

        textArea.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            switch (keyEvent.getCode()) {
                case Y:
                    presenter.paintLoop();
                case ENTER:
                    break;
                case UP:
                    presenter.onMakerAdvance();
                    break;
                case DOWN:
                    presenter.onMakerInverse();
                    presenter.onMakerAdvance();
                    presenter.onMakerInverse();
                    break;
                case LEFT:
                    presenter.onMakerTurnLeft();
                    break;
                case RIGHT:
                    presenter.onMakerTurnRight();
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
        for (double row = 0; row < ROWS; row++) {
            for (double col = 0; col < COLS; col++) {
                screen[(int)col][(int)row] = " ";
            }
        }
    }

    @Override
    public void set(int x, int y, String c) {
        screen[x][y] = c;
    }

    @Override
    public void clear(int x, int y) {
        screen[x][y] = " ";
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
