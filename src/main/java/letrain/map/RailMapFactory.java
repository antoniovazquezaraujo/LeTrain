package letrain.map;

import letrain.mvp.Model;
import letrain.mvp.impl.CompactPresenter;
import letrain.track.Track;
import letrain.track.rail.*;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RailMapFactory {
    final int MAP_COORDS = 0;
    final int MAP_DIR_MOVEMENT = 1;
    final int MAP_CURVE_MOVEMENT = 2;
    private Model model;
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.N;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;

    boolean makingTraks = false;

    public RailMapFactory(Model model ) {
        this.model = model;
    }
    public Model getModel() {
        return model;
    }

    public void cursorForward() {
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
    }
    public void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }
    public void cursorTurnLeft() {
        if (makingTraks) {
            if (degreesOfRotation <= 0) {
                this.dir = this.dir.turnLeft();
                model.getCursor().setDir(this.dir);
                degreesOfRotation += 1;
            }
        } else {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
        }
    }
    public void cursorTurnRight() {
        if (makingTraks) {
            if (degreesOfRotation >= 0) {
                this.dir = this.dir.turnRight();
                model.getCursor().setDir(this.dir);
                degreesOfRotation -= 1;
            }
        } else {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
        }
    }
    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STOP_TRACK:
                return new StopRailTrack();
            case TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case TUNNEL_GATE:
                return new TunnelRailTrack();
            case NORMAL_TRACK:
            default:
                return new RailTrack();
        }
    }
    public void selectNewTrackType(CompactPresenter.TrackType type) {
        this.newTrackType = type;
    }
    public CompactPresenter.TrackType getNewTrackType() {
        return this.newTrackType;
    }

    public boolean makeTrack() {
        makingTraks = true;
        Point cursorPosition = model.getCursor().getPosition();
        Dir dir = model.getCursor().getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

        //Obtenemos el track bajo el cursor
        RailTrack track = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            //si no había nada creamos un track normal
            track =createTrackOfSelectedType();
        } else {
            // si había un fork no seguimos
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        // al track que había (o al que hemos creado normal) le agregamos la ruta entre la vieja dir y la nueva
        track.addRoute(oldDir, dir);
        track.setPosition(cursorPosition);
        model.getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            myNewTrack.setPosition(cursorPosition);
            model.addFork(myNewTrack);
            final Router router = track.getRouter();
            router.forEach(t -> {
                myNewTrack.addRoute(t.getKey(), t.getValue());
            });
            myNewTrack.setNormalRoute();
            model.getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            model.getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
            for (Dir d : Dir.values()) {
                if (track.getConnectedTrack(d) != null) {
                    Track connected = track.getConnectedTrack(d);
                    connected.disconnectTrack(d.inverse());
                    connected.connectTrack(d.inverse(), myNewTrack);
                    myNewTrack.connectTrack(d, connected);
                }
            }
            track = myNewTrack;
//            myNewTrack.setAlternativeRoute();
        }
        if (oldTrack != null) {
            //conectamos el track con oldTrack en oldDir, bien.
            track.connectTrack(oldDir, oldTrack);
            //conectamos a oldTrack con track, en la inversa
            oldTrack.connectTrack(track.getDirWhenEnteringFrom(dir).inverse(), track);
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        oldTrack = track;
        return true;
    }
    public void removeTrack() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            model.getRailMap().removeTrack(position.getX(), position.getY());
        }
        if (model.getForks().contains(track)) {
            model.getForks().remove(track);
        }
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point p = model.getCursor().getPosition();
    }
    public void reset() {
        degreesOfRotation = 0;
        dir = model.getCursor().getDir();
        oldTrack = null;
        oldDir = dir;
        reversed = false;
    }
    public void createTrack() {
        degreesOfRotation = 0;
        makeTrack();
    }
    public void read(String definition) {
        StringTokenizer tokenizer = new StringTokenizer(definition, " ");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            switch (tokenType(token)) {
                case MAP_COORDS: {
                    StringTokenizer t = new StringTokenizer(token, ",");
                    int x = Integer.valueOf(t.nextToken());
                    int y = Integer.valueOf(t.nextToken());
                    model.getCursor().setPosition(new Point(x, y));
                }
                break;
                case MAP_DIR_MOVEMENT: {
                    String regexp = "(\\D{1,2})(\\d+)";
                    Matcher matcher = Pattern
                            .compile(regexp, Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS)
                            .matcher(token);
                    if (matcher.find()) { // the author is NOT alone in the comment
                        Dir dir = Dir.fromString(matcher.group(1));
                        int distance = Integer.valueOf(matcher.group(2));
                        model.getCursor().setDir(dir);
                        while (distance > 0) {
                            makeTrack();
                            distance--;
                        }
                    }
                }
                break;
                case MAP_CURVE_MOVEMENT: {
                    String regexp = "(l|r)(\\d+)";
                    Matcher matcher = Pattern
                            .compile(regexp, Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS)
                            .matcher(token);
                    if (matcher.find()) { // the author is NOT alone in the comment
                        final String dir = matcher.group(1);
                        int distance = Integer.valueOf(matcher.group(2));
                        switch (dir) {
                            case "l":
                                model.getCursor().rotateLeft();
                                break;
                            case "r":
                                model.getCursor().rotateRight();
                                break;
                        }
                        while (distance > 0) {
                            makeTrack();
                            distance--;
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
            return MAP_COORDS;
        }
        if ("nsew".contains(firstChar)) {
            return MAP_DIR_MOVEMENT;
        }
        if ("lr".contains(firstChar)) {
            return MAP_CURVE_MOVEMENT;
        }
        return -1;
    }
    public boolean isMakingTraks() {
        return makingTraks;
    }

    public void setMakingTraks(boolean makingTraks) {
        this.makingTraks = makingTraks;
    }
}
