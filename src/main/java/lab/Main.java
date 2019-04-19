package lab;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {

        BorderPane mainPane = new BorderPane();

        MenuBar menuBar = new MenuBar();

        Menu editMenu = new Menu("File");
        MenuItem fileMenuItem1 = new MenuItem("Item 1");
        MenuItem fileMenuItem2 = new MenuItem("Item 2");
        MenuItem fileMenuItem3 = new MenuItem("Item 3");
        editMenu.getItems().addAll(fileMenuItem1, fileMenuItem2, fileMenuItem3);

        Menu fileMenu = new Menu("Edit");
        MenuItem fileMenu1 = new MenuItem("Item 1");
        MenuItem fileMenu2 = new MenuItem("Item 2");
        MenuItem fileMenu3 = new MenuItem("Item 3");
        fileMenu.getItems().addAll(fileMenu1, fileMenu2, fileMenu3);

        menuBar.getMenus().addAll(editMenu, fileMenu);

        mainPane.setTop(menuBar);

        Scene scene = new Scene(mainPane, 300, 300);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();

        // Find menubar HBox holding the menus
        HBox container = (HBox) menuBar.lookup("HBox");

        for(int i = 0 ; i < container.getChildren().size() ; i++) {
            Node parentNode = container.getChildren().get(i);
            Menu menu = menuBar.getMenus().get(i);

            parentNode.setOnMouseMoved(e->{
                menu.show();
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}