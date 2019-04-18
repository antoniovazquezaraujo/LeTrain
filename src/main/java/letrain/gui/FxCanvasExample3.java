package letrain.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
 
public class FxCanvasExample3 extends Application
{
    public static void main(String[] args) 
    {
        Application.launch(args);
    }
     
    @Override
    public void start(Stage stage) 
    {
        // Create the Canvas
        Canvas canvas = new Canvas(400, 200);
        // Get the graphics context of the canvas
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Set line width
        gc.setLineWidth(1.0);
        // Set fill color
        gc.setFill(Color.BLUE);
        gc.setFont(new Font("Monospace", 40));
         
        // Draw a Text
        gc.strokeText("This is a stroked Text", 10, 50);
        gc.strokeText("This is a stroked Text with Max Width 300 px", 10, 100, 300);
        // Draw a filled Text
        gc.fillText("This is a filled Text", 10, 150);
        gc.fillText("This is a filled Text with Max Width 400 px", 10, 200, 400);
         
        // Create the Pane
        Pane root = new Pane();
        // Set the Style-properties of the Pane
        root.setStyle("-fx-padding: 10;" +
                "-fx-border-style: solid inside;" +
                "-fx-border-width: 2;" +
                "-fx-border-insets: 5;" +
                "-fx-border-radius: 5;" +
                "-fx-border-color: blue;");
         
        // Add the Canvas to the Pane
        root.getChildren().add(canvas);
        // Create the Scene
        Scene scene = new Scene(root);
        // Add the Scene to the Stage
        stage.setScene(scene);
        // Set the Title of the Stage
        stage.setTitle("Drawing a Text on a Canvas");
        // Display the Stage
        stage.show();       
    }
}