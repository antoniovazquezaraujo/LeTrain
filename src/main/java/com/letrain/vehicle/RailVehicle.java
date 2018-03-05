package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class RailVehicle extends Vehicle {
	Rail rail;
	boolean moved;
	private Train train;
	int speed;
 

	public RailVehicle() {
		super();
	}
 
	public boolean exitRail(){
		return rail.exitVehicle();
	}
	public boolean gotoRail(Rail r) {
		// si ya tengo un rail tengo que salir correctamente
		if (this.rail != null) {
			return false;
		}
		if (r.getVehicle() != null) {
			return false;
		}
		if (dir != null) {
			Dir inverse = dir.inverse();
			dir = r.getPath(inverse);
		} else {
			dir = r.getAnyPath();
		}
		if(r.enterVehicle(this)){
			this.pos = r.getPos();
			this.rail = r;
			return true ;
		}else{
			return false;
		}
	}

	public void setSpeed(int speed) {
		this.speed = speed;
	}

	public int getSpeed() {
		return speed;
	}

	public void setTrain(Train t) {
		this.train = t;
	}

	public Train getTrain() {
		return train;
	}

	public Rail getRail() {
		return rail;
	}

	public void setRail(Rail r) {
		this.rail = r;
	}

	public boolean isMoved() {
		return moved;
	}

	public void setMoved(boolean moved) {
		this.moved = moved;
	}

	public Dir push(RailVehicle v) {
		Rail r = getRail();
		if (r != null) {
			return r.  getPath(v.getDir().inverse());
		} else {
			return null;
		}
	}
	public Dir pull(RailVehicle v) {
		Rail r = v.getRail();
		if (r != null) {
			return r. getPath(v.getDir()).inverse();
		} else {
			return null;
		}
	}

}
