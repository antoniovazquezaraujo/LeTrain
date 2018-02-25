package com.letrain.rail;

import com.letrain.dir.DirEnv;
import com.letrain.view.Aspect;
import com.letrain.view.CurveAspect;

public class CurveRail extends Rail {
	public CurveRail(){
		super();
	}
	public CurveRail(DirEnv dirEnv) {
		super(dirEnv  );
	}

	@Override
	public Aspect getAspect() {
		return new CurveAspect();
	}

}
