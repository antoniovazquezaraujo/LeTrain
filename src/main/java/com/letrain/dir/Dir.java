package com.letrain.dir;

import com.letrain.view.AspectChar;

public enum Dir {
	E ( 0,AspectChar.HORIZONTAL_LINE),
	NE ( 1,AspectChar.GO_UP_LINE),
	N ( 2,AspectChar.VERTICAL_LINE),
	NW ( 3,AspectChar.GO_DOWN_LINE),
	W ( 4,AspectChar.HORIZONTAL_LINE),
	SW ( 5,AspectChar.GO_UP_LINE),
	S ( 6,AspectChar.VERTICAL_LINE),
	SE ( 7,AspectChar.GO_DOWN_LINE);
	
	public static final int NUM_DIRS = 8;
	public static final int MIDDLE_ANGLE = NUM_DIRS  / 2;
	public static final int MIN_CURVE_ANGLE = MIDDLE_ANGLE - 1;
	public static final int MAX_CURVE_ANGLE = MIDDLE_ANGLE + 1;

	private int value;
	private AspectChar aspect;


	Dir(int value, AspectChar aspect) {
		this.value = value;
		this.aspect = aspect;
	}
	
	public int getValue(){
		return value;
	}
	public AspectChar getAspect(){
		return aspect ;
	}

	public static Dir random(){
		return fromInt((int)(Math.random()*NUM_DIRS));
	}
	public static Dir fromInt(int n){
		if (n < 0) {
			n = NUM_DIRS + n;
		} else if (n > SE.getValue()) {
			n = n - NUM_DIRS;
		}
		n %=NUM_DIRS;
		switch(n){
		case 0:
			return E;
		case 1:
			return NE;
		case 2:
			return N;
		case 3:
			return NW;
		case 4:
			return W;
		case 5:
			return SW;
		case 6:
			return S;
		case 7:
			return SE;
		}
		return null;
	}
 
 
	public static Dir add(Dir a, int n) {
		return fromInt(a.value+n);
	}


	public int angularDistance(Dir d) {
		return shortWay( d.value - this.value);
	}

	public Dir inverse(){
		return fromInt(invert(value));
	}

	static boolean  isValidValue(int value){
		return (value >= E.getValue() && value <= SE.getValue());
	}
	public static Dir invert(Dir dir) {
			return Dir.fromInt(dir.getValue()+ MIDDLE_ANGLE);
	}
	public static int invert(int value) {
		if (  isValidValue(value))  {
			return   value + MIDDLE_ANGLE;
		}else {
			throw new RuntimeException("Invalid int value for direction");
		}
		 
	}

	public static int shortWay(int angle) {
		int absValue = Math.abs(angle);
		if (absValue > MIDDLE_ANGLE) {
			return (NUM_DIRS - absValue) * -1;
		} else {
			return angle;
		}
	}

	public boolean isCurve(Dir to) {
		int distance = Math.abs(this.value - to.getValue());
		return ((distance == MIN_CURVE_ANGLE) || (distance == MAX_CURVE_ANGLE));
	}

	public boolean isStraight(Dir to) {
		return (Math.abs(this.value - to.getValue()) == MIDDLE_ANGLE);
	}

};
