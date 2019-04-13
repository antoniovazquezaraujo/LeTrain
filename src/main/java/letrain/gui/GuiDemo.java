package letrain.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.map.RailTrackMaker;
import letrain.track.rail.RailTrack;
import letrain.tui.GraphicConverter;
import letrain.tui.RailTrackGraphicConverter;

public class GuiDemo extends Application {
    public static void main(String[] args) {
        launch(args);

    }

    @Override
    public void start(Stage stage) throws Exception {
        Gui gui = new Gui();
        RailMap map = new RailMap();
        GraphicConverter<RailTrack> converter = new RailTrackGraphicConverter();
        RailTrackMaker maker = new RailTrackMaker();
        maker.setMap(map);
        maker.setDirection(Dir.N);
        maker.setPosition(20,20);
        maker.setMode(RailTrackMaker.Mode.MAP_WALK);

        gui.addListener(keyEvent -> {
            switch (keyEvent.getCode()) {
                case ENTER:
                    break;
                case UP:
                    maker.advance();
                    break;
                case DOWN:
                    maker.reverse();
                    maker.advance();
                    maker.reverse();
                    break;
                case LEFT:
                    maker.rotateLeft();
                    break;
                case RIGHT:
                    maker.rotateRight();
                    break;
                case M:
                    maker.setMode(RailTrackMaker.Mode.MAP_WALK);
                    break;
                case T:
                    maker.setMode(RailTrackMaker.Mode.TRACK_WALK);
                    break;
                case P:
                    maker.setMode(RailTrackMaker.Mode.MAKE_TRACK);
                    break;
                case R:
                    maker.setMode(RailTrackMaker.Mode.REMOVE_TRACK);
                    break;
                case Q:
                    System.exit(0);
                    break;
                default:
                    break;
            }
            gui.clear();
            map.forEach(t ->{
                gui.set(
                        t.getPosition().getX(),
                        t.getPosition().getY(),
                        converter.getAspect(t));
            });
            gui.set(maker.getPosition().getX(), maker.getPosition().getY(), (char)'@');
            gui.paint();
        });
        GridPane pane = new GridPane();
        pane.getChildren().add(gui);
        final Scene scene = new Scene(pane, 800, 800);
        stage.setScene(scene);
        stage.show();
    }
}
