package com.letrain.sim;

import com.letrain.rail.ForkRail;
import com.letrain.vehicle.Bulldozer.BulldozerMode;
import com.letrain.vehicle.Train;


public class Commander {
	Sim sim;
	Mode  actualMode;
	public Commander(Sim sim) {
		this.sim = sim;
		this.actualMode = Mode.BULLDOZER_MODE;
	}
	public void doCommand(Command command){
		switch(command){
		case SET_BULLDOZER_MODE:
		case SET_FORKS_MODE:
		case SET_TRAINS_MODE:
			doSetModeCommand(command);
			return;
		}
		switch(actualMode){
		case BULLDOZER_MODE:
			doBulldozerCommand(command);
			break;
		case FORKS_MODE:
			doForksCommand(command);
			break;
		case TRAINS_MODE:
			doTrainsCommand(command);
			break;
		default:
			break;
		}
	}
	private void doTrainsCommand(Command command) {
		Train t= null;
		switch(command){
		case TRAIN_SELECT_NEXT:
			sim.selectNextTrain();
			break;
		case TRAIN_SELECT_PREV:
			sim.selectPrevTrain();
			break;
		case TRAIN_INC_SPEED:
			 t = sim.getSelectedTrain();
			if (t != null){
				t.incSpeed();
			}
			break;
		case TRAIN_DEC_SPEED:
			 t = sim.getSelectedTrain();
			if (t != null){
				t.decSpeed();
			}
			break;
		default:
			break;
		}
	}
	private void doForksCommand(Command command) {
		ForkRail f= null;
		switch(command){
		case FORK_SELECT_NEXT:
			sim.selectNextFork();
			break;
		case FORK_SELECT_PREV :
			sim.selectPrevFork();
			break;
		case FORK_INC:
			f = sim.getSelectedFork();
			if (f != null){
				f.setNextDir();
			}
			break;
		case FORK_DEC:
			f = sim.getSelectedFork();
			if (f != null){
				f.setPrevDir();
			}
			break;
		default:
			break;
		}
	}
	private void doBulldozerCommand(Command command) {
		switch(command){
		case BULLDOZER_PUT_TRAIN_GARAGE:
			sim.addGate(sim.getBulldozer().makeGate());
			break;
		case BULLDOZER_PUT_RAILS_MODE:
			sim.getBulldozer().setMode(BulldozerMode.PAINTING);
			break;
		case BULLDOZER_REMOVE_RAILS_MODE:
			sim.getBulldozer().setMode(BulldozerMode.ERASING);
			break;
		case BULLDOZER_MOVE_MODE:
			sim.getBulldozer().setMode(BulldozerMode.MOVING);
			break;
		case BULLDOZER_ROTATE_RIGHT:
			sim.getBulldozer().rotateRight();
			break;
		case BULLDOZER_ROTATE_LEFT:
  			sim.bulldozer.rotateLeft();
			break;
		case BULLDOZER_MOVE:
			sim.bulldozer.move();
			break;
  
		default:
			break;
		}
	}
	private void doSetModeCommand(Command command) {
		switch(command){
		case SET_BULLDOZER_MODE:
			this.actualMode = Mode.BULLDOZER_MODE;
		case SET_FORKS_MODE:
			this.actualMode = Mode.FORKS_MODE;
		case SET_TRAINS_MODE:
			this.actualMode = Mode.TRAINS_MODE;
		}
	}
	 
 
 
	void paint(){
	}
	void erase(){
	}
	public enum Mode  {
		TRAINS_MODE,
		BULLDOZER_MODE,
		FORKS_MODE,	
	};
	
	public enum Command{
		SET_BULLDOZER_MODE,
		SET_TRAINS_MODE,
		SET_FORKS_MODE,
		TRAIN_SELECT_NEXT ,
		TRAIN_SELECT_PREV,
		TRAIN_INC_SPEED,
		TRAIN_DEC_SPEED,
		BULLDOZER_PUT_RAILS_MODE,
		BULLDOZER_REMOVE_RAILS_MODE,
		BULLDOZER_MOVE_MODE,
		BULLDOZER_ROTATE_RIGHT,
		BULLDOZER_ROTATE_LEFT,
		BULLDOZER_MOVE,
		FORK_SELECT_NEXT,
		FORK_SELECT_PREV ,
		FORK_INC,
		FORK_DEC, BULLDOZER_PUT_TRAIN_GARAGE;
	}
 
}
