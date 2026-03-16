package letrain;

import letrain.mvp.Model;
import letrain.mvp.impl.terminal.Presenter2D;
import letrain.mvp.impl.gdx3d.Presenter3D;

public class LeTrain {

    private Model model = null;
    private Presenter2D presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        if (this.model == null) {
            this.model = new letrain.mvp.impl.Model();
        }

        boolean use3D = false;
        for (String arg : args) {
            if ("--3d".equals(arg)) {
                use3D = true;
                break;
            }
        }

        if (use3D) {
            com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration config = new com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration();
            config.setTitle("LeTrain 3D - Wooden Edition");
            config.setWindowedMode(1280, 720);
            Presenter3D view3D = new Presenter3D(this.model);
            new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(view3D, config);
        } else {
            presenter = new Presenter2D(this.model);
            presenter.start();
            presenter.stop();
        }
        System.exit(0);
    }
}
