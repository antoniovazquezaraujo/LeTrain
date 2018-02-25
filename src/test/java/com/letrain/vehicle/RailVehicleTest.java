package com.letrain.vehicle;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;
import com.letrain.vehicle.RailVehicle;

public class RailVehicleTest {
	Rail r1;
	Rail r2;
	Rail r3;
	RailVehicle v;
	@Before
	public void setUp() throws Exception {
		v = new RailVehicle();
		r1 = new StraightRail();
		r1.getEnv().addPath(Dir.W, Dir.E);
		r2 = new StraightRail();
		r2.getEnv().addPath(Dir.W, Dir.E);
		r3 = new StraightRail();
		r3.getEnv().addPath(Dir.W, Dir.E);
		r1.linkRailAt(Dir.E, r2);
		r2.linkRailAt(Dir.E, r3);
	}

	@After
	public void tearDown() throws Exception {
		r1 = null;
		r2 = null;
		r3 = null;
		v = null;
	}
 
	@Test
	public void testForward() {
		v.setDir(Dir.E);
		v.gotoRail(r2);
		v.forward();
		assertEquals(r2.getRailVehicle(), null);
		assertEquals(r3.getRailVehicle(), v);
		
	}

	@Test
	public void testBackward() {
		v.setDir(Dir.E);
		v.gotoRail(r2);
		v.backward();
		assertEquals(r2.getRailVehicle(), null);
		assertEquals(r1.getRailVehicle(), v);
	}

	@Test
	public void testGoBackToRail() {
		v.setDir(Dir.E);
		v.gotoRail(r2);
		v.forward();
		v.goBackToRail(r2);
		assertEquals(r2.getRailVehicle(), v);
		assertEquals(r3.getRailVehicle(), null);
	}

	@Test
	public void testGotoRail() {
		assertEquals(r2.getRailVehicle(), null);
		v.gotoRail(r2);
		assertEquals(r2.getRailVehicle(), v);
	}
//
//	@Test
//	public void testSetSpeed() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetSpeed() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testSetTrain() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetTrain() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetRail() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testSetRail() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testIsMoved() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testSetMoved() {
//		fail("Not yet implemented");
//	}

}
