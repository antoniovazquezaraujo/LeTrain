package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.map.Point;
import com.letrain.map.RailMap;
import com.letrain.rail.BasicGateRail;
import com.letrain.rail.GateRail;
import com.letrain.rail.Rail;

public class Bulldozer extends Vehicle {
	public enum BulldozerMode {
		MOVING, PAINTING, ERASING
	};

	Dir lastDir;
	RailMap railMap;
	BulldozerMode mode;

	public Bulldozer(RailMap railMap, Point pos) {
		this.railMap = railMap;
		this.pos = pos;
	}

	public void setMode(BulldozerMode mode) {
		this.mode = mode;
	}

	public BulldozerMode getMode() {
		return mode;
	}

	public boolean move() {
		return move(1);
	}

	public boolean move(int distance) {
		Point p = getPos();
		for (int n = 0; n < distance; n++) {
			switch (getMode()) {
			case ERASING:
				removeRail();
				backwards();
				break;
			case MOVING:
				forward();
				break;
			case PAINTING:
				Rail newRail = addRail(p);
				linkRail(newRail);
				forward();
			}
		}
		return true;
	}

	public Rail addRail(Point p) {
		Rail newRail = makeNewRail(railMap.getRailAt(p.getRow(), p.getCol()));
		railMap.setRail(p.getRow(), p.getCol(), newRail);
		return newRail;
	}

	private void linkRail(Rail newRail) {
		Point lastPoint = new Point(getPos());
		Dir lastDir = getLastDir();
		Dir backDir = dir.inverse();
		lastPoint.move(backDir);
		Rail lastRail = railMap.getRailAt(lastPoint.getRow(), lastPoint.getCol());
		if (lastRail != null) {
			newRail.linkRailAt(backDir, lastRail);
		}
	}

	public boolean forward() {
		pos.move(dir, 1);
		lastDir = dir;
		return true;
	}

	public boolean backwards() {
		pos.move(Dir.add(dir, Dir.MIDDLE_ANGLE), 1);
		return true;
	}

	public Rail removeRail() {
		Point p2 = getPos();
		Rail rail = railMap.getRailAt(p2.getRow(), p2.getCol());
		railMap.setRail(p2.getRow(), p2.getCol(), null);
		return rail;
	}

	Rail makeNewRail(Rail rail) {
		DirEnv env = null;
		if (rail != null) {
			env = rail.getEnv();
		} else {
			env = new DirEnv();
		}
		env.addPath(dir.inverse(), dir);

		Rail newRail = env.makeNewRail();
		newRail.setPos(pos);
		if (rail != null) {
			for (Dir d : Dir.values()) {

				Rail linked = rail.getLinkedRailAt(d);
				if (linked != null) {
					newRail.linkRailAt(d, linked);
				}
			}

		}
		return newRail;
	}
	 

	Dir getLastDir() {
		return lastDir;
	}

	public GateRail makeGate() {
		Point p = getPos();
		GateRail ret = new BasicGateRail(); 
		return ret;
	}

}
