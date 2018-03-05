package com.letrain.rail;
import com.letrain.dir.Dir;
import com.letrain.view.StraightAspect;

public class StraightRail extends Rail {
	public StraightRail() {
		super(new StraightAspect());
	}
	@Override
	public void addPath(Dir from, Dir to) {
		super.addPath(from, to);
		((StraightAspect)aspect).selectAspectChar(to);
	}
}
