package letrain;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.map.RailMapFactory;
import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.View;
import letrain.vehicle.impl.rail.TrainFactory;

public class LeTrain extends Application {

    private letrain.mvp.Model model= null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        model = new Model();
        RailMapFactory railMapFactory = new RailMapFactory(model);
                      railMapFactory.read("30,20 e30 r1 r1 r1 r35 r1 r1 r1 r5 l5 r5 r5 l3");
        //   railMapFactory.read("30,20 e1 r1 r50 r1 r35 r1 r1 r1 r5 l5 r5 r5 l3");
        TrainFactory trainFactory = new TrainFactory(model);
        trainFactory.read("50,20 w #LetrainX");
        trainFactory.read("35,20 w #Other");
        presenter = new CompactPresenter((Model) model);
        GridPane pane = new GridPane();
        pane.getChildren().add((View) presenter.getView());
        final Scene scene = new Scene(pane);
        stage.setTitle("LeTrain, the letter train simulator");
        stage.setScene(scene);
        stage.show();
         presenter.start();
    }

    @Override
    public void init() throws Exception {
//        FileInputStream fis = null;
//        ObjectInputStream in = null;
//        try {
//            File file = new File("TestCircuit1.sav");
//            if(!file.exists()){
//                return;
//            }
//            fis = new FileInputStream("TestCircuit1.sav");
//            in = new ObjectInputStream(fis);
//            this.model = (Model) in.readObject();
//            in.close();
//        } catch (Exception ex) {
//            this.model = null;
//            ex.printStackTrace();
//        }
    }

    @Override
    public void stop() throws Exception {
//        String fileName = "LeTrain.sav";
//        FileOutputStream fos = null;
//        ObjectOutputStream out = null;
//        try {
//            fos = new FileOutputStream(fileName);
//            out = new ObjectOutputStream(fos);
//            out.writeObject(presenter.getModel());
//            out.close();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
    }
}
