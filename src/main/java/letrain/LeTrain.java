package letrain;

import letrain.mvp.Model;
import letrain.mvp.impl.graphic.GraphicPresenter;
import letrain.mvp.impl.terminal.TerminalPresenter;

public class LeTrain {

    private Model model = null;
    private TerminalPresenter presenter;

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
            com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration config =
                    new com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration();
            config.setTitle("LeTrain 3D - Wooden Edition");
            config.setMaximized(true);
            GraphicPresenter view3D = new GraphicPresenter(this.model);
            new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(view3D, config);
        } else {
            presenter = new TerminalPresenter(this.model);
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        if (presenter != null) {
                                            presenter.stop();
                                        }
                                    }));
            presenter.start();
            presenter.stop();
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        System.exit(0);
    }
}
