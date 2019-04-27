package letrain;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.mvp.GameModel;
import letrain.mvp.impl.LeTrainModel;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.mvp.impl.LeTrainView;

import java.io.*;

public class LeTrain extends Application {

    private GameModel model= null;
    private LeTrainPresenter presenter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        presenter = new LeTrainPresenter((LeTrainModel) model);
        GridPane pane = new GridPane();
        pane.getChildren().add((LeTrainView) presenter.getView());
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
//            this.model = (GameModel) in.readObject();
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
