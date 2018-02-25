package com.letrain.rail;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.map.Point;
import com.letrain.vehicle.RailVehicle;
import com.letrain.view.Aspect;

public abstract class Rail {
	Point pos;
	RailVehicle vehicle;
	DirEnv dirEnv; // entradas y salidas
	RailEnv railEnv; // enlaces con otros railes para formar una vía
	Aspect aspect;

	public Rail() {
		this(null);
	}

	public Rail(DirEnv dirEnv) {
		this.vehicle = null;
		this.dirEnv = new DirEnv(dirEnv);
		this.railEnv = new RailEnv();
	}

	public Aspect getAspect() {
		return aspect;
	}

	/*
	 * EnterVehicle Si hay un vehículo no entra y devuelve false. Si el rail
	 * está vacio, entra el vehículo. Cuando entra un vehículo, miramos su
	 * dirección, y girandola 180 grados obtenemos la puerta por la que está
	 * entrando (si viene en dirección sur, entra por la puerta norte, etc)
	 * Entonces, obtenemos la puerta a donde va a dar esa entrada, y le ponemos
	 * al vehículo esa dirección, para que cuando salga, lo haga por ahí.
	 */
	public boolean enterVehicle(RailVehicle vehicle) {
		if (this.vehicle != null) {
			return false;
		}
		setRailVehicle(vehicle);

		this.vehicle.setPos(pos);
		this.vehicle.setDir(getPath(vehicle.getDir().inverse()));
		return true;
	}

	/*
	 * ExitVehicle Saca el vehículo hacia el rail conectado en la dirección de
	 * avance del mismo y lo hace entrar en el rail conectado en esa dirección
	 */
	public boolean exitVehicle() {
		Rail dest = getLinkedRailAt(vehicle.getDir());
		if (dest != null && dest.enterVehicle(this.vehicle)) {
			this.vehicle = null;
			return true;
		}
		return false;
	}

	public void linkRailAt(Dir dir, Rail rail) {
		railEnv.addRail(dir, rail);
		// enlazamos también a rail con nosotros
		if (rail != null) {
			if (rail.getLinkedRailAt(dir.inverse()) != this) {
				rail.linkRailAt(dir.inverse(), this);
			}
		}
	}
 
	public Rail getLinkedRailAt(Dir dir) {
		return railEnv.getRail(dir);
	}

	public Dir getPath(Dir dir) {
		return dirEnv.getPath(dir);
	}

	public Dir getAnyPath() {
		return dirEnv.getFirstOpenOut();
	}

	public void setRailVehicle(RailVehicle vehicle) {
		this.vehicle = vehicle;
		if (vehicle != null) {
			vehicle.setRail(this);
		}
	}

	public RailVehicle getRailVehicle() {
		return vehicle;
	}

	public void reverseVehicle() {
		vehicle.setDir(getPath(vehicle.getDir()));
	}

	public DirEnv getEnv() {
		return dirEnv;
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
}
