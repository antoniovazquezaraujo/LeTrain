package com.letrain.gate;

import java.util.Deque;
import java.util.LinkedList;

import com.letrain.rail.Rail;
import com.letrain.vehicle.RailVehicle;
import com.letrain.vehicle.Train;

public abstract class TrainGate extends Rail {
	Train train;
	int numVehiclesInside;
	private int ejectingVehicleIndex;
	Deque<RailVehicle> vehiclesInside;
	private boolean ejecting;

	public TrainGate() {
		vehiclesInside = new LinkedList<>();
		this.numVehiclesInside = 0;
		this.train = null;
	}

	@Override
	public boolean enterVehicle(RailVehicle vehicle) {
		if (numVehiclesInside == 0) {
			this.train = vehicle.getTrain();
			onFirstVehicleEnter();
		}
		numVehiclesInside++;
		if (numVehiclesInside >= vehicle.getTrain().getVehicles().size()) {
			onLastVehicleEnter();
		}
		vehiclesInside.addLast(vehicle);

		// dejamos la puerta libre
		this.vehicle = null;
		// pero ojo, el vehículo cree que sigue estando en el rail
		// todos los que entran se quedan en el mismo rail hasta que salen
		// Para salir, hacemos un tratamiento especial de exitVehicle
		return true;
	}

	@Override
	public boolean exitVehicle() {
		return super.exitVehicle();
	}

	// Desde qué rail se entra en esta puerta
	public abstract void connectInputRail(Rail rail);

	// A qué raíl se sale desde esta puerta. Puede ser el mismo
	public abstract void connectOutputRail(Rail rail);

	// aviso de que ha entrado el primer vehículo del tren
	public void onFirstVehicleEnter() {
	}

	// aviso de que ha entrado el último vehículo del tren
	public void onLastVehicleEnter() {
	}

	// aviso de que ha salido el primer vehículo del tren
	public void onFirstVehicleExit() {
	}

	// aviso de que salido el último vehículo del tren
	public void onLastVehicleExit() {
	}

	// el tren que está "dentro", si es que hay alguno
	public Train getTrain() {
		return train;
	}

	// se expulsa el tren que hay dentro
	public void eject() {
		ejecting = true;
	}

	public void turn() {
		// a la puerta le tocará actuar en cada turno para que pueda sacar el
		// tren
		// si es lo que se le ha ordenado
		if (ejecting) {
			if(vehiclesInside.size() == numVehiclesInside){
				onFirstVehicleExit();
			}
			if (vehiclesInside.isEmpty()) {
				onLastVehicleExit();
				ejecting=false;
			} else {
				vehiclesInside.removeFirst().getRail().exitVehicle();
			}
		}
	}
}

class VanishTrainGate extends TrainGate {

	@Override
	public void connectInputRail(Rail rail) {
		// TODO Auto-generated method stub

	}

	@Override
	public void connectOutputRail(Rail rail) {
		// TODO Auto-generated method stub

	}

}