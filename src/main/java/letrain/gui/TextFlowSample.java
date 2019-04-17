package letrain.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class TextFlowSample extends Application {

    public static void main(String[] args) {
        Application.launch(args);
    }

    List<TextFlow> textFlows = new ArrayList<>();
    @Override
    public void start(Stage stage) {
        for(int n=0; n<10; n++){
            textFlows.add(new TextFlow());
        }
        VBox box = new VBox();
        box.getChildren().addAll(textFlows);

        textFlows.get(4).getChildren().add(new Text("hola"));
        String log = ">> Sample passed \n";
        Text t1 = new Text();
        t1.setStyle("-fx-fill: #4F8A10;-fx-font-weight:bold;");
        t1.setText("Que pasa por este lado");
        textFlows.get(6).getChildren().add(t1);
        stage.setScene(new Scene(box, 300, 250));
        stage.show();
    }
}