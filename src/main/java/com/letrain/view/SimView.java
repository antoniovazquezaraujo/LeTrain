package com.letrain.view;

import com.letrain.map.Point;
import com.letrain.vehicle.Vehicle;

import sim.Sim;

public class SimView {
	Sim  sim;
	Point pos;
	Window   window;
	Vehicle   vehicleToFollow;
SimView(Sim  sim){
	
	this.sim = sim;
//this.vehicleToFollow= null;
//	int screenRows = Window.getScreenRows();
//	int screenCols = Window.getScreenCols();
//	int commandCols = screenCols  20/100;
//	int commandRows = screenRows  20/100;
//	int mapCols = screenCols - commandCols;
//	int mapRows = screenRows - commandRows;
//
//	window = new Window(0, 0, mapCols, mapRows);
//	window.setTitle("Map");
//
//	recalcWindowShift();
//}
// 
//
//void recalcWindowShift(){
//	int hLimit = (window.getWidth()/2);
//	int vLimit = (window.getHeight()/2);
//	int hShift=hLimit;
//	int vShift=vLimit;
//	if(vehicleToFollow){
//		hShift = vehicleToFollow.getPos().col;
//		vShift = vehicleToFollow.getPos().row;
//	}
//	int perimeter= 2;
//	if( (hShift > pos.col+ hLimit-perimeter) ||
//		(hShift < pos.col- hLimit+perimeter)){
//		pos.col = hShift;
//		window.shift(Window.HORIZONTAL_SHIFT, hShift);
//	}
//	if( (vShift > pos.row+ vLimit-perimeter) ||
//		(vShift < pos.row- vLimit+perimeter)){
//		pos.row = vShift;
//		window.shift(Window.VERTICAL_SHIFT,vShift);
//	}
//}
//void followVehicle(Vehicle  v){
//	vehicleToFollow = v;
//}
//void setPos(Point p){
//	pos = p;
//}
//Point getPos(){
//	return pos;
//}
//void erase(){
//	sim.railMap.erase(window);
//	sim.railPen.erase(window);
//	sim.getFinder().erase(window);
//}
//void paint(){
//	recalcWindowShift();
//	sim.railMap.paint(window);
//	sim.railPen.paint(window);
//
//	for(auto semaphore : sim.getSemaphores()){
//		semaphore.paint(window);
//	}
//	for(auto sensor: sim.getSensors()){
//		sensor.paint(window);
//	}
//	for(auto train: sim.getTrains()){
//		train.paint(window);
//	}
//	for(auto loco: sim.getLocomotives()){
//		loco.paint(window);
//	}
//	for(auto wagon: sim.getWagons()){
//		wagon.paint(window);
//	}
//
//	sim.getFinder().paint(window);
//	window.repaint();
 }
}
