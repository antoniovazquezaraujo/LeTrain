package letrain;

/**
 * Entry point of the {@code letrain-check} executable: validates a scenario {@code .ltr} (syntax)
 * without starting the game. Usage: {@code letrain-check file.ltr} (a leading {@code --check} is
 * accepted too). See {@link letrain.command.ScenarioCheckCli} for exit codes and output format.
 */
public class LeTrainCheck {

    public static void main(String[] args) {
        System.exit(letrain.command.ScenarioCheckCli.run(args));
    }
}
