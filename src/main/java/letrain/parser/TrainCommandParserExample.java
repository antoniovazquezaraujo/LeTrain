package letrain.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class TrainCommandParserExample {
    static Logger log = LoggerFactory.getLogger(TrainCommandParserExample.class);

    public static void main(String[] args) throws IOException {
        CharStream input = CharStreams.fromFileName("commands.txt");
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
                        String semaphorePosition = ctx.getChild(3).getText();
                        System.out.println("semaphoreCommand: {}" + semaphoreCommand + ", " + semaphorePosition);
                        break;
                    case "fork":
                        String forkPosition = ctx.getChild(3).getText();
                        String forkCommand = ctx.getChild(2).getText();
                        System.out.println("forkCommand: {}" + forkCommand + ", " + forkPosition);
                        break;
                    case "train":
                        String trainCommand = ctx.getChild(2).getText();
                        String trainValue = ctx.getChild(3).getText();
                        System.out.println("trainCommand: {}" + trainCommand + ", " + trainValue);

                        break;
                }
            }
        };
        walker.walk(listener, tree);
    }
}
