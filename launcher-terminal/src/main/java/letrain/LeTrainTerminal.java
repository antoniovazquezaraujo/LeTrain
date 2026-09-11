package letrain;

import letrain.mvp.impl.Model;
import letrain.mvp.impl.terminal.TerminalPresenter;

public class LeTrainTerminal {
    public static void main(String[] args) {
        if (letrain.command.ScenarioCheckCli.isCheckInvocation(args)) {
            System.exit(letrain.command.ScenarioCheckCli.run(args));
            return;
        }
        Model model = new Model();
        TerminalPresenter presenter = new TerminalPresenter(model);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (presenter != null) {
                presenter.stop();
            }
        }));
        presenter.start();
        presenter.stop();
        System.exit(0);
    }
}
