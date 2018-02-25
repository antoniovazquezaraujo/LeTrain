package com.letrain.rail;

import com.letrain.dir.DirEnv;
import com.letrain.view.Aspect;
import com.letrain.view.StraightAspect;

public class StraightRail extends Rail {
	public StraightRail() {
		super();
	}

	public StraightRail(DirEnv dirEnv) {
		super(dirEnv);
		
	}

	@Override
	public Aspect getAspect() {
		StraightAspect ret = new StraightAspect();
		ret.selectAspectChar(getEnv().getFirstOpenOut());
		return ret;
	}

}
