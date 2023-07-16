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

 
    int type;
    int x;
    int y;
    Point pos = new Point(x, y);
    private static final long serialVersionUID = 1L;

    private Ground() {

    }
    public Ground(  int x, int y, int type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }
    public int getType(){
        return type;
    }
    public void setType(int type){
        this.type = type;
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ":" + type + "}";
    }

    /**************************************************************
     * Mapeable implementation
     ***************************************************************/
    @Override
    public Point getPosition() {
        pos.setX(x);
        pos.setY(y);
        return pos;
    }

    @Override
    public void setPosition(Point pos) {
        this.x = pos.getX();
        this.y = pos.getY();
    }

	@Override
	public void accept(Visitor visitor) {
        visitor.visitGround(this);
	}

}
