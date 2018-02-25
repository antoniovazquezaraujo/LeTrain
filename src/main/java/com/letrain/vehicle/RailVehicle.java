package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class RailVehicle extends Vehicle {
	Rail rail;
	boolean moved;
	private Train train;
	int speed;

	// phisics
	// float impulse;
	// private float impulseGenerated;
	// private int mass;
	// int brakes;

	public RailVehicle() {
		super();
	}

	public void forward() {
		gotoRail(rail.getLinkedRailAt(dir));
	}

	public void backward() {
		goBackToRail(rail.getLinkedRailAt(rail.getPath(dir)));
	}

	public boolean goBackToRail(Rail r) {
		if (r.getRailVehicle() != null) {
			return false;
		}
		// si ya tengo un rail lo vacio
		if (rail != null) {
			rail.setRailVehicle(null);
		}
		if (dir != null) {
			dir = r.getPath(dir);
			dir = dir.inverse();
		} else {
			dir = r.getAnyPath();
		}
		pos = r.getPos();
		rail = r;
		r.setRailVehicle(this);
		return true;
	}

	public boolean gotoRail(Rail r) {
		if (r.getRailVehicle() != null) {
			return false;
		}
		// si ya tengo un rail lo vacio
		if (rail != null) {
			rail.setRailVehicle(null);
		}
		if (dir != null) {
			Dir inverse = dir.inverse();
			dir = r.getPath(inverse);
		} else {
			dir = r.getAnyPath();
		}
		pos = r.getPos();
		rail = r;
		r.setRailVehicle(this);
		return true;
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

	// Phisics /////////////////////////////////////////

	// int getMass(){
	// return mass;
	// }
	//
	// float getImpulse(){
	// return impulse;
	// }
	// float receiveImpulse(float impulseReceived, Dir dir){
	// float consumed = 0;
	// if(getRail().getPath(dir.inverse()) == this.dir){
	// consumed = mass - this.impulse;
	// if(consumed < 0) consumed = 0;
	// }else{
	// consumed = mass + this.impulse;
	// }
	// if(impulseReceived >= consumed){
	// this.impulse += consumed;
	// return impulseReceived - consumed;
	// }else{
	// this.impulse += impulseReceived;
	// return 0;
	// }
	// }
	// void reverseImpulse(){
	// impulse*= -1;
	// }
	// void incImpulseGenerated(float n){
	// impulseGenerated+=n;
	// }
	// void decImpulseGenerated(float n){
	// impulseGenerated-=n;
	// }
	// void generateImpulse(){
	// impulse+= impulseGenerated;
	// }
	// void consumeImpulse(){
	// impulse =0;
	// }
	// int getBrakes(){
	// return brakes;
	// }
	// void setBrakes(int brakes){
	// this.brakes = brakes;
	// }
}
