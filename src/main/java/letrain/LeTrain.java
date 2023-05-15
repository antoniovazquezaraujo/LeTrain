package letrain;

import java.io.IOException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;

public class LeTrain {

    private letrain.mvp.Model model = null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        try {
            model = new Model();
            model.loadModel("game.ltr");
        } catch (Exception e) {
            model = new Model();
            System.out.println("No game.ltr file found, creating a new one");
        }
        readProgram();
        presenter = new CompactPresenter((Model) model);
        presenter.start();
        model.saveModel("game.ltr");
    }

    public void readProgram() {
        CharStream input = null;
        try {
            input = CharStreams.fromFileName("commands.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
        LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

        // Obtener el árbol de análisis sintáctico
        LeTrainProgramParser.StartContext tree = parser.start();

        // Crear un Visitor personalizado para procesar los comandos
        LeTrainSensorProgramVisitor visitor = new LeTrainSensorProgramVisitor(model);

        // Visitar el árbol utilizando el Visitor personalizado
        visitor.visit(tree);
    }

}
