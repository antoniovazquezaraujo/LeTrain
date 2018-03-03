package com.letrain.vehicle;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class Train extends Locomotive {
	public enum TrainSide {
		FRONT, BACK;
	}

	List<RailVehicle> vehiclesAtBack;
	List<RailVehicle> vehiclesAtFront;

	int numStoppedTurns;

	Dir trainDir;
	boolean moved;
	boolean reversed;
	TrainSide sense;

	public Train() {
		vehiclesAtFront = new ArrayList<>();
		vehiclesAtBack = new ArrayList<>();
		reversed = false;
		sense = TrainSide.FRONT;
	}

	public int getSize() {
		return vehiclesAtFront.size() + 1 + vehiclesAtBack.size();
	}

	public List<RailVehicle> getVehiclesAtFront() {
		return vehiclesAtFront;
	}

	public List<RailVehicle> getVehiclesAtBack() {
		return vehiclesAtBack;
	}

	public void setMoved(boolean moved) {
		this.moved = moved;
	}

//	public void addVehicle(RailVehicle v) {
//		Rail railOfVehicle = v.getRail();
//		if (railOfVehicle != null) {
//			if (railOfVehicle.equals(nextRail(TrainSide.FRONT))) {
//				addVehicle(TrainSide.FRONT, v);
//				return;
//			}
//			if (railOfVehicle.equals(nextRail(TrainSide.BACK))) {
//				addVehicle(TrainSide.BACK, v);
//				return;
//			}
//		}
//	}

	public void addVehicle(TrainSide p, RailVehicle w) {
		RailVehicle vehicleToLink = null;
		if (p == TrainSide.FRONT) {
			vehiclesAtFront.add(w);
		} else {
			vehiclesAtBack.add(w);
		}
		w.setTrain(this);
	}

	// public Dir getDirFromFirst() {
	// return ((vehiclesAtFront.peek())).getDir();
	// }
	//
	// public Dir getDirFromBack() {
	// return ((vehiclesAtBack.peek())).getDir();
	// }

	public void invert() {
		// vehiclesAtFront.iterator().forEachRemaining(v -> {
		// v.getRail().reverseVehicle();
		// });
		// getRail().reverseVehicle();
		// vehiclesAtBack.iterator().forEachRemaining(v -> {
		// v.getRail().reverseVehicle();
		// });
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

		if (numStoppedTurns == 0) {
			moveTrain();
			numStoppedTurns = 100;
		} else {
			numStoppedTurns--;
		}

	}

	// public Rail nextRail() {
	// return nextRail(reversed?TrainSide.BACK:TrainSide.FRONT);
	// }
	// public Rail nextRailAtFront() {
	// return nextRail(TrainSide.FRONT);
	// }
	// public Rail nextRailAtBack() {
	// return nextRail(TrainSide.BACK);
	// }
	// public Rail nextRail(TrainSide trainSide) {
	// RailVehicle topVehicle = null;
	// RailVehicle linker = null;
	// Rail nextRail = null;
	// if (trainSide.equals(TrainSide.BACK)) {
	// topVehicle = getLastBackVehicle();
	// linker = getBackAntecessor(topVehicle);
	// } else {
	// topVehicle = getLastFrontVehicle();
	// linker = getFrontAntecessor(topVehicle);
	// }
	// if(linker==null) {
	// linker = this;
	// }
	//
	//
	// nextRail = topVehicle.getRail().getLinkedRailAt(topVehicle.getDir());
	// return nextRail;
	// }

	public RailVehicle getBackAntecessor(RailVehicle v) {
		if (vehiclesAtBack.isEmpty()) {
			return null;
		}
		int index = vehiclesAtBack.indexOf(v);
		if (index <= 0) {
			return null;
		}
		return vehiclesAtBack.get(index - 1);
	}

	public RailVehicle getFrontAntecessor(RailVehicle v) {
		if (vehiclesAtFront.isEmpty()) {
			return null;
		}
		int index = vehiclesAtFront.indexOf(v);
		if (index <= 0) {
			return null;
		}
		return vehiclesAtFront.get(index - 1);
	}

	public RailVehicle getLastFrontVehicle() {
		return vehiclesAtFront.isEmpty() ? null : vehiclesAtFront.get(vehiclesAtFront.size() - 1);
	}

	public RailVehicle getLastBackVehicle() {
		return vehiclesAtBack.isEmpty() ? null : vehiclesAtBack.get(vehiclesAtBack.size() - 1);
	}

	public void moveTrain() {
		Dir d = this.dir;
		RailVehicle v = this;
		for (RailVehicle next : vehiclesAtFront) {
			d = next.push(v);
			v = next;
		}
		for (RailVehicle next : vehiclesAtFront) {
			next.forward();
		}
		v = this;
		for (RailVehicle next : vehiclesAtBack) {
			d = next.pull(v);
			v = next;
		}
		this.forward();
		for (RailVehicle next : vehiclesAtBack) {
			next.forward();
		}
	}

	public void incSpeed() {
		// TODO Auto-generated method stub

	}

	public void decSpeed() {
		// TODO Auto-generated method stub

	}
}
