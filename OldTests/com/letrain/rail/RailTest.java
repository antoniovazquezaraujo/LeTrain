package com.letrain.rail;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.rail.Rail;
import com.letrain.vehicle.RailVehicle;

public class RailTest {

	Rail rail;

	@Before
	public void setUp() throws Exception {
		rail = new Rail() {

		};
	}

	@After
	public void tearDown() throws Exception {
		rail = null;
	}

	@Test
	public void testEnterVehicle() {
		rail.getEnv().addPath(Dir.N, Dir.SW);
		RailVehicle v = new RailVehicle();
		v.setDir(Dir.S);
		rail.enterVehicle(v);
		assertEquals(v.getDir(), Dir.SW);
		rail.setVehicle(null);
		rail.getEnv().addPath(Dir.E, Dir.NW);
		v.setDir(Dir.W);
		rail.enterVehicle(v);
		assertEquals(v.getDir(), Dir.NW);

	}

//	@Test
//	public void testExitVehicle() {
//		Rail r2 = new Rail() {
//		};
//		r2.getEnv().addPath(Dir.S, Dir.N);
//		r2.linkRailAt(Dir.N, rail);
//		RailVehicle v = new RailVehicle();
//		v.setDir(Dir.N);
//		r2.enterVehicle(v);
//		r2.exitVehicle();
//		assertEquals(rail.getRailVehicle(), v);
//		assertEquals(r2.getRailVehicle(), null);
//	}

//	@Test
//	public void testLinkRailAt() {
//		Rail r2 = new Rail() {
//		};
//		r2.getEnv().addPath(Dir.S, Dir.N);
//		for (Dir d : Dir.values()) {
//			assertEquals(null, r2.getLinkedRailAt(d));
//			assertEquals(null, rail.getLinkedRailAt(d));
//		}
//		r2.linkRailAt(Dir.N, rail);
//		assertEquals(rail, r2.getLinkedRailAt(Dir.N));
//		assertEquals(r2, rail.getLinkedRailAt(Dir.S));
//	}

	@Test
	public void testGetPath() {
		for (Dir d : Dir.values()) {
			assertEquals(rail.getPath(d), null);
		}
		rail.getEnv().addPath(Dir.E, Dir.SW);
		assertEquals(rail.getPath(Dir.E), Dir.SW);
		assertEquals(rail.getPath(Dir.SW), Dir.E);
	}

	@Test
	public void testGetAnyPath() {
		assertEquals(rail.getAnyPath(), null);
		rail.getEnv().addPath(Dir.E, Dir.SW);
		Dir path = rail.getAnyPath();
		assertThat(path, anyOf(equalTo(Dir.E), equalTo(Dir.SW)));
	}

	@Test
	public void testSetRailVehicle() {
		RailVehicle v = new RailVehicle();
		assertNull(rail.getVehicle());
		rail.setVehicle(v);
		assertEquals(v, rail.getVehicle());
	}


	@Test
	public void testReverseVehicle() {
		rail.getEnv().addPath(Dir.NE, Dir.S);
		RailVehicle v = new RailVehicle();
		v.setDir(Dir.NE);
		rail.setVehicle(v);
		rail.reverseVehicle();
		assertEquals(v.getDir(), Dir.S);
	}
 
}
