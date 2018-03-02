package com.letrain.vehicle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Stack;
import java.util.function.Consumer;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class Train extends Locomotive {
	public enum TrainSide {
		FRONT, BACK;
	}

	Deque<RailVehicle> vehiclesAtBack;
	Deque<RailVehicle> vehiclesAtFront;

	int numStoppedTurns;

	Dir trainDir;
	boolean moved;
	boolean reversed;
	TrainSide sense;

	public Train() {
		vehiclesAtFront = new ArrayDeque<>();
		vehiclesAtBack = new ArrayDeque<>();
		reversed = false;
		sense = TrainSide.FRONT;
	}

	public int getSize() {
		return vehiclesAtFront.size() + 1 + vehiclesAtBack.size();
	}

	public Deque<RailVehicle> getVehiclesAtFront() {
		return vehiclesAtFront;
	}

	public Deque<RailVehicle> getVehiclesAtBack() {
		return vehiclesAtBack;
	}

	public void setMoved(boolean moved) {
		this.moved = moved;
	}

	public void addVehicle(RailVehicle v) {
		Rail railOfVehicle = v.getRail();
		if (railOfVehicle != null) {
			if (railOfVehicle.equals(nextRail(TrainSide.FRONT))) {
				addVehicle(TrainSide.FRONT, v);
				return;
			}
			if (railOfVehicle.equals(nextRail(TrainSide.BACK))) {
				addVehicle(TrainSide.BACK, v);
				return;
			}
		}
	}

	public void updateTrainDir() {
		Dir d = this.dir;
		for(RailVehicle v : getVehiclesAtFront()){
			d = v.push(d);
		}
		d = this.dir;
		for(RailVehicle v : getVehiclesAtBack()){
			d = v.push(d);
		}
	}

	public void addVehicle(TrainSide p, RailVehicle w) {
		RailVehicle vehicleToLink = null;
		if (p == TrainSide.FRONT) {
			vehicleToLink = vehiclesAtFront.peek();
			vehiclesAtFront.add(w);
		} else {
			vehicleToLink = vehiclesAtBack.peek();
			vehiclesAtBack.add(w);
		}
		w.setTrain(this);
		updateTrainDir();
	}

	public Dir getDirFromFirst() {
		return ((vehiclesAtFront.peek())).getDir();
	}

	public Dir getDirFromBack() {
		return ((vehiclesAtBack.peek())).getDir();
	}

	public void invert() {
		vehiclesAtFront.iterator().forEachRemaining(v -> {
			v.getRail().reverseVehicle();
		});
		getRail().reverseVehicle();
		vehiclesAtBack.iterator().forEachRemaining(v -> {
			v.getRail().reverseVehicle();
		});
		reversed = !reversed;
	}

	public void forEachVehicle(Consumer<RailVehicle> c) {
		forEachFrontVehicle(c);
		c.accept(this);
		forEachBackVehicle(c);
	}

	public void forEachBackVehicle(Consumer<RailVehicle> c) {
		Iterator<RailVehicle> iterator;
		iterator = vehiclesAtFront.iterator();
		iterator.forEachRemaining(v -> {
			c.accept(v);
		});
	}

	public void forEachFrontVehicle(Consumer<RailVehicle> c) {
		Iterator<RailVehicle> iterator;
		iterator = vehiclesAtBack.iterator();
		iterator.forEachRemaining(v -> {
			c.accept(v);
		});
	}

	public void turn() {
		if (moved) {
			return;
		} else {
			moved = true;
		}
		Rail target = nextRail();
		if (target != null) {
			if (target.getRailVehicle() != null) {
				throw new RuntimeException("Crash with other vehicle");
			} else {
				if (numStoppedTurns == 0) {
					moveTrain();
					numStoppedTurns = 100;
				} else {
					numStoppedTurns--;
				}
			}
		} else {
			throw new RuntimeException("Crash. End of railway");
		}
	}

	public Rail nextRail(TrainSide trainSide) {
		RailVehicle topVehicle = null;
		Rail nextRail = null;

		if (trainSide.equals(TrainSide.BACK)) {
			topVehicle = vehiclesAtBack.peek();
		} else {
			topVehicle = vehiclesAtFront.peek();
		}
		nextRail = topVehicle.getRail().getLinkedRailAt(topVehicle.getDir());
		return nextRail;
	}

	public void moveTrain() {
		forEach(v -> {
			v.getRail().exitVehicle();
		});
	}

	public void incSpeed() {
		// TODO Auto-generated method stub

	}

	public void decSpeed() {
		// TODO Auto-generated method stub

	}
}
