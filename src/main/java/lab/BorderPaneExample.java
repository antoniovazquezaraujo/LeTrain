package lab;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BorderPaneExample extends Application {

    enum MenuCommand{
        NAVIGATE_MAP_COMMAND ("Navigate map"),
        CREATE_NORMAL_TRACKS_COMMAND("Create normal tracks"),
        CREATE_LOAD_PLATFORM_COMMAND("Create load platform"),
        CREATE_FACTORY_PLATFORM_COMMAND("Create factory platform"),
        REMOVE_TRACKS_COMMAND("Remove tracks"),
        USE_TRAINS_COMMAND("Use trains"),
        USE_FORKS_COMMAND("Use forks"),
        USE_LOAD_PLATFORMS_COMMAND("Use load platforms"),
        USE_FACTORY_PLATFORMS_COMMAND("Use factory platforms"),
        QUIT_COMMAN("Quit");
        private String name;
        public String getName(){
            return name;
        }

        private MenuCommand(String name) {
            this.name = name;
        }
    }
    private static final String STYLESHEET_PATH = "/styles.css";

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new Group());
        stage.setTitle("Table View Favorite Sample");
        stage.setWidth(600);
        stage.setHeight(431);

        BorderPane borderPane = new BorderPane();
        borderPane.setPrefHeight(400.0);
        borderPane.setPrefWidth(600.0);

        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("Actions");
        menuBar.getMenus().add(menu);
        for(MenuCommand option: MenuCommand.values()){
            MenuItem item = new MenuItem(option.getName());
            menu.getItems().add(item);
            item.setOnAction(t->{doCommand(option);});
        }
        borderPane.setTop(menuBar);
        ((Group) scene.getRoot()).getChildren().addAll(borderPane);

        stage.setScene(scene);
        stage.show();
    }
    public void doCommand(MenuCommand command){
        System.out.println(command);

    }
    public static void main(String[] args) {
        launch(args);
    }
}