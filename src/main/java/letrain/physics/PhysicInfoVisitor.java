package letrain.physics;

import javafx.scene.paint.Color;
import letrain.mvp.View;
import letrain.vehicle.impl.Cursor;

import java.text.DecimalFormat;

public class PhysicInfoVisitor implements PhysicVisitor {
    private static final Color RAIL_TRACK_COLOR = Color.grayRgb(80);
    public static final Color FORK_COLOR = Color.grayRgb(180);
    String infoBarText = "";
    String helpBarText = "";
    private final View view;

    public PhysicInfoVisitor(View view) {
        this.view = view;
    }

    @Override
    public void visitModel(PhysicModel model) {
        infoBarText = "";
        visitBody(model.getSelectedBody());
        visitCursor(model.getCursor());
        view.setInfoBarText(infoBarText);
    }

    @Override
    public void visitBody(Body2D body) {
        if (body != null) {
            DecimalFormat df = new DecimalFormat("0000.0000");
            infoBarText +=
                    " MF:" + df.format(body.getMotorForce()) +
                            " EF:" + df.format(body.getExternalForce().magnitude()) +
                            " VE:" + df.format(body.getVelocity().magnitude()) +
                            " DT:" + df.format(body.distanceTraveledInStep) +
                            " BR:" + df.format(body.getBrakesForce()) +
                            " MA:" + df.format(body.getMass()) +
                            " RE:" + body.isInverted();
        }
    }

    @Override
    public void visitCursor(Cursor cursor) {
        infoBarText += "Cursor:[" + cursor.getPosition().getX() + "," + cursor.getPosition().getY() + "]" + "\n";
    }

}
