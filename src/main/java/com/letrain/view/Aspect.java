package com.letrain.view;

import com.letrain.map.Point;
import com.letrain.view.Term.BackColor;
import com.letrain.view.Term.ForeColor;

public abstract class Aspect {

	public BackColor getBgColor() {
		return BackColor.BACKGROUND_BLACK;
	}

	public ForeColor getFgColor() {
		return ForeColor.WHITE;
	}

	abstract public AspectChar getAspectChar();

	public void paint(Window g, Point pos) {
		g.setBg(getBgColor());
		g.setFg(getFgColor());
		Point p = pos;
		g.putC(p.getRow(), p.getCol(), getAspectChar());
	}

	public void erase(Window g, Point pos) {
		g.setBg(getBgColor());
		g.setFg(getFgColor());
		Point p = pos;
		g.putC(p.getRow(), p.getCol(), ' ');
	}
}
