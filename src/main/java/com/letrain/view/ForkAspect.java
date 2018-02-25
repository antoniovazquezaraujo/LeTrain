package com.letrain.view;

import com.letrain.dir.Dir;

public class ForkAspect extends StraightAspect {
	public ForkAspect(){
		this.aspectChar = AspectChar.CURVE;
	}
	public void  updateAspect(Dir dir){
		 selectAspectChar(dir);
	}
	public AspectChar getAspectChar(){
		return aspectChar;
	}
}
