package letrain.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;

public class TrainCommandParserExample {

    public static void main(String[] args) throws IOException {
        CharStream codePointCharStream = CharStreams.fromFileName("commands.txt");
        TrainCommandLexer lexer = new TrainCommandLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TrainCommandParser parser = new TrainCommandParser(tokens);

        ParseTree tree = parser.start();
        ParseTreeWalker walker = new ParseTreeWalker();
        TrainCommandBaseListener listener = new TrainCommandBaseListener() {
            @Override
            public void enterCommandItem(TrainCommandParser.CommandItemContext ctx) {
                String command = ctx.getChild(0).getText();
                String id = ctx.getChild(1).getText();
                switch (command) {
                    case "semaphore":
                        String semaphoreCommand = ctx.getChild(2).getText();
                        System.out.println("Semaphore " + id + " " + semaphoreCommand);
                        break;
                    case "fork":
                        String forkPosition = ctx.getChild(3).getText();
                        System.out.println("Fork " + id + " " + forkPosition);
                        break;
                    case "train":
                        String trainCommand = ctx.getChild(2).getText();
                        String trainValue = ctx.getChild(3).getText();
                        System.out.println("Train " + trainCommand + " " + trainValue);
                        break;
                }
            }
        };
        walker.walk(listener, tree);
    }
}
