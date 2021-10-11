package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.map.Positionable;
import letrain.map.Positionable2D;
import letrain.map.Rotable;
import letrain.physics.Vector2D;
import letrain.vehicle.Orientable;
import letrain.vehicle.Vehicle;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Cursor implements Positionable2D, Orientable, Rotable, Renderable {
    public void move(){
        Vector2D newPosition = getPosition2D();
        newPosition.add(getHeading2D());
        setPosition2D(newPosition);
    }
    @Override
    public void rotateLeft() {
        dir = dir.turnLeft();
    }

    @Override
    public void rotateLeft(int angle) {
        // remove this!
    }

    @Override
    public void rotateRight() {
        dir = dir.turnRight();
    }

    @Override
    public void rotateRight(int angle) {
        // remove this!
    }

    @Override
    public void rotate(int angle) {
        // remove this!
    }

    public enum CursorMode{
        DRAWING,
        ERASING,
        MOVING
    }
    private CursorMode mode;
    Vector2D position = new Vector2D(0,0);
    Dir dir= Dir.E;

    @Override
    public Vector2D getHeading2D() {
        return Vector2D.fromDir(dir, 1);
    }

    @Override
    public void setHeading2D(Vector2D heading) {
        this.dir = heading.toDir();
    }

    @Override
    public Dir getDir() {
        return dir;
    }

    @Override
    public void setDir(Dir dir) {
        this.dir = dir;
    }



    public Cursor(){
        this.mode = CursorMode.DRAWING;
    }

    @Override
    public Vector2D getPosition2D() {
        return position;
    }

    @Override
    public void setPosition2D(Vector2D pos) {
        this.position = pos;
    }
    public void setMode(CursorMode mode){
        this.mode = mode;
    }
    public CursorMode getMode(){
        return mode;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitCursor(this);
    }


}
