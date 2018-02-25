package com.letrain.rail;

import com.letrain.dir.DirEnv;
import com.letrain.view.Aspect;
import com.letrain.view.CrossAspect;

public class CrossRail extends Rail {
	public CrossRail(){
		super();
	}
	public CrossRail(DirEnv dirEnv) {
		super(dirEnv);
	}

	@Override
	public Aspect getAspect() {
		return  new CrossAspect();
	}

}
