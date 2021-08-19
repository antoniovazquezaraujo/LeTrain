package letrain.physics;

import javafx.scene.paint.Color;
import letrain.map.Dir;
import letrain.mvp.View;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;

public class BasicPhysicVisitor implements PhysicVisitor {
    private static final Color RAIL_TRACK_COLOR = Color.grayRgb(80);
    public static final Color FORK_COLOR = Color.grayRgb(180);
    public static final Color SELECTED_FORK_COLOR = Color.RED;
    Body2D selectedBody;
    ForkRailTrack selectedFork;
    private final View view;

    public BasicPhysicVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(PhysicModel model) {
        if(model.getSelectedBody() != null){
            this.selectedBody= model.getSelectedBody();
            visitBody(model.getSelectedBody());
        }
        model.getBodies().forEach(t -> t.accept(this));
        visitCursor(model.getCursor());
    }


    @Override
    public void visitBody(Body2D body) {
        if(body == selectedBody){
            view.setColor(Color.RED);
        }else{
            view.setColor(Color.LIGHTYELLOW);
        }
        view.set((int)body.getPosition().getX(), (int)body.getPosition().getY(), cursorGraphicAspect(body.getDir()));
    }

    @Override
    public void visitCursor(Cursor cursor) {
        switch(cursor.getMode()){
            case DRAWING:
                view.setColor(Color.LIGHTGREEN);
                break;
            case ERASING:
                view.setColor(Color.ORANGERED);
                break;
            case MOVING:
                view.setColor(Color.YELLOW);
                break;
        }
        view.set(cursor.getPosition().getX(), cursor.getPosition().getY(), cursorGraphicAspect(cursor.getDir()));
    }

    private String dirGraphicAspect(Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
            case W:
                return "−";
            case NE:
            case SW:
                return "/";
            case N:
            case S:
                return "|";
            case NW:
            case SE:
                return "\\";
        }
        return "?";
    }

    private String cursorGraphicAspect(Dir dir) {
        if (dir == null) {
            return "";
        }
        switch (dir) {
            case E:
                return ">";
            case W:
                return "<";
            case NE:
                return "⌝";
            case SW:
                return "⌞";
            case N:
                return "⌃";
            case S:
                return "⌄";
            case NW:
                return "⌜";
            case SE:
                return "⌟";
        }
        return "?";
    }

}
