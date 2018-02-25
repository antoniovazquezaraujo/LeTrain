package com.letrain.rail;

import com.letrain.dir.Dir;

public class RailEnv {

	Rail[] links;

	public RailEnv() {
		this(null);
	}

	RailEnv(RailEnv env) {
		this.links = new Rail[Dir.NUM_DIRS];
		for (Dir dir : Dir.values()) {
			links[dir.getValue()] = env!=null?env.links[dir.getValue()]:null;
		}
	}
	Rail getRail(Dir dir) {
		return links[dir.getValue()];
	}
	void addRail(Dir d, Rail r) {
		links[d.getValue()] = r;
	}
}
