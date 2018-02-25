package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.map.Point;
import com.letrain.view.Aspect;
import com.letrain.view.Window;

public class Vehicle {
	Aspect aspect;
	Point pos;
	Dir dir;
	boolean selected;

	public Vehicle(){
		dir = Dir.N;
	}
	public Vehicle(Aspect aspect) {
		this.aspect = aspect;
	}

	public void paint(Window window){
		this.aspect.paint(window, pos);
	}
	public void erase(Window window){
		this.aspect.erase(window, pos);
	}
	public Point getPos() {
		return pos;
	}
	public boolean isSelected(){
		return selected;
	}
	public void setSelected(boolean selected){
		this.selected = selected;
	}
	public void rotateLeft() {
		rotateLeft(1);
	}
	public void rotateLeft(int angle) {
		dir = Dir.add(dir, angle);
	}

	public void rotateRight( ) {
		rotateRight(1);
	}
	public void rotateRight(int angle) {
		dir = Dir.add(dir, angle * -1);
	}

	public void rotate(int angle) {
		dir = Dir.add(dir, angle);
	}

	public void setPos(Point pos) {
		this.pos = pos;
	}

	public void setRow(int row) {
		this.pos.setRow(row);
	}

	public void setCol(int col) {
		this.pos.setCol(col);
	}

	public void setDir(Dir dir) {
		this.dir = dir;
	}

	public Dir getDir() {
		return dir;
	}

	public Aspect getAspect() {
		return aspect;
	}
}
