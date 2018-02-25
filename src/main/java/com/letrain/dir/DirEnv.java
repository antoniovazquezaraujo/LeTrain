package com.letrain.dir;

import com.letrain.rail.CrossRail;
import com.letrain.rail.CurveRail;
import com.letrain.rail.ForkRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;

/*
 * DirEnv representa una encrucijada, donde entrando desde una dirección puedes llegar a salir por otra.
 * El array dirs guarda la dirección por la que se sale al entrar por cada una de ellas, por ejemplo, 
 * si en la dirección N guarda un S significa que si entramos por el N vamos a salir por el S
 * Cuando se agrega un camino ha de hacerse en las dos direcciones, por ejemplo, si entrando desde el norte 
 * sales por el sur, también desde el sur saldrás por el norte.
 * 
 */
public class DirEnv {
	private Dir[] dirs;

	public DirEnv() {
		this(null);
	}

	public DirEnv(DirEnv env) {
		this.dirs = new Dir[Dir.NUM_DIRS];
		for(Dir d: Dir.values()){
			dirs[d.getValue()] = env != null? env.dirs[d.getValue()]: null;
		}
	}

	public Dir getPath(int dir) {
		if (Dir.isValidValue(dir)) {
			return dirs[dir];
		} else {
			throw new RuntimeException("Invalid value for getPath");
		}
	}
	public Dir getPath(Dir dir) {
		return getPath(dir.getValue());
	}
	 
	public Dir getValue(int dir){
		return dirs[dir];
	}
	public Dir getValue(Dir dir){
		return dirs[dir.getValue()];
	}


	public Dir getFirstOpenOut() {
		for (Dir d : Dir.values()) {
			if (dirs[d.getValue()] != null) {
				return d;
			}
		}
		return null;
	}

	public Dir getFirstOpenIn() {
		for (Dir d : Dir.values()) {
			if (dirs[d.getValue()] != null) {
				return dirs[d.getValue()];
			}
		}
		return null;
	}

	public int getNumOpenOuts() {
		int numDirs = 0;
		for (int n = 0; n < Dir.NUM_DIRS; n++) {
			if (dirs[n] != null) {
				numDirs++;
			}
		}
		return numDirs;
	}

	public void addPath(Dir from, Dir to) {
		dirs[to.getValue()] = from;
		dirs[from.getValue()] = to;
	}

	public void removePath(Dir from, Dir to) {
		dirs[to.getValue()] = null;
		dirs[from.getValue()] = null;
	}

	public  Rail makeNewRail() {
		Rail ret = null;
		if (isFork()) {
			ret = new ForkRail(this);
		} else if (isCurve()) {
			ret = new CurveRail(this);
		} else if (isStraight()) {
			ret = new StraightRail(this);
		} else {
			ret = new CrossRail(this);
		}
		return ret;
	}

	public boolean isStraight() {
		// es valido si solo tiene dos dir y forman recta
		int numDirs = 0;
		for (Dir d : Dir.values()) {
			Dir found = getPath(d);
			if (found != null) {
				numDirs++;
				if (numDirs > 2) {
					return false;
				}
				if (!found.isStraight(getPath(found))) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean isCurve() {
		boolean ret = true;
		int numDirs = 0;
		for (Dir dir : Dir.values()) {
			Dir from = getPath(dir);
			if (from != null) {
				numDirs++;
				if (numDirs > 2) {
					ret = false;
					break;
				}
				if (!from.isCurve(getPath(from))) {
					ret = false;
					break;
				}
			}
		}
		return (ret == true && numDirs == 2);
	}

	public boolean isCross() {
		// es valido si solo tiene rectas y tiene mas de una
		boolean ret = true;
		int numDirs = 0;
		for (Dir dir : Dir.values()) {
			if (dirs[dir.getValue()] != null) {
				Dir from = getPath(dir);
				numDirs++;
				if (!from.isStraight(getPath(from))) {
					return false;
				}
			}
		}
		return (ret == true && numDirs > 2);
	}

	public boolean isFork() {
		int numDirs = 0;
		boolean haveAnInput = false;
		for (Dir dir : Dir.values()) {
			Dir found = getPath(dir);
			if (found != null) {
				numDirs++;
				if (canBeAForkInput(found)) {
					haveAnInput = true;
				}
			}
		}
		return ((numDirs > 2) && (haveAnInput));
	}

	public boolean canBeAForkInput(Dir dir) {
		return ((getPath(Dir.add(dir, 1)) != null) && (getPath(Dir.add(dir, -1)) != null));
	}

}