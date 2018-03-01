package com.letrain.rail;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.map.RailMap;
import com.letrain.rail.BasicGateRail;
import com.letrain.rail.Rail;
import com.letrain.rail.StraightRail;
import com.letrain.vehicle.Train;
import com.letrain.vehicle.Train.TrainSide;
import com.letrain.vehicle.Wagon;

public class BasicGateRailTest {
	RailMap map;
	Rail r1;
	Rail r2;
	BasicGateRail gate;

	@Before
	public void setUp() throws Exception {
		map = new RailMap();
		r1 = new StraightRail();
		r2 = new StraightRail();
		gate = new BasicGateRail();
	}

	@After
	public void tearDown() throws Exception {
		map = null;
		r1 = null;
		r2 = null;
		gate = null;
	}

	@Test
	public void testConnectEnterAndExitRail() {
		r1.getEnv().addPath(Dir.W, Dir.E);
		r2.getEnv().addPath(Dir.W, Dir.E);
		map.addRail(0, -1, r1);
		map.addRail(0, 0, gate);
		map.addRail(0, 1, r2);
		gate.connectInputRail(r1);
		gate.connectOutputRail(r2);

		assertTrue(gate.numVehiclesInside == 0);
		assertTrue(gate.train == null);
		assertTrue(gate.getLinkedRailAt(Dir.E).equals(r1));
		assertTrue(gate.getLinkedRailAt(Dir.W).equals(r2));
		
		Train t1 = new Train();
		Wagon v1 = new Wagon();
		t1.addVehicle(TrainSide.BACK, v1);

		gate.enterVehicle(v1);
		assertTrue(gate.getTrain().equals(t1));
		assertTrue(gate.numVehiclesInside == 1);

		assertTrue(r2.getRailVehicle() == null);
		gate.exitVehicle();
		assertTrue(gate.getTrain() == null);
		assertTrue(gate.numVehiclesInside == 0);
		assertTrue(r2.getRailVehicle().equals(v1));

		
	}

	@Test
	public void testEnterAndExitTrain() {
		r1.getEnv().addPath(Dir.W, Dir.E);
		r2.getEnv().addPath(Dir.W, Dir.E);
		map.addRail(0, -1, r1);
		map.addRail(0, 0, r2);
		map.addRail(0, 1, gate);
		gate.connectInputRail(r2);
		gate.connectOutputRail(r1);

		Train t1 = new Train();
		t1.gotoRail(r1);
		Wagon v1 = new Wagon();
		v1.gotoRail(r2);
		Wagon v2 = new Wagon();
		v2.setRail(r2);
		t1.addVehicle(TrainSide.BACK, v1);
		t1.addVehicle(TrainSide.BACK, v2);
		t1.moveTrain();
	}
//	@Test
//	public void testOnFirstVehicleEnter() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testOnLastVehicleEnter() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testOnFirstVehicleExit() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testOnLastVehicleExit() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testGetTrain() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testEject() {
//		fail("Not yet implemented");
//	}
//
//	@Test
//	public void testTurn() {
//		fail("Not yet implemented");
//	}

}
