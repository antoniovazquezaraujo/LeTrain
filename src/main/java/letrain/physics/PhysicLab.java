package letrain.physics;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.map.RailMapFactory;
import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.View;
import letrain.vehicle.impl.rail.TrainFactory;

public class PhysicLab extends Application {

    private PhysicModel model= null;
    private BasicPhysicPresenter presenter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        model = new BasicPhysicModel();
        presenter = new BasicPhysicPresenter((PhysicModel) model);

        GridPane pane = new GridPane();
        pane.getChildren().add(presenter.getView());
        final Scene scene = new Scene(pane);
        stage.setTitle("LeBody, physic lab");
        stage.setScene(scene);
        stage.show();
        presenter.start();
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void stop() throws Exception {
    }
}
