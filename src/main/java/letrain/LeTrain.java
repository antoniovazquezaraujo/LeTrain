package letrain;

import letrain.mvp.Model;
import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Gdx3DView;

public class LeTrain {

    private Model model = null;
    private CompactPresenter presenter;

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
            Gdx3DView view3D = new Gdx3DView((letrain.mvp.impl.Model) this.model);
            new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(view3D, config);
        } else {
            presenter = new CompactPresenter((letrain.mvp.impl.Model) this.model);
            presenter.start();
            presenter.stop();
        }
    }
}
