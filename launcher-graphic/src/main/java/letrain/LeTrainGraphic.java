package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.graphic.GraphicPresenter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;

public class LeTrainGraphic {
    public static void main(String[] args) {
        Model model = new Model();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("LeTrain Editor " + letrain.BuildInfo.versionTag() + " (3D)");
        // Start in borderless fullscreen: the window appears at full size immediately, with no
        // maximize animation (which otherwise flashes a small window first).
        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        GraphicPresenter view3D = new GraphicPresenter(model);
        new Lwjgl3Application(view3D, config);
    }
}
