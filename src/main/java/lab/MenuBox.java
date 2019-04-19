package lab;

import javafx.animation.Animation;
import javafx.animation.Transition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class MenuBox extends VBox {
    private double expandedSize = 200;

    public MenuBox(Node node) {
        this.expandedSize = expandedSize;
        setVisible(false);
        setPrefWidth(0);
        setMinWidth(0);
        getChildren().add(node);
    }
    public void togglePanelStatus() {
        final Animation hidePanel = new Transition() {
            {
                setCycleDuration(Duration.millis(150));
            }

            @Override
            protected void interpolate(double frac) {
                final double size = expandedSize * (1.0 - frac);
                setPrefWidth(size);
            }
        };

        hidePanel.onFinishedProperty().set(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                setVisible(false);
            }
        });

        final Animation showPanel = new Transition() {
            {
                setCycleDuration(Duration.millis(150));
            }

            @Override
            protected void interpolate(double frac) {
                final double size = expandedSize * frac;
                setPrefWidth(size);

            }
        };

        if (showPanel.statusProperty().get() == Animation.Status.STOPPED
                && hidePanel.statusProperty().get() == Animation.Status.STOPPED) {
            if (isVisible()) {
                hidePanel.play();
            } else {
                setVisible(true);
                showPanel.play();
            }
        }
    }
}