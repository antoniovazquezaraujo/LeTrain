package com.letrain.rail;

import java.util.Deque;
import java.util.LinkedList;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.vehicle.RailVehicle;
/**
 * FactoryRail es un rail "mágico" que hace de taller de construcción y reparación de trenes.
 * Se mostrará como un solo raíl, (como si solamente viésemos la puerta) y los trenes se fabricarán
 * dentro y saldrán a las vías del mapa. Cuando un tren entre desaparecerá de nuestra vista y eso
 * querrá decir que está en el taller de reparación. Ha de estar conectado a las vías por uno solo de los
 * extremos, el otro ha de quedar libre para indicar la entrada hacia el taller.
 * Solo puede haber un tren dentro, o bien el que acaba de fabricarse, o el que acaba de entrar.
 * Antes de salir, se le pueden agregar o eliminar vagones. Quizá también colores, nombre, tipo de carga, etc.
 * 
 * @author to
 *
 */
public class GarageRail extends StraightRail {

	//Vehículos en el taller. Forman parte de un tren
	private Deque<RailVehicle> vehicles;
	//Railes sobre los que están. El primero tiene conexión con la salida
	//Cada uno tiene conexión con el anterior. Se sale también hacia delante
	private Deque<Rail> rails;
	//Dirección desde la que se entra
	Dir inputDir;
	//Rail con el que está conectada la entrada
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
		// el vehículo entra como en cualquier otro raíl.
		return super.enterVehicle(vehicle);
	}

	@Override
	public boolean exitVehicle() {
		Rail dest = getLinkedRailAt(vehicle.getDir());
		// Puede salir en dos direcciones hacia fuera o hacia dentro
		if (dest == outputRail && dest.enterVehicle(this.vehicle)) {
			//el vehículo sale del taller, su tren lo moverá en la dirección de salida
			//solo necesitamos quitarlo de la lista de vehículos en reparación
			if(!this.vehicles.isEmpty()) {
				this.vehicle = this.vehicles.removeFirst();
			}
		}else {
			//el vehículo entra en el taller
			//lo agregamos a la lista de vehículos en reparación
			//agregamos un rail más si no hay para ponerlo ahí
			if(this.rails.isEmpty()) {
				Rail last = new StraightRail();
				this.rails.addLast(last);
				//al primero le ponemos de salida la misma dirección que de entrada
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
