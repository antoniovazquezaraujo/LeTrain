package com.letrain.rail;

import com.letrain.dir.Dir;

import com.letrain.map.Point;
import com.letrain.vehicle.RailVehicle;
import com.letrain.view.Aspect;

import static com.letrain.dir.Dir.*;

public abstract class Rail {
	Point pos;
	protected RailVehicle vehicle;
	Aspect aspect;
	protected Rail[] links; // enlaces con otros railes
	protected Dir[] dirs;   // caminos de entrada o salida


	public Rail(Aspect aspect) {
		this.aspect = aspect;
		this.vehicle = null;
		this.links = new Rail[NUM_DIRS];
		this.dirs = new Dir[NUM_DIRS];
	}
	
	public Dir getPath(Dir dir) {
		return dirs[ dir.getValue() ] ;
	}
 
	public Dir getFirstOpenDir() {
		for (Dir d : values()) {
			if (dirs[d.getValue()] != null) {
				return d;
			}
		}
		return null;
	}

  	public int getNumOpenDirs() {
		int numDirs = 0;
		for (int n = 0; n < NUM_DIRS; n++) {
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
 
	public RailVehicle getVehicle() {
	    return vehicle;
	}

	/*
	 * EnterVehicle Si hay un veh�culo no entra y devuelve false. Si el rail
	 * est� vacio, entra el veh�culo. Cuando entra un veh�culo, miramos su
	 * direcci�n, y girandola 180 grados obtenemos la puerta por la que est�
	 * entrando (si viene en direcci�n sur, entra por la puerta norte, etc)
	 * Entonces, obtenemos la puerta a donde va a dar esa entrada, y le ponemos
	 * al veh�culo esa direcci�n, para que cuando salga, lo haga por ah�.
	 */
	public boolean enterVehicle(RailVehicle vehicle) {
		if (this.vehicle != null) {
			return false;
		}
		this.vehicle = vehicle;
		if (vehicle != null) {
			vehicle.setRail(this);
		}

		this.vehicle.setPos(pos);
		this.vehicle.setDir(getPath(vehicle.getDir().inverse()));
		return true;
	}

	/*
	 * ExitVehicle Saca el veh�culo hacia el rail conectado en la direcci�n de
	 * avance del mismo y lo hace entrar en el rail conectado en esa direcci�n
	 */
	public boolean exitVehicle() {
		Rail dest = getLinkedRailAt(getPath(vehicle.getDir().inverse()));
		if (dest != null && dest.enterVehicle(this.vehicle)) {
			this.vehicle = null;
			return true;
		}
		return false;
	}


	public Rail linkStraight(Dir d) {
		Rail r = new StraightRail();
		r.addPath(d.inverse(), d);
		r.links[d.inverse().getValue()] = this;
		links[d.getValue()] = r;
		updatePos(d,r);
		return r;
	}

	public Rail linkCurve(Dir d, Dir next) {
		Rail r = new CurveRail();
		r.addPath(d.inverse(), next);
		r.links[d.inverse().getValue()] = this;
		links[d.getValue()] = r;
		updatePos(d,r);
		return r;
	}

	public Rail linkFork(Dir d, Dir left, Dir right) {
		ForkRail r = new ForkRail();
		r.addPath(d.inverse(), left, right);
		r.links[d.inverse().getValue()] = this;
		links[d.getValue()] = r;
		updatePos(d,r);
		return r;
	}

	public Rail linkGate(Dir d, Rail nextRail) {
		GateRail r = new GateRail();  
		r.addPath(d.inverse(), d);
		r.links[d.inverse().getValue()] = this;
		links[d.getValue()] = r;
		r.setInputRail(d.inverse(), this);
		r.setOutputRail(d, null);
		updatePos(d,r);
		return r;
	}

	private void updatePos(Dir dir, Rail rail) {
		Point dest = new Point(this.pos);
		dest.move(dir);
		rail.setPos(dest);
	}

	public Rail getLinkedRailAt(Dir dir) {
		return links[dir.getValue()];
	}
 	public Dir getAnyPath() {
		return getFirstOpenDir();
	}

	public void reverseVehicle() {
		vehicle.setDir(getPath(vehicle.getDir()));
	}
  
	public Point getPos() {
		return pos;
	}

	public void setPos(Point pos) {
		this.pos = pos;
	}

	public void setPos(int row, int col) {
		this.pos = new Point(row, col);
	}
	public Aspect getAspect(){
		return aspect;
	}
}
