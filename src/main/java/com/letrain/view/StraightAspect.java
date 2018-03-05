package com.letrain.view;

import com.letrain.dir.Dir;
 

public class StraightAspect extends Aspect {
	AspectChar aspectChar;
	public StraightAspect(){
		super();
	}

	public void selectAspectChar(Dir dir) {
		AspectChar ret = AspectChar.HORIZONTAL_LINE;
		switch (dir) {
		case E:
			ret = AspectChar.HORIZONTAL_LINE;
			break;
		case NE:
			ret = AspectChar.GO_UP_LINE;
			break;
		case N:
			ret = AspectChar.VERTICAL_LINE;
			break;
		case NW:
			ret = AspectChar.GO_DOWN_LINE;
			break;
		case W:
			ret = AspectChar.HORIZONTAL_LINE;
			break;
		case SW:
			ret = AspectChar.GO_UP_LINE;
			break;
		case S:
			ret = AspectChar.VERTICAL_LINE;
			break;
		case SE:
			ret = AspectChar.GO_DOWN_LINE;
			break;
		}
		this.aspectChar = ret;
	}

	@Override
	public AspectChar getAspectChar() {
		if(aspectChar == null){
			aspectChar = AspectChar.UNKNOWN;
		}
		return aspectChar;
	}
}
