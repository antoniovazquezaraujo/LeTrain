package com.letrain.garage;

import com.letrain.vehicle.Train;
import com.letrain.vehicle.Wagon;
import com.letrain.vehicle.Train.TrainSide;

/**
 * Garage donde se fabrican y reparan trenes.
 * @author to
 *
 */
public class TrainGarage {
	
	GarageRail rail;
	private int numWagons;
	private Train train;
	public TrainGarage(GarageRail rail) {
		this.rail = rail;
	}
	public void setNumWagons(int numWagons) {
		this.numWagons = numWagons;
	}
	public Train makeTrain() {
		if(this.train == null) {
			this.train = new Train();
			for(int n=0; n<numWagons; n++) {
				this.train.addWagon(TrainSide.BACK, new Wagon());
			}
		}
		return this.train;
	}
	public Train getTrain() {
		return train;
	}
	public GarageRail getRail() {
		return rail;
	}
}
