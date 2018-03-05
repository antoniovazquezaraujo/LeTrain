package com.letrain.view;

public class GateAspect extends Aspect {
	AspectChar aspectChar;
	public GateAspect(){
		super();
	}
 
	@Override
	public AspectChar getAspectChar() {
		return AspectChar.HASH;
	}
}
