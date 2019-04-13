package letrain.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.map.RailTrackMaker;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.tui.GraphicConverter;
import letrain.tui.RailTrackGraphicConverter;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Wagon;

import java.util.ArrayList;
import java.util.List;

public class GuiDemo extends Application {

    private Gui gui;
    private RailMap map;
    private RailTrackMaker maker;
    private List<Linker<Track<RailTrack>>> vehicles;

    public static void main(String[] args) {
        launch(args);

    }

    @Override
    public void start(Stage stage) throws Exception {
        gui = new Gui();
        map = new RailMap();
        vehicles = new ArrayList<>();
        GraphicConverter<RailTrack> converter = new RailTrackGraphicConverter();
        maker = new RailTrackMaker();
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
                case W:
                    addNewWagon(maker.getPosition());
                    break;
                case F:
                    for (Linker<Track<RailTrack>> vehicle : vehicles) {
                        vehicle.advance();
                    }
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
            vehicles.forEach(t ->{
                gui.set(
                        t.getPosition().getX(),
                        t.getPosition().getY(),
                        '0');
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

    private void addNewWagon(Point position) {
        RailTrack track = map.getTrackAt(position);
        Linker<Track<RailTrack>> w = new Wagon();
        track.enterLinker(maker.getDirection().inverse(), w);
        vehicles.add(w);
    }
}
