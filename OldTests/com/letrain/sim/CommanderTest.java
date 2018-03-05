package com.letrain.sim;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.letrain.dir.Dir;
import com.letrain.map.Point;
import com.letrain.rail.Rail;
import com.letrain.sim.Commander.Command;
import com.letrain.vehicle.Train;

public class CommanderTest {
	Commander commander;

	@Before
	public void setUp() throws Exception {
		commander = new Commander(new Sim());
	}

	@After
	public void tearDown() throws Exception {
		commander = null;
	}

 
	@Test
	public void testBulldozerPaint() {
		commander.doCommand(Command.BULLDOZER_PUT_RAILS_MODE);
		assertTrue(commander.sim.getBulldozer().getDir() == Dir.N);
		assertTrue(commander.sim.getBulldozer().getPos().equals(new Point(0, 0)));
		commander.doCommand(Command.BULLDOZER_MOVE);
		assertTrue(commander.sim.getBulldozer().getPos().equals(new Point(-1, 0)));

	}

}
