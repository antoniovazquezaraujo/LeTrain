package letrain.physics;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class PhysicLab extends Application {

    private PhysicLabSpace model = null;
    private PhysicLabPresenter presenter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        model = new PhysicLabSpace();
        presenter = new PhysicLabPresenter(model);

        GridPane pane = new GridPane();
        pane.getChildren().add(presenter.getView());
        final Scene scene = new Scene(pane);
        stage.setTitle("LeTrain Physic lab");
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
