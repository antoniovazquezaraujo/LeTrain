package letrain.vehicle.impl.rail;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.track.rail.RailTrack;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrainFactory {
    private static final int TRAIN_POSITION_COORDS = 0;
    private static final int TRAIN_DIRECTION = 1;
    private static final int TRAIN_VEHICLES = 2;
    Model model;

    public TrainFactory(Model model) {
        this.model = model;
    }

    public Model getModel() {
        return model;
    }

    public void read(String definition) {
        StringTokenizer tokenizer = new StringTokenizer(definition, " ");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            switch (tokenType(token)) {
                case TRAIN_POSITION_COORDS:
                    StringTokenizer t = new StringTokenizer(token, ",");
                    int x = Integer.valueOf(t.nextToken());
                    int y = Integer.valueOf(t.nextToken());
                    model.getCursor().setPosition(new Point(x, y));
                    break;
                case TRAIN_DIRECTION: {
                    String regexp = "(\\D{1,2})";
                    Matcher matcher = Pattern
                            .compile(regexp, Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS)
                            .matcher(token);
                    if (matcher.find()) {
                        Dir dir = Dir.fromString(matcher.group(1));
                        model.getCursor().setDir(dir);
                    }
                    break;
                }
                case TRAIN_VEHICLES: {
                    String regexp = "(#)(.+)";
                    Matcher matcher = Pattern
                            .compile(regexp, Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS)
                            .matcher(token);
                    if (matcher.find()) {
                        final String vehicles = matcher.group(2);
                        char[] array = vehicles.toCharArray();
                        RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
                        for (char c : array) {
                            if (Character.isUpperCase(c)) {
                                createLocomotive(String.valueOf(c), track);
                            } else {
                                createWagon(String.valueOf(c), track);
                            }
                            model.getCursor().getPosition().move(model.getCursor().getDir());
                        }
                    }
                }
                break;
            }
        }
    }


    private int tokenType(String token) {
        String firstChar = token.substring(0, 1);
        if (Character.isDigit(firstChar.charAt(0))) {
            return TRAIN_POSITION_COORDS;
        }
        if ("nsew".contains(firstChar)) {
            return TRAIN_DIRECTION;
        }
        if ("#".contains(firstChar)) {
            return TRAIN_VEHICLES;
        }
        return -1;
    }

    /***********************************************************
     * FACTORIES
     **********************************************************/

    Train newTrain;

    private Train getNewTrain() {
        if (newTrain == null) {
            newTrain = new Train();
            model.addTrain(newTrain);
        }
        return newTrain;
    }

    private void createWagon(String c, RailTrack track) {
        Wagon wagon = new Wagon(c);
        getNewTrain().pushBack(wagon);
        track.enterLinkerFromDir(Dir.E, wagon);
    }

    private void createLocomotive(String c, RailTrack track) {
        Locomotive locomotive = new Locomotive(c);
        getNewTrain().pushBack(locomotive);
        track.enterLinkerFromDir(Dir.E, locomotive);
        if (getNewTrain().getDirectorLinker() == null) {
            getNewTrain().assignDefaultDirectorLinker();
        }
    }
}
