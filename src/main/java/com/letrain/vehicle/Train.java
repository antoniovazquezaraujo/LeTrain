package com.letrain.vehicle;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class Train {
	public enum TrainSide {
		FRONT(0), BACK(1);
		public int value;

		TrainSide(int value) {
			this.value = value;
		}

		int getValue() {
			return value;
		}
	}

	Deque<RailVehicle> vehicles;

	int numStoppedTurns;
	float speed;

	Dir trainDir;
	boolean moved;
	boolean reversed;

	public Train() {
		vehicles = new LinkedList<>();
		reversed=false;
	}

	public Deque<RailVehicle> getVehicles() {
		return vehicles;
	}

	public void setMoved(boolean moved) {
		this.moved = moved;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public float getSpeed() {
		return speed;
	}

	// void addVehicle(int p, RailVehicle v) {
	// v.addToTrain(p, this);
	// }

	public void addWagon(TrainSide p, Wagon w) {
		RailVehicle vehicleToLink = null;
		if (p == TrainSide.FRONT) {
			vehicleToLink = vehicles.peekFirst();
			vehicles.addFirst(w);
		} else {
			vehicleToLink = vehicles.peekLast();
			vehicles.addLast(w);
		}
		if (vehicleToLink != null) {
			Rail linkedRail = w.getRail();
			w.setDir(linkedRail.getPath(vehicleToLink.getDir().inverse()));
		}
		w.setTrain(this);
	}

	public Dir getDirFromFirst() {
		return ((vehicles.peekFirst())).getDir();
	}

	public Dir getDirFromLast() {
		return ((vehicles.peekLast())).getDir();
	}

	// void shiftBackward() {
	// vehicles.descendingIterator().forEachRemaining(v -> {
	// v.getRail().exitVehicle();
	// });
	// }

	public void invert() {
		vehicles.iterator().forEachRemaining(v -> {
			v.getRail().reverseVehicle();
		});
		reversed = !reversed;

	}

	public void forEach(Consumer<RailVehicle> c) {
		forEach(c, true);
	}

	public void forEach(Consumer<RailVehicle> c, boolean forward) {
		Iterator<RailVehicle> iterator;
		if (forward) {
			iterator = vehicles.iterator();
		} else {
			iterator = vehicles.descendingIterator();
		}
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
		Rail target = railAhead();
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

	public Rail railAhead() {
		RailVehicle topVehicle = null;
		Rail nextRail = null;

		if (!reversed) {
			topVehicle = vehicles.peekFirst();
		} else {
			topVehicle = vehicles.peekLast();
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
