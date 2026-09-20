package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.terminal.TerminalPresenter;
import letrain.soundscape.game.SoundscapeAmbience;

public class LeTrainTerminal {
    public static void main(String[] args) {
        Model model = new Model();
        SoundscapeAmbience ambience = null;
        try {
            ambience = new SoundscapeAmbience(model);
            ambience.start();
        } catch (Exception e) {
            System.err.println("Ambient audio disabled: " + e.getMessage());
        }
        TerminalPresenter presenter = new TerminalPresenter(model, ambience);
        final SoundscapeAmbience ownedAmbience = ambience;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (presenter != null) {
                presenter.stop();
            }
            if (ownedAmbience != null) {
                ownedAmbience.close();
            }
        }));
        presenter.start();
        presenter.stop();
        if (ambience != null) {
            ambience.close();
        }
        System.exit(0);
    }
}
