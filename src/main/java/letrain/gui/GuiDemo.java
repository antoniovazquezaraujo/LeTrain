package letrain.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailTrackMaker;
import letrain.sim.Game;
import letrain.sim.Sim;
import letrain.track.rail.RailTrack;
import letrain.tui.GraphicConverter;
import letrain.tui.RailTrackGraphicConverter;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class GuiDemo extends Application {
    private Sim sim;
    private Gui gui;
    RailTrackMaker maker;

    public static void main(String[] args) {
        launch(args);
    }
    public GuiDemo(){
        sim = new Game();
        gui = new Gui();
        this.maker = new RailTrackMaker();
        this.maker.setMap(sim.getMap());
        this.maker.setDirection(Dir.N);
        this.maker.setPosition(10,10);
        this.maker.setMode(RailTrackMaker.Mode.MAP_WALK);
    }

    @Override
    public void start(Stage stage) throws Exception {
        GraphicConverter<RailTrack> converter = new RailTrackGraphicConverter();

        gui.addListener(keyEvent -> {
            switch (keyEvent.getCode()) {
                case ENTER:
                    break;
                case UP:
                    this.maker.advance();
                    break;
                case DOWN:
                    this.maker.reverse();
                    this.maker.advance();
                    this.maker.reverse();
                    break;
                case LEFT:
                    this.maker.rotateLeft();
                    break;
                case RIGHT:
                    this.maker.rotateRight();
                    break;
                case M:
                    this.maker.setMode(RailTrackMaker.Mode.MAP_WALK);
                    break;
                case T:
                    this.maker.setMode(RailTrackMaker.Mode.TRACK_WALK);
                    break;
                case P:
                    this.maker.setMode(RailTrackMaker.Mode.MAKE_TRACK);
                    break;
                case R:
                    this.maker.setMode(RailTrackMaker.Mode.REMOVE_TRACK);
                    break;
                case W:
                    addTrain(this.maker.getPosition());
                    break;
                case F:
                    this.sim.moveTrains();
                    break;
                case Q:
                    System.exit(0);
                    break;
                default:
                    break;
            }
            gui.clear();
            sim.getMap().forEach(t ->{
                gui.set(
                        t.getPosition().getX(),
                        t.getPosition().getY(),
                        converter.getAspect(t));
            });
//            vehicles.forEach(t ->{
//                gui.set(
//                        t.getPosition().getX(),
//                        t.getPosition().getY(),
//                        '0');
//            });
            gui.set(this.maker.getPosition().getX(), this.maker.getPosition().getY(), (char)'@');
            gui.paint();
        });
        GridPane pane = new GridPane();
        pane.getChildren().add(gui);
        final Scene scene = new Scene(pane, 800, 800);
        stage.setScene(scene);
        stage.show();
    }

    private void addTrain(Point position) {
        RailTrack track = sim.getMap().getTrackAt(position);
        Linker wagon = new Wagon();
//        track.enterLinker(this.maker.getDirection().inverse(), wagon);
        Train train = new Train();
        train.pushBack(wagon);
        sim.addTrain(train);
//        vehicles.add(wagon);
    }
}
