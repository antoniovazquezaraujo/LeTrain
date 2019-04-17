package letrain.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class AnimatedSort extends Application {
    final int SCALE = 16;

    final int WIDTH = 64;
    final int HEIGHT = 32;

    int[][] pixels;

    Timeline sortLoop;

    int[] myArray = {32, 28, 22, 20, 16, 13, 10, 9, 5, 3};


    @Override
    public void start(Stage primaryStage) throws Exception {
        pixels = new int[WIDTH][HEIGHT];

//        int[] myArray = new int[WIDTH];
//
//        
//        Random rand = new Random();
//        for (int i = 0; i < WIDTH; i++)
//        {
//            myArray[i] = rand.nextInt((HEIGHT) + 1);
//        }

        Text text1 = new Text("Big italic red text");
        text1.setFill(Color.RED);
        text1.setFont(Font.font("Helvetica", FontPosture.ITALIC, 20));
        Text text2 = new Text(" little bold blue text");
        text2.setFill(Color.BLUE);
        text2.setFont(Font.font("monospace", FontWeight.BOLD, 20));
        TextFlow textFlow = new TextFlow(text1, text2);
        Canvas display = new Canvas(WIDTH * SCALE, HEIGHT * SCALE);

        GraphicsContext gc = display.getGraphicsContext2D();

        GridPane grid = new GridPane();
        grid.add(display, 0, 0);
        grid.add(textFlow, 0,1);

        primaryStage.setTitle("Sort");
        primaryStage.setScene(new Scene(grid, WIDTH * SCALE, HEIGHT * SCALE));
        primaryStage.show();

        sortLoop = new Timeline();
        sortLoop.setCycleCount(Timeline.INDEFINITE);

        // Sort array
        for (int index = 0; index < myArray.length - 1; index++) {

            final int i = index;

            KeyFrame kf = new KeyFrame(Duration.seconds(i + 1), actionEvent -> {

                if (myArray[i] > myArray[i + 1]) {
                    int swap = myArray[i];
                    myArray[i] = myArray[i + 1];
                    myArray[i + 1] = swap;
//                    i = -1;
                }

                // Clear screen by zeroing out pixel array
                for (int k = 0; k < WIDTH; k++) {
                    for (int j = 0; j < HEIGHT; j++) {
                        pixels[k][j] = 0;
                    }
                }

                // Draw array with vertical bars (assign values to canvas array)
                for (int k = 0; k < myArray.length; k++) {
                    for (int j = (HEIGHT - 1); j > ((HEIGHT - myArray[k]) - 1); j--) {
                        pixels[k][j] = 1;
                    }
                }

                // Render canvas
                for (int k = 0; k < WIDTH; k++) {
                    for (int j = 0; j < HEIGHT; j++) {
                        if (pixels[k][j] == 1) {
                            gc.setFill(Color.WHITE);
                            gc.fillRect((k * SCALE), (j * SCALE), SCALE, SCALE);
                        } else {
                            gc.setFill(Color.BLACK);
                            gc.fillRect((k * SCALE), (j * SCALE), SCALE, SCALE);
                        }
                    }
                }

            });

            sortLoop.getKeyFrames().add(kf);
        }

        sortLoop.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}