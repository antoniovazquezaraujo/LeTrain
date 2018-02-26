package com.letrain.view;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;

public class GateRailAspect extends Aspect {
	AspectChar aspectChar;
	public GateRailAspect(){
		super();
	}
 
	@Override
	public AspectChar getAspectChar() {
		return AspectChar.HASHTAG;
	}
}
