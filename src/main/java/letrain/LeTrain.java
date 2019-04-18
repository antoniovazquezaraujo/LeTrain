package letrain;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.mvp.impl.LeTrainView;

public class LeTrain extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        LeTrainPresenter presenter = new LeTrainPresenter();
        GridPane pane = new GridPane();
        pane.getChildren().add((LeTrainView) presenter.getView());
        final Scene scene = new Scene(pane);
        stage.setTitle("LeTrain, the letter train simulator");
        stage.setScene(scene);
        stage.show();
        presenter.start();
    }
}
