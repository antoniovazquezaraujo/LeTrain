package com.letrain.garage;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.dir.DirEnv;
import com.letrain.garage.GarageRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;
import com.letrain.vehicle.RailVehicle;
import com.letrain.vehicle.Wagon;

public class GarageRailTest {
	Rail north;
	GarageRail rail;

	@Before
	public void setUp() throws Exception {
		north = new StraightRail();
		north.getEnv().addPath(Dir.N, Dir.S);
		rail = new GarageRail(new DirEnv());
		rail.linkRailAt(Dir.N, north);
		rail.setOuputRail(north);
	}

	@After
	public void tearDown() throws Exception {
		rail = null;
	}

	@Test
	public void testEnterVehicle() {
		RailVehicle v = new Wagon();
		rail.enterVehicle(v);
	}

	@Test
	public void testExitVehicle() {
		fail("Not yet implemented");
	}

	@Test
	public void testGarageRail() {
		fail("Not yet implemented");
	}

	@Test
	public void testSetInputDir() {
		fail("Not yet implemented");
	}

	@Test
	public void testSetOuputRail() {
		fail("Not yet implemented");
	}

}
