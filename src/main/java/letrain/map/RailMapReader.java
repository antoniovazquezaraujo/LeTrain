package letrain.map;

import letrain.mvp.impl.CompactPresenter;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;
import javafx.util.Pair;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RailMapReader {
    final int MAP_COORDS = 0;
    final int MAP_DIR_MOVEMENT = 1;
    final int MAP_CURVE_MOVEMENT = 2;
    Cursor cursor = new Cursor();
    RailMap railMap = new RailMap();
    RailTrack oldTrack;
    Dir oldDir;
    boolean reversed;
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;

    public RailMap getRailMap(){
        return railMap;
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
                    cursor.setPosition(new Point(x, y));
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
                        cursor.setDir(dir);
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
                                cursor.rotateLeft();
                                break;
                            case "r":
                                cursor.rotateRight();
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

    private boolean makeTrack() {
        Point cursorPosition = cursor.getPosition();
        Dir dir = cursor.getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = cursor.getDir().inverse();
            } else {
                oldDir = cursor.getDir();
            }
        }

        RailTrack track = railMap.getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            track = createTrackOfSelectedType();
        } else {
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        track.addRoute(oldDir, dir);
        track.setPosition(cursorPosition);
        if (oldTrack != null) {
            track.connect(oldDir, oldTrack);
            oldTrack.connect(track.getDirWhenEnteringFrom(dir).inverse(), track);
        }
        railMap.addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            final Router router = track.getRouter();
            router.forEach((Pair<Dir, Dir> t) ->
                    myNewTrack.getRouter().addRoute(t.getKey(), t.getValue())
            );
            myNewTrack.setNormalRoute();
            railMap.removeTrack(track.getPosition().getX(), track.getPosition().getY());
            railMap.addTrack(cursor.getPosition(), myNewTrack);
            for (Dir d : Dir.values()) {
                if (track.getConnected(d) != null) {
                    myNewTrack.connect(d, track.getConnected(d));
                }
            }
            myNewTrack.setAlternativeRoute();
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(cursor.getDir(), 1);
        } else {
            newPos.move(cursor.getDir().inverse());
        }
        cursor.setPosition(newPos);
        oldTrack = track;
        return true;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STOP_TRACK:
                return new StopRailTrack();
            case TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case TUNNEL_GATE:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
}
