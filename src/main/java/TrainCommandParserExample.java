import java.io.IOException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainCommandParserExample {
    static Logger log = LoggerFactory.getLogger(TrainCommandParserExample.class);

    public static void main(String[] args) throws IOException {
        CharStream input = CharStreams.fromFileName("commands.txt");
        TrainCommandLexer lexer = new TrainCommandLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TrainCommandParser parser = new TrainCommandParser(tokens);

        // Obtener el árbol de análisis sintáctico
        TrainCommandParser.StartContext tree = parser.start();

        // Crear un Visitor personalizado para procesar los comandos
        TrainCommandVisitor visitor = new TrainCommandVisitor();

        // Visitar el árbol utilizando el Visitor personalizado
        visitor.visit(tree);
    }

    static class TrainCommandVisitor extends TrainCommandBaseVisitor<Void> {
        private String sensorId;
        private String trainId;
        private String trainAction;
        private String trainDir;

        @Override
        public Void visitCommand(TrainCommandParser.CommandContext ctx) {
            sensorId = ctx.NUMBER(0).getText();
            trainId = ctx.NUMBER().size() > 1 ? ctx.NUMBER(1).getText() : "";
            trainAction = ctx.trainAction() != null ? ctx.trainAction().getText() : "";
            trainDir = ctx.dir() != null ? ctx.dir().getText() : "";

            // System.out.println("Sensor NUM: " + sensorId);
            // System.out.println("Train NUM: " + trainId);
            // System.out.println("Train Action: " + trainAction);
            // System.out.println("DIR: " + trainDir);
            // System.out.println();

            return super.visitCommand(ctx);
        }

        @Override
        public Void visitCommandItem(TrainCommandParser.CommandItemContext ctx) {

            if (ctx.semaphoreAction() != null) {
                String semaphoreNum = ctx.NUMBER().getText();
                String action = ctx.semaphoreAction().getText();

                System.out.println("Semaphore NUM: " + semaphoreNum);
                System.out.println("Action: " + action);
                System.out.println();
            } else if (ctx.dir() != null) {
                String forkNum = ctx.NUMBER().getText();
                String direction = ctx.dir().getText();

                System.out.println("Fork NUM: " + forkNum);
                System.out.println("Direction: " + direction);
                System.out.println();
            } else if (ctx.speedLimit() != null) {
                String limit = ctx.speedLimit().getText();
                String speed = ctx.NUMBER().getText();

                System.out.println("Speed Limit: " + limit);
                System.out.println("Speed: " + speed);
                System.out.println();
            }

            return super.visitCommandItem(ctx);
        }
    }
}
