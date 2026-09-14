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
        config.setMaximized(true);
        // Create the window hidden so it never flashes at its default size; GraphicPresenter.create()
        // shows it once the first frame is ready.
        config.setInitialVisible(false);
        GraphicPresenter view3D = new GraphicPresenter(model);
        new Lwjgl3Application(view3D, config);
    }
}
