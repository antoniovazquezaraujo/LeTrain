package com.letrain.rail;

import java.util.Deque;
import java.util.LinkedList;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.vehicle.RailVehicle;
/**
 * FactoryRail es un rail "m�gico" que hace de taller de construcci�n y reparaci�n de trenes.
 * Se mostrar� como un solo ra�l, (como si solamente vi�semos la puerta) y los trenes se fabricar�n
 * dentro y saldr�n a las v�as del mapa. Cuando un tren entre desaparecer� de nuestra vista y eso
 * querr� decir que est� en el taller de reparaci�n. Ha de estar conectado a las v�as por uno solo de los
 * extremos, el otro ha de quedar libre para indicar la entrada hacia el taller.
 * Solo puede haber un tren dentro, o bien el que acaba de fabricarse, o el que acaba de entrar.
 * Antes de salir, se le pueden agregar o eliminar vagones. Quiz� tambi�n colores, nombre, tipo de carga, etc.
 * 
 * @author to
 *
 */
public class GarageRail extends StraightRail {

	//Veh�culos en el taller. Forman parte de un tren
	private Deque<RailVehicle> vehicles;
	//Railes sobre los que est�n. El primero tiene conexi�n con la salida
	//Cada uno tiene conexi�n con el anterior. Se sale tambi�n hacia delante
	private Deque<Rail> rails;
	//Direcci�n desde la que se entra
	Dir inputDir;
	//Rail con el que est� conectada la entrada
	Rail outputRail; 
	
	public GarageRail(DirEnv env) {
		super(env);
		this.vehicles = new LinkedList<>();
		this.rails = new LinkedList<>();
	}
	public void setInputDir(Dir dir) {
		this.inputDir = dir;
	}
	public void setOuputRail(Rail r) {
		this.outputRail= r;
	}

	@Override
	public boolean enterVehicle(RailVehicle vehicle) {
		// el veh�culo entra como en cualquier otro ra�l.
		return super.enterVehicle(vehicle);
	}

	@Override
	public boolean exitVehicle() {
		Rail dest = getLinkedRailAt(vehicle.getDir());
		// Puede salir en dos direcciones hacia fuera o hacia dentro
		if (dest == outputRail && dest.enterVehicle(this.vehicle)) {
			//el veh�culo sale del taller, su tren lo mover� en la direcci�n de salida
			//solo necesitamos quitarlo de la lista de veh�culos en reparaci�n
			if(!this.vehicles.isEmpty()) {
				this.vehicle = this.vehicles.removeFirst();
			}
		}else {
			//el veh�culo entra en el taller
			//lo agregamos a la lista de veh�culos en reparaci�n
			//agregamos un rail m�s si no hay para ponerlo ah�
			if(this.rails.isEmpty()) {
				Rail last = new StraightRail();
				this.rails.addLast(last);
				//al primero le ponemos de salida la misma direcci�n que de entrada
				last.linkRailAt(inputDir.inverse(), getLinkedRailAt(inputDir));
				last.linkRailAt(inputDir, getLinkedRailAt(inputDir));
			}
			Rail last = this.rails.getLast();
			if(last.getRailVehicle() != null) {
				Rail newLast = new StraightRail();
				this.rails.addLast(newLast);
				newLast.linkRailAt(inputDir.inverse(), last);
				this.vehicle.setRail(newLast);
			}
			//dejamos el sitio libre para que entre otro
			this.vehicle = null;
		}
		return true;
	}

}
