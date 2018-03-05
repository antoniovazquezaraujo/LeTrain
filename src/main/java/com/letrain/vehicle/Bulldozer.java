package com.letrain.vehicle;

import com.letrain.dir.Dir;
import com.letrain.map.Point;
import com.letrain.map.RailMap;
import com.letrain.rail.CrossRail;
import com.letrain.rail.CurveRail;
import com.letrain.rail.ForkRail;
import com.letrain.rail.GateRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;

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

//	public void setMode(BulldozerMode mode) {
//		this.mode = mode;
//	}
//
//	public BulldozerMode getMode() {
//		return mode;
//	}
//
//	public boolean move() {
//		return move(1);
//	}
//	public void rotateLeft() {
//		super.rotateLeft( );
//		//anotar que es una curva a la izquierda
//	}
// 	public void rotateRight( ) {
//		super.rotateRight( );
//		// anotar que es una curva a la derecha
//	}
//	public boolean move(int distance) {
//		Point p = getPos();
//		for (int n = 0; n < distance; n++) {
//			switch (getMode()) {
//			case ERASING:
//				removeRail();
//				backwards();
//				break;
//			case MOVING:
//				forward();
//				break;
//			case PAINTING:
//				Rail newRail = addRail(p);
//				linkRail(newRail);
//				forward();
//			}
//		}
//		return true;
//	}
//
//	public Rail addRail(Point p) {
//		Rail newRail = makeNewRail(railMap.getRailAt(p.getRow(), p.getCol()));
//		railMap.addRail(p.getRow(), p.getCol(), newRail);
//		return newRail;
//	}
//
//	private void linkRail(Rail newRail) {
//		Point lastPoint = new Point(getPos());
//		Dir lastDir = getLastDir();
//		Dir backDir = dir.inverse();
//		lastPoint.move(backDir);
//		Rail lastRail = railMap.getRailAt(lastPoint.getRow(), lastPoint.getCol());
//		if (lastRail != null) {
//			newRail.linkRailAt(backDir, lastRail);
//		}
//	}
//
//	public boolean forward() {
//		pos.move(dir, 1);
//		lastDir = dir;
//		return true;
//	}
//
//	public boolean backwards() {
//		pos.move(Dir.add(dir, Dir.MIDDLE_ANGLE), 1);
//		return true;
//	}
//
//	public Rail removeRail() {
//		Point p2 = getPos();
//		Rail rail = railMap.getRailAt(p2.getRow(), p2.getCol());
//		railMap.addRail(p2.getRow(), p2.getCol(), null);
//		return rail;
//	}
//
//	
//	Dir getLastDir() {
//		return lastDir;
//	}
//
//	public GateRail makeGate() {
//		Point p = getPos();
//		GateRail ret = new BasicGateRail(); 
//		return ret;
//	}
//// Cosas traidas desde DirEnv
//	public  Rail makeNewRail() {
//		Rail ret = null;
//		if (isFork()) {
//			ret = new ForkRail();
//		} else if (isCurve()) {
//			ret = new CurveRail();
//		} else if (isStraight()) {
//			ret = new StraightRail();
//		} else {
//			ret = new CrossRail();
//		}
//		ret.setEnv(this);
//		return ret;
//	}
//
//	public boolean isStraight() {
//		// es valido si solo tiene dos dir y forman recta
//		int numDirs = 0;
//		for (Dir d : Dir.values()) {
//			Dir found = getPath(d);
//			if (found != null) {
//				numDirs++;
//				if (numDirs > 2) {
//					return false;
//				}
//				if (!found.isStraight(getPath(found))) {
//					return false;
//				}
//			}
//		}
//		return true;
//	}
//
//	public boolean isCurve() {
//		boolean ret = true;
//		int numDirs = 0;
//		for (Dir dir : Dir.values()) {
//			Dir from = getPath(dir);
//			if (from != null) {
//				numDirs++;
//				if (numDirs > 2) {
//					ret = false;
//					break;
//				}
//				if (!from.isCurve(getPath(from))) {
//					ret = false;
//					break;
//				}
//			}
//		}
//		return (ret == true && numDirs == 2);
//	}
//
//	public boolean isCross() {
//		// es valido si solo tiene rectas y tiene mas de una
//		boolean ret = true;
//		int numDirs = 0;
//		for (Dir dir : Dir.values()) {
//			if (dirs[dir.getValue()] != null) {
//				Dir from = getPath(dir);
//				numDirs++;
//				if (!from.isStraight(getPath(from))) {
//					return false;
//				}
//			}
//		}
//		return (ret == true && numDirs > 2);
//	}
//
//	public boolean isFork() {
//		int numDirs = 0;
//		boolean haveAnInput = false;
//		for (Dir dir : Dir.values()) {
//			Dir found = getPath(dir);
//			if (found != null) {
//				numDirs++;
//				if (canBeAForkInput(found)) {
//					haveAnInput = true;
//				}
//			}
//		}
//		return ((numDirs > 2) && (haveAnInput));
//	}
//
//	public boolean canBeAForkInput(Dir dir) {
//		return ((getPath(Dir.add(dir, 1)) != null) && (getPath(Dir.add(dir, -1)) != null));
//	}
}
