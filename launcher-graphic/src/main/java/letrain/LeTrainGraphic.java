package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.graphic.GraphicPresenter;
import letrain.soundscape.game.SoundscapeAmbience;
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
        SoundscapeAmbience ambience = null;
        try {
            ambience = new SoundscapeAmbience(model);
            ambience.start();
        } catch (Exception e) {
            System.err.println("Ambient audio disabled: " + e.getMessage());
        }
        GraphicPresenter view3D = new GraphicPresenter(model, ambience);
        try {
            new Lwjgl3Application(view3D, config);
        } finally {
            if (ambience != null) {
                ambience.close();
            }
        }
    }
}
