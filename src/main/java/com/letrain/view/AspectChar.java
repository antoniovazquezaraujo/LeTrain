package com.letrain.view;

public enum AspectChar {
	HORIZONTAL_LINE('-'), 
	VERTICAL_LINE('|'), 
	GO_UP_LINE('/'), 
	GO_DOWN_LINE('\\'), 
	CURVE('.'),
	CROSS('+'),
	FINDER('@'),
	UNKNOWN('?'), 
	HASHTAG('#');
	private char value;

	AspectChar(char value) {
		this.value = value;
	}

	public char getValue() {
		return this.value;
	}
}