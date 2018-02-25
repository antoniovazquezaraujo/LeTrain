package com.letrain.rail;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.view.Aspect;
import com.letrain.view.ForkAspect;

public class ForkRail extends Rail {
	final int NUM_OUTS= 3;
	Dir[] outs = new Dir[NUM_OUTS];
	Dir in;	
	int selectedOut;
	int numOuts =0;
	public ForkRail() {
		super();
	}

	public ForkRail(DirEnv dirEnv) {
		super(dirEnv);
		//innecesario!
		for(int n = 0; n< NUM_OUTS; n++){
			outs[n] = null;
		}
		selectedOut = 0;
		int numOuts = 0;
		for (Dir dir: Dir.values()){
			Dir found = dirEnv.getPath(dir);
			if(found  != null){
				if(dirEnv.canBeAForkInput(dir)){
					in = dir;
				}else{
					selectedOut=numOuts;
					outs[numOuts] = dir;
					numOuts++;
				}
			}
		}
	}
	void  selectDir(Dir dir){
		for(int n= 0; n< numOuts; n++){
			if(outs[n] == dir){
				selectedOut = n;
				break;
			}
		}	
	}
	public Dir  getSelectedDir(){
		return outs[selectedOut];
	}
	public void  setNextDir(){
		selectedOut++;
		selectedOut%=numOuts;
	}
	public void  setPrevDir(){
		selectedOut--;
		if(selectedOut < 0){
			selectedOut=numOuts-1;
		}
	}
	public Dir getPath(Dir dir){
		if(dir == in){
			return outs[selectedOut];
		}else{
			return in;
		}
	}
	@Override
	public Aspect getAspect() {
		if(aspect == null){
			aspect = new ForkAspect();
		}
		return super.getAspect();
	}
 

}
