package com.letrain.vehicle;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.function.Consumer;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;

public class Train extends Locomotive {
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

	Stack<RailVehicle> vehiclesAtBack;
	Stack<RailVehicle> vehiclesAtFront;

	int numStoppedTurns;

	Dir trainDir;
	boolean moved;
	boolean reversed;

	public Train() {
		vehiclesAtFront = new Stack<>();
		vehiclesAtBack = new Stack<>();
		reversed = false;
	}

	public int getSize() {
		return vehiclesAtFront.size()+ 1 + vehiclesAtBack.size();
	}
	public Stack<RailVehicle> getVehiclesAtFront() {
		return vehiclesAtFront;
	}

	public Stack<RailVehicle> getVehiclesAtBack() {
		return vehiclesAtBack;
	}

	public void setMoved(boolean moved) {
		this.moved = moved;
	}

	public void addVehicle(TrainSide p, Wagon w) {
		RailVehicle vehicleToLink = null;
		if (p == TrainSide.FRONT) {
			vehiclesAtFront.add(w);
		} else {
			vehiclesAtBack.add(w);
		}
		if (vehicleToLink != null) {
			Rail linkedRail = w.getRail();
			w.setDir(linkedRail.getPath(vehicleToLink.getDir().inverse()));
		}
		w.setTrain(this);
	}

	public Dir getDirFromFirst() {
		return ((vehiclesAtFront.peek())).getDir();
	}

	public Dir getDirFromBack() {
		return ((vehiclesAtBack.peek())).getDir();
	}

	// void shiftBackward() {
	// vehicles.descendingIterator().forEachRemaining(v -> {
	// v.getRail().exitVehicle();
	// });
	// }

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

	public void forEach(Consumer<RailVehicle> c) {
		Iterator<RailVehicle> iterator;
		iterator = vehiclesAtFront.iterator();
		iterator.forEachRemaining(v -> {
			c.accept(v);
		});
		c.accept(this);
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

		if (reversed) {
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
