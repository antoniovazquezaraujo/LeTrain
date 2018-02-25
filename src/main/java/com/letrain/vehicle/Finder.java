package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;
import com.letrain.view.FinderAspect;
import com.letrain.view.Window;

public class Finder extends RailVehicle {
	Rail rail;

public Finder(RailVehicle  r){
	aspect = new FinderAspect() ;
	if(r != null){
		gotoRail(r.getRail());
	}else{
		pos.setRow(0) ;
		pos.setCol(0) ;
		rail = null;
		dir = null;
	}
} 
 
	public Rail getRail() {
		return rail;
	}

	public Dir getDir() {
		return dir;
	}

	public void setDir(Dir dir) {
		this.dir = dir;
	}

	boolean isEmpty() {
		return !(rail != null && rail.getRailVehicle() != null);
	}

	public boolean gotoRail(Rail r) {
		if (dir  != null) {
			dir = r.getPath(dir.inverse());
		} else {
			dir = r.getAnyPath();
		}
		pos = r.getPos();
		rail = r;
		return true;
	}

  	boolean forward(int distance) {
		boolean ret = false;
		if (dir  == null) {
			dir = rail.getAnyPath();
		}
		Rail next = rail.getLinkedRailAt(dir);
		if (next != null) {
			gotoRail(next);
			ret = true;
		} else {
			ret = false;
		}
		return ret;
	}

	void reverse() {
		if (rail != null) {
			dir = rail.getPath(rail.getPath(dir.inverse()));
		}
	}

	boolean backwards(int distance) {
		boolean ret = false;
		if (dir  == null) {
			dir = rail.getPath(rail.getPath(dir.inverse()));
		}
		Rail next = rail.getLinkedRailAt(dir);
		if (next != null) {
			gotoRail(next);
			ret = true;
		} else {
			ret = false;
		}
		return ret;
	}

	public void paint(Window g) {
		getAspect().paint(g, pos);
	}

	public void erase(Window g) {
		getAspect().erase(g, pos);
	}
}