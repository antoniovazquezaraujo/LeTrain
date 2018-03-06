package com.letrain.rail;

import java.util.Deque;
import java.util.LinkedList;

import com.letrain.dir.Dir;
import com.letrain.vehicle.RailVehicle;
import com.letrain.vehicle.Train;
import com.letrain.view.GateAspect;

public class GateRail extends Rail   {
	Train train;
	int numVehiclesInside;
	private int ejectingVehicleIndex;
	Deque<RailVehicle> vehiclesInside;
	private boolean ejecting;
	private Rail inputRail;
	private Rail outputRail;
	private Dir inputDir;
	private Dir outputDir;

	public GateRail() {
		super(new GateAspect());
		vehiclesInside = new LinkedList<>();
		this.numVehiclesInside = 0;
		this.train = null;
	}

	/* (non-Javadoc)
	 * @see com.letrain.gate.TrainGate#enterVehicle(com.letrain.vehicle.RailVehicle)
	 */
	@Override
	public boolean enterVehicle(RailVehicle vehicle) {
		if (numVehiclesInside == 0) {
			this.train = vehicle.getTrain();
			onFirstVehicleEnter();
		}
		numVehiclesInside++;
		if (numVehiclesInside >= vehicle.getTrain().getSize()) {
			onLastVehicleEnter();
		}
		vehiclesInside.addLast(vehicle);
		//lo ponemos apuntando hacia la salida
		vehicle.setDir(this.outputDir);
		// dejamos la puerta libre
		vehicle.setRail(this);
		this.vehicle = null;

		// pero ojo, el veh�culo cree que sigue estando en el rail
		// todos los que entran se quedan en el mismo rail hasta que salen
		// Para salir, hacemos un tratamiento especial de exitVehicle
		return true;
	}

	/* (non-Javadoc)
	 * @see com.letrain.gate.TrainGate#exitVehicle()
	 */
	@Override
	public boolean exitVehicle() {
		this.vehicle = vehiclesInside.removeFirst() ;
		numVehiclesInside--;
		if(numVehiclesInside == 0){
			onLastVehicleExit();
			this.train = null;
		}
		Rail dest = getLinkedRailAt(outputDir);
		if (dest != null && dest.enterVehicle(this.vehicle)) {
			this.vehicle = null;
			return true;
		}
		return false;
	}

	// Desde qu� rail se entra en esta puerta
	public void setInputRail(Dir d, Rail rail){
		this.inputRail= rail;
		this.inputDir = d ;;
	}

	// A qu� ra�l se sale desde esta puerta. Puede ser el mismo
	public   void setOutputRail(Dir d ,Rail rail){
		this.outputRail= rail;
		this.outputDir = d;
	}
 
	// aviso de que ha entrado el primer veh�culo del tren
	public void onFirstVehicleEnter() {
	}

	// aviso de que ha entrado el �ltimo veh�culo del tren
	public void onLastVehicleEnter() {
	}

	// aviso de que ha salido el primer veh�culo del tren
	public void onFirstVehicleExit() {
	}

	// aviso de que salido el �ltimo veh�culo del tren
	public void onLastVehicleExit() {
	}

	// el tren que est� "dentro", si es que hay alguno
	public Train getTrain() {
		return train;
	}

	// se expulsa el tren que hay dentro
	public void eject() {
		ejecting = true;
	}

	public void turn() {
		// a la puerta le tocar� actuar en cada turno para que pueda sacar el
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