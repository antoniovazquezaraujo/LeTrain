package lab;

import javafx.application.*;
import javafx.stage.*;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
public class ToolBarGame extends Application
{
    public static void main(String[] args)
    {
        launch(args);
    }
    @Override public void start(Stage primaryStage)
    {
        Button btnNew = new Button("New");
        Button btnPause = new Button("Pause");
        Button btnQuit = new Button("Quit");
        CheckBox chkSound = new CheckBox("Sound");
        CheckBox chkMusic = new CheckBox("Music");
        RadioButton rdoEasy = new RadioButton("Easy");
        RadioButton rdoMedium = new RadioButton("Medium");
        RadioButton rdoHard = new RadioButton("Hard");
        ToggleGroup groupDifficulty = new ToggleGroup();
        groupDifficulty.getToggles().addAll(
                rdoEasy,
                rdoMedium,
                rdoHard
            );
        ToolBar toolBar1 = new ToolBar();
        toolBar1.getItems().addAll(
                new Separator(),
                btnNew,
                btnPause,
                btnQuit,
                new Separator(),
                chkSound,
                chkMusic,
                new Separator()
            );
        ToolBar toolBar2 = new ToolBar();
        toolBar2.setOrientation(Orientation.VERTICAL);
        toolBar2.getItems().addAll(
                new Separator(),
                rdoEasy,
                rdoMedium,
                rdoHard,
                new Separator()
            );
        BorderPane pane = new BorderPane();
        pane.setTop(toolBar1);
        pane.setLeft(toolBar2);
        Scene scene = new Scene(pane, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("ToolBar Sample");
        primaryStage.show();
    }
}
