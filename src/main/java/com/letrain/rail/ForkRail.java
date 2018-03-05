package com.letrain.rail;

import com.letrain.dir.Dir;
 
import com.letrain.view.Aspect;
import com.letrain.view.ForkAspect;

import static com.letrain.dir.Dir.*;
import static com.letrain.dir.Dir.SE;

public class ForkRail extends Rail {
	Dir in;
	Dir leftOut;
	Dir rightOut;
	Dir selectedOut;
	public ForkRail() {
		super(new ForkAspect());
	}


	@Override
	public void addPath(Dir from, Dir to) {
	    // DO NOTHING, USE THE NEXT
    }
    public void addPath(Dir from, Dir left, Dir right) {
        this.in = from;
        leftOut = left;
        rightOut = right;
        selectedOut = left; // by default
		dirs[left.getValue()] = from;
		dirs[right.getValue()] = from;
		dirs[from.getValue()] = left; //by default
	}
	public Dir getSelectedDir(){
		return selectedOut;
	}
	public void  selectLeftOut(){
        selectedOut = leftOut;
	}
	public void  selectRightOut(){
        selectedOut = rightOut;
	}
	public Dir getPath(Dir dir){
		if(dir == in){
			return selectedOut;
		}else{
			return in;
		}
	}
 }
