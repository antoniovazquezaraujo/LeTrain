package letrain;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.View;

public class LeTrain extends Application {

    private letrain.mvp.Model model = null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        model = new Model();
        presenter = new CompactPresenter((Model) model);
        BorderPane pane = new BorderPane();
        pane.setCenter((View) presenter.getView());
        final Scene scene = new Scene(pane);
        stage.setTitle("LeTrain, the letter train simulator");
        stage.setScene(scene);
        stage.show();
        presenter.start();
    }

    @Override
    public void init() throws Exception {
        // FileInputStream fis = null;
        // ObjectInputStream in = null;
        // try {
        // File file = new File("TestCircuit1.sav");
        // if(!file.exists()){
        // return;
        // }
        // fis = new FileInputStream("TestCircuit1.sav");
        // in = new ObjectInputStream(fis);
        // this.model = (Model) in.readObject();
        // in.close();
        // } catch (Exception ex) {
        // this.model = null;
        // ex.printStackTrace();
        // }
    }

    @Override
    public void stop() throws Exception {
        // String fileName = "LeTrain.sav";
        // FileOutputStream fos = null;
        // ObjectOutputStream out = null;
        // try {
        // fos = new FileOutputStream(fileName);
        // out = new ObjectOutputStream(fos);
        // out.writeObject(presenter.getModel());
        // out.close();
        // } catch (Exception ex) {
        // ex.printStackTrace();
        // }
    }
}
