package com.letrain.dir;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;

public class DirTest {

	@Test
	public void testDirFromInt() {
		assertEquals(Dir.fromInt(0), Dir.E);
		assertEquals(Dir.fromInt(1), Dir.NE);
		assertEquals(Dir.fromInt(2), Dir.N);
		assertEquals(Dir.fromInt(3), Dir.NW);
		assertEquals(Dir.fromInt(4), Dir.W);
		assertEquals(Dir.fromInt(5), Dir.SW);
		assertEquals(Dir.fromInt(6), Dir.S);
		assertEquals(Dir.fromInt(7), Dir.SE);
		assertEquals(Dir.fromInt(-7), Dir.NE);
		assertEquals(Dir.fromInt(-6), Dir.N);
		assertEquals(Dir.fromInt(-5), Dir.NW);
		assertEquals(Dir.fromInt(-4), Dir.W);
		assertEquals(Dir.fromInt(-3), Dir.SW);
		assertEquals(Dir.fromInt(-2), Dir.S);
		assertEquals(Dir.fromInt(-1), Dir.SE);
	}

	@Test
	public void testAdd() {
		for (Dir d : Dir.values()) {
			assert (Dir.add(d, 1) == Dir.fromInt(d.getValue() + 1));
			assert (Dir.add(d, -1) == Dir.fromInt(d.getValue() - 1));
		}
	}

	@Test
	public void testAngularDistance() {
		assertEquals(Dir.N.angularDistance(Dir.N), 0);
		assertEquals(Dir.S.angularDistance(Dir.S), 0);
		assertEquals(Dir.N.angularDistance(Dir.S), 4);
		assertEquals(Dir.NW.angularDistance(Dir.S), 3);
		assertEquals(Dir.NE.angularDistance(Dir.S), -3);
		assertEquals(Dir.NE.angularDistance(Dir.NW), 2);
		assertEquals(Dir.NW.angularDistance(Dir.NE), -2);
	}

	@Test
	public void testInverse() {
		assertEquals(Dir.N.inverse(), Dir.S);
		assertEquals(Dir.S.inverse(), Dir.N);
		assertEquals(Dir.E.inverse(), Dir.W);
		assertEquals(Dir.W.inverse(), Dir.E);
		assertEquals(Dir.NW.inverse(), Dir.SE);
		assertEquals(Dir.NE.inverse(), Dir.SW);
		assertEquals(Dir.SE.inverse(), Dir.NW);
		assertEquals(Dir.SW.inverse(), Dir.NE);
	}

  	@Test
	public void testShortWay() {
		assertEquals(Dir.shortWay(4), 4);
		assertEquals(Dir.shortWay(5), -3);
		assertEquals(Dir.shortWay(6), -2);
		assertEquals(Dir.shortWay(7), -1);
		assertEquals(Dir.shortWay(8), 0);

	}

	@Test
	public void testIsCurve() {
		for (int n = 0; n < Dir.NUM_DIRS; n++) {
			Dir d = Dir.fromInt(n);
			Dir inverseLeft = Dir.invert(Dir.add(d ,-1));
			Dir inverseRight = Dir.invert(Dir.add(d ,1));
			assertTrue(d.isCurve(inverseRight));
			assertTrue(d.isCurve( inverseRight ));
		}
	}

	@Test
	public void testIsStraight() {
		for (int n = 0; n < Dir.NUM_DIRS; n++) {
			Dir d = Dir.fromInt(n);
			int inverse = Dir.invert(d.getValue());
			assertTrue(d.isStraight(Dir.fromInt(inverse )));
		}
	}

}
