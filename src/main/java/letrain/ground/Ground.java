package letrain.ground;

import java.io.Serializable;

import letrain.map.Mapeable;
import letrain.map.Point;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Ground implements
        Serializable,
        Mapeable,
        Renderable {

    public enum GroundType {
        GROUND,
        MOUNTAIN
    }
    GroundType type = GroundType.GROUND;

    private static final long serialVersionUID = 1L;
    private Point pos = new Point(0, 0);

    Ground() {

    }
    Ground (GroundType type){
        this.type = type;
    }
    public GroundType getType(){
        return type;
    }
    public void setType(GroundType type){
        this.type = type;
    }

    @Override
    public String toString() {
        return "{" + pos + ", " + getClass().getSimpleName() + "}";
    }

    /**************************************************************
     * Mapeable implementation
     ***************************************************************/
    @Override
    public Point getPosition() {
        return pos;
    }

    @Override
    public void setPosition(Point pos) {
        this.pos.setX(pos.getX());
        this.pos.setY(pos.getY());
    }

	@Override
	public void accept(Visitor visitor) {
        visitor.visitGround(this);
	}

}
