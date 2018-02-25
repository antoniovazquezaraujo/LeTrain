package com.letrain.view;

import com.letrain.map.Point;
import com.letrain.view.Term.BackColor;
import com.letrain.view.Term.ForeColor;

public class FinderAspect extends Aspect {
	public FinderAspect() {
		super();
	}

	public AspectChar getAspectChar() {
		return AspectChar.FINDER;
	}

	public BackColor getBgColor() {
		return BackColor.BACKGROUND_BLACK;
	}

	public ForeColor getFgColor() {
		return ForeColor.WHITE;
	}

	public void paint(Window g, Point pos) {
		g.setBg(getBgColor());
		g.setFg(getFgColor());
		g.putC(pos.getRow(), pos.getCol() - 1, '[');
		g.putC(pos.getRow(), pos.getCol() + 1, ']');
	}

	public void erase(Window g, Point pos) {
		g.setBg(getBgColor());
		g.setFg(getFgColor());
		g.putC(pos.getRow(), pos.getCol() - 1, ' ');
		g.putC(pos.getRow(), pos.getCol() + 1, ' ');
	}
}
